/* (C)2023 */
package org.transitclock.core.avl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.transitclock.core.ServiceUtils;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.VehicleAtStopInfo;
import org.transitclock.core.VehicleStatus;
import org.transitclock.core.avl.assigner.AutoBlockAssignerFactory;
import org.transitclock.core.avl.assigner.BlockAssigner;
import org.transitclock.core.avl.assigner.BlockAssignmentMethod;
import org.transitclock.core.avl.space.SpatialMatch;
import org.transitclock.core.avl.space.SpatialMatcher;
import org.transitclock.core.avl.space.SpatialMatcher.MatchingType;
import org.transitclock.core.avl.time.TemporalMatch;
import org.transitclock.core.avl.time.TemporalMatcher;
import org.transitclock.core.dataCache.PredictionDataCache;
import org.transitclock.core.dataCache.VehicleDataCache;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.repository.VehicleToBlockConfigRepository;
import org.transitclock.domain.structs.AssignmentType;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.domain.structs.Block;
import org.transitclock.domain.structs.Location;
import org.transitclock.domain.structs.Route;
import org.transitclock.domain.structs.Stop;
import org.transitclock.domain.structs.Trip;
import org.transitclock.domain.structs.VectorWithHeading;
import org.transitclock.domain.structs.VehicleEvent;
import org.transitclock.domain.structs.VehicleEventType;
import org.transitclock.domain.structs.VehicleState;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.AutoBlockAssignerProperties;
import org.transitclock.properties.AvlProperties;
import org.transitclock.properties.CoreProperties;
import org.transitclock.utils.Geo;
import org.transitclock.utils.IntervalTimer;
import org.transitclock.utils.StringUtils;
import org.transitclock.utils.SystemTime;
import org.transitclock.utils.Time;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This is a very important high-level class. It takes the AVL data and processes it. Matches
 * vehicles to their assignments. Once a match is made then MatchProcessor class is used to generate
 * predictions, arrival/departure times, headway, etc.
 *
 * @author SkiBu Smith
 */
@Slf4j
@Component
public class AvlProcessor {
    @Autowired
    private TemporalMatcher temporalMatcher;

    @Autowired
    private MatchProcessor matchProcessor;

    @Autowired
    private RealTimeSchedAdhProcessor realTimeSchedAdhProcessor;

    @Autowired
    private BlockAssigner blockAssigner;

    @Autowired
    private AutoBlockAssignerFactory autoBlockAssignerFactory;

    @Autowired
    private VehicleDataCache vehicleDataCache;

    @Autowired
    private PredictionDataCache predictionDataCache;

    @Autowired
    private VehicleStatusManager vehicleStatusManager;

    @Autowired
    private AvlReportRegistry avlReportRegistry;

    @Autowired
    private DbConfig dbConfig;

    @Autowired
    private DataDbLogger dataDbLogger;

    @Autowired
    private CoreProperties coreProperties;

    @Autowired
    private AutoBlockAssignerProperties autoBlockAssignerProperties;

    @Autowired
    private AvlProperties avlProperties;

    // For keeping track of how long since received an AVL report so
    // can determine if AVL feed is up.
    private AvlReport lastRegularReportProcessed;

    /**
     * Removes predictions and the match for the vehicle and marks it as unpredictable. Updates
     * VehicleDataCache. Creates and logs a VehicleEvent explaining the situation.
     *
     * @param vehicleId The vehicle to be made unpredictable
     * @param eventDescription A longer description of why vehicle being made unpredictable
     * @param vehicleEvent A short description from VehicleEvent class for labeling the event.
     */
    public void makeVehicleUnpredictable(String vehicleId, String eventDescription, String vehicleEvent) {
        logger.info("Making vehicleId={} unpredictable. {}", vehicleId, eventDescription);

        VehicleStatus vehicleStatus = vehicleStatusManager.getStatus(vehicleId);

        AvlReport avlReport = vehicleStatus.getAvlReport();
        TemporalMatch lastMatch = vehicleStatus.getMatch();
        boolean wasPredictable = vehicleStatus.isPredictable();
        VehicleEvent event = new VehicleEvent(
                avlReport,
                lastMatch,
                vehicleEvent,
                eventDescription,
                false, // predictable
                wasPredictable, // becameUnpredictable
                null);// supervisor
        dataDbLogger.add(event);

        // Update the state of the vehicle
        vehicleStatus.setMatch(null);

        // Remove the predictions that were generated by the vehicle
        predictionDataCache.removePredictions(vehicleStatus);

        // Update VehicleDataCache with the new state for the vehicle
        vehicleDataCache.updateVehicle(vehicleStatus);
    }

    /**
     * Removes predictions and the match for the vehicle and marks is as unpredictable. Also removes
     * block assignment from the vehicleState. To be used for situations such as assignment ended or
     * vehicle was reassigned. Creates and logs a VehicleEvent explaining the situation.
     *
     * @param vehicleStatus The vehicle to be made unpredictable
     * @param eventDescription A longer description of why vehicle being made unpredictable
     * @param vehicleEvent A short description from VehicleEvent class for labeling the event.
     */
    public void makeVehicleUnpredictableAndTerminateAssignment(VehicleStatus vehicleStatus,
                                                               String eventDescription,
                                                               String vehicleEvent) {
        makeVehicleUnpredictable(vehicleStatus.getVehicleId(), eventDescription, vehicleEvent);

        vehicleStatus.unsetBlock(BlockAssignmentMethod.ASSIGNMENT_TERMINATED);
    }

    /**
     * Marks the vehicle as not being predictable and that the assignment has been grabbed. Updates
     * VehicleDataCache. Creates and logs a VehicleEvent explaining the situation.
     *
     * @param vehicleStatus The vehicle to be made unpredictable
     * @param eventDescription A longer description of why vehicle being made unpredictable
     * @param vehicleEvent A short description from VehicleEvent class for labeling the event.
     */
    public void makeVehicleUnpredictableAndGrabAssignment(
        VehicleStatus vehicleStatus, String eventDescription, String vehicleEvent) {
        makeVehicleUnpredictable(vehicleStatus.getVehicleId(), eventDescription, vehicleEvent);

        vehicleStatus.unsetBlock(BlockAssignmentMethod.ASSIGNMENT_GRABBED);
    }

    /**
     * Removes the vehicle from the VehicleDataCache.
     *
     * @param vehicleId The vehicle to remove
     */
    public void removeFromVehicleDataCache(String vehicleId) {
        vehicleDataCache.removeVehicle(vehicleId);
    }

    /**
     * Looks at the previous AVL reports to determine if vehicle is actually moving. If it is not
     * moving then the vehicle is made unpredictable. Uses the system properties
     * transitclock.core.timeForDeterminingNoProgress and transitclock.core.minDistanceForNoProgress
     *
     * @param bestTemporalMatch
     * @param vehicleStatus
     * @return True if vehicle not making progress, otherwise false. If vehicle doesn't currently
     *     match or if there is not enough history for the vehicle then false is returned.
     */
    private boolean handleIfVehicleNotMakingProgress(TemporalMatch bestTemporalMatch, VehicleStatus vehicleStatus) {
        // If there is no current match anyways then don't need to do anything
        // here.
        if (bestTemporalMatch == null)
            return false;

        // If this feature disabled then return false
        int noProgressMsec = coreProperties.getTimeForDeterminingNoProgress();
        if (noProgressMsec <= 0)
            return false;

        // If no previous match then cannot determine if not making progress
        TemporalMatch previousMatch = vehicleStatus.getPreviousMatch(noProgressMsec);
        if (previousMatch == null)
            return false;

        // Determine distance traveled between the matches
        double distanceTraveled = previousMatch.distanceBetweenMatches(bestTemporalMatch, dbConfig);
        double minDistance = coreProperties.getMinDistanceForNoProgress();

        if (distanceTraveled < minDistance) {
            // Determine if went through any wait stops since if did then
            // vehicle wasn't stuck in traffic. It was simply stopped at
            // layover.
            boolean traversedWaitStop = previousMatch.traversedWaitStop(bestTemporalMatch, dbConfig);
            if (!traversedWaitStop) {
                // Determine how much time elapsed between AVL reports
                long timeBetweenAvlReports = vehicleStatus.getAvlReport().getTime() - previousMatch.getAvlTime();

                // Create message indicating why vehicle being made
                // unpredictable because vehicle not making forward progress.
                String eventDescription = "Vehicle only traveled "
                        + StringUtils.distanceFormat(distanceTraveled)
                        + " over the last "
                        + Time.elapsedTimeStr(timeBetweenAvlReports)
                        + " which is below minDistanceForNoProgress of "
                        + StringUtils.distanceFormat(minDistance)
                        + " so was made unpredictable.";

                // Make vehicle unpredictable and do associated logging
                makeVehicleUnpredictable(
                        vehicleStatus.getVehicleId(), eventDescription, VehicleEventType.NO_PROGRESS);

                // Return that vehicle indeed not making progress
                return true;
            }
        }

        // Vehicle was making progress so return such
        return false;
    }

    /**
     * Looks at the previous AVL reports to determine if vehicle is actually moving. If it is not
     * moving then the vehicle should be marked as being delayed. Uses the system properties
     * transitclock.core.timeForDeterminingDelayed and transitclock.core.minDistanceForDelayed
     *
     * @param vehicleStatus For providing the temporal match and the AVL history. It is expected that
     *     the new match has already been set.
     * @return True if vehicle not making progress, otherwise false. If vehicle doesn't currently
     *     match or if there is not enough history for the vehicle then false is returned.
     */
    private boolean handlePossibleVehicleDelay(VehicleStatus vehicleStatus) {
        // Assume vehicle is not delayed
        boolean wasDelayed = vehicleStatus.isDelayed();
        vehicleStatus.setIsDelayed(false);

        // Determine the new match
        TemporalMatch currentMatch = vehicleStatus.getMatch();

        // If there is no current match anyways then don't need to do anything
        // here.
        if (currentMatch == null) return false;

        // If this feature disabled then return false
        int maxDelayedSecs = coreProperties.getTimeForDeterminingDelayedSecs();
        if (maxDelayedSecs <= 0) return false;

        // If no previous match then cannot determine if not making progress
        TemporalMatch previousMatch = vehicleStatus.getPreviousMatch(maxDelayedSecs * Time.MS_PER_SEC);
        if (previousMatch == null) return false;

        // Determine distance traveled between the matches
        double distanceTraveled = previousMatch.distanceBetweenMatches(currentMatch, dbConfig);

        double minDistance = coreProperties.getMinDistanceForDelayed();
        if (distanceTraveled < minDistance) {
            // Determine if went through any wait stops since if did then
            // vehicle wasn't stuck in traffic. It was simply stopped at
            // layover.
            boolean traversedWaitStop = previousMatch.traversedWaitStop(currentMatch, dbConfig);
            if (!traversedWaitStop) {
                // Mark vehicle as being delayed
                vehicleStatus.setIsDelayed(true);

                // Create description of event
                long timeBetweenAvlReports = vehicleStatus.getAvlReport().getTime() - previousMatch.getAvlTime();
                String description = "Vehicle vehicleId="
                        + vehicleStatus.getVehicleId()
                        + " is delayed. Over "
                        + timeBetweenAvlReports
                        + " msec it "
                        + "traveled only "
                        + Geo.distanceFormat(distanceTraveled)
                        + " while "
                        + "transitclock.core.timeForDeterminingDelayedSecs="
                        + maxDelayedSecs
                        + " and "
                        + "transitclock.core.minDistanceForDelayed="
                        + Geo.distanceFormat(minDistance);

                // Log the event
                logger.info(description);

                // If vehicle newly delayed then also create a VehicleEvent
                // indicating such
                if (!wasDelayed) {
                    VehicleEvent vehicleEvent = new VehicleEvent(
                            vehicleStatus.getAvlReport(),
                            vehicleStatus.getMatch(),
                            VehicleEventType.DELAYED,
                            description,
                            true, // predictable
                            false, // becameUnpredictable
                            null);// supervisor
                    dataDbLogger.add(vehicleEvent);
                }

                // Return that vehicle indeed delayed
                return true;
            }
        }

        // Vehicle was making progress so return such
        return false;
    }

    /**
     * For vehicles that were already predictable but then got a new AvlReport. Determines where in
     * the block assignment the vehicle now matches to. Starts at the previous match and then looks
     * ahead from there to find good spatial matches. Then determines which spatial match is best by
     * looking at temporal match. Updates the vehicleState with the resulting best temporal match.
     *
     * @param vehicleStatus the previous vehicle state
     */
    public void matchNewFixForPredictableVehicle(VehicleStatus vehicleStatus) {
        // Make sure state is coherent
        if (!vehicleStatus.isPredictable() || vehicleStatus.getMatch() == null) {
            throw new RuntimeException("Called AvlProcessor.matchNewFix() "
                    + "for a vehicle that was not already predictable. "
                    + vehicleStatus);
        }

        logger.debug("STARTOFMATCHING");
        logger.debug(
                "Matching already predictable vehicle using new AVL " + "report. The old spatial match is {}",
            vehicleStatus);

        // Find possible spatial matches
        SpatialMatcher spatialMatcher = new SpatialMatcher(dbConfig, coreProperties, avlProperties);
        List<SpatialMatch> spatialMatches = spatialMatcher.getSpatialMatches(vehicleStatus);
        logger.debug(
                "For vehicleId={} found the following {} spatial " + "matches: {}",
                vehicleStatus.getVehicleId(),
                spatialMatches.size(),
                spatialMatches);

        // Find the best temporal match of the spatial matches
        TemporalMatch bestTemporalMatch =
                temporalMatcher.getBestTemporalMatch(vehicleStatus, spatialMatches);

        // Log this as info since matching is a significant milestone
        logger.info("For vehicleId={} the best match is {}", vehicleStatus.getVehicleId(), bestTemporalMatch);

        // If didn't get a match then remember such in VehicleState
        if (bestTemporalMatch == null) vehicleStatus.incrementNumberOfBadMatches();

        // If vehicle not making progress then return
        boolean notMakingProgress = handleIfVehicleNotMakingProgress(bestTemporalMatch, vehicleStatus);
        if (notMakingProgress) return;

        // Record this match unless the match was null and haven't
        // reached number of bad matches.
        if (bestTemporalMatch != null || vehicleStatus.overLimitOfBadMatches()) {
            // If not over the limit of bad matches then handle normally
            if (bestTemporalMatch != null || !vehicleStatus.overLimitOfBadMatches()) {
                // Set the match of the vehicle.
                vehicleStatus.setMatch(bestTemporalMatch);
            } else {
                // Exceeded allowable number of bad matches so make vehicle
                // unpredictable due to bad matches log that info.
                // Log that vehicle is being made unpredictable as a
                // VehicleEvent
                String eventDescription = "Vehicle had "
                        + vehicleStatus.numberOfBadMatches()
                        + " bad spatial matches in a row"
                        + " and so was made unpredictable.";

                logger.warn("For vehicleId={} {}", vehicleStatus.getVehicleId(), eventDescription);

                // Remove the predictions for the vehicle
                makeVehicleUnpredictable(vehicleStatus.getVehicleId(), eventDescription, VehicleEventType.NO_MATCH);

                // Remove block assignment from vehicle
                vehicleStatus.unsetBlock(BlockAssignmentMethod.COULD_NOT_MATCH);
            }
        } else {
            logger.info(
                    "For vehicleId={} got a bad match, {} in a row, so " + "not updating match for vehicle",
                    vehicleStatus.getVehicleId(),
                    vehicleStatus.numberOfBadMatches());
        }

        // If schedule adherence is bad then try matching vehicle to assignment
        // again. This can make vehicle unpredictable if can't match vehicle to
        // assignment.
        if (vehicleStatus.isPredictable() && vehicleStatus.lastMatchIsValid()) verifyRealTimeSchAdh(vehicleStatus);

        logger.debug("ENDOFMATCHING");
    }

    /**
     * When matching a vehicle to a route we are currently assuming that we cannot make predictions
     * or match a vehicle to a specific trip until after vehicle has started on its trip. This is
     * because there will be multiple trips per route and we cannot tell which one the vehicle is on
     * time wise until the vehicle has started the trip.
     *
     * @param match
     * @return True if the match can be used when matching vehicle to a route
     */
    private boolean matchOkForRouteMatching(SpatialMatch match) {
        return match.awayFromTerminals(coreProperties.getTerminalDistanceForRouteMatching());
    }

    /**
     * When assigning a vehicle to a block then this method should be called to update the
     * VehicleState and log a corresponding VehicleEvent. If block assignments are to be exclusive
     * then any old vehicle on that assignment will be have its assignment removed.
     *
     * @param bestMatch The TemporalMatch that the vehicle was matched to. If set then vehicle will
     *     be made predictable and if block assignments are to be exclusive then any old vehicle on
     *     that assignment will be have its assignment removed. If null then vehicle will be
     *     configured to be not predictable.
     * @param vehicleStatus The VehicleState for the vehicle to be updated
     * @param possibleBlockAssignmentMethod The type of assignment, such as
     *     BlockAssignmentMethod.AVL_FEED_ROUTE_ASSIGNMENT or
     *     BlockAssignmentMethod.AVL_FEED_BLOCK_ASSIGNMENT
     * @param assignmentId The ID of the route or block that getting assigned to
     * @param assignmentType A string for logging and such indicating whether assignment made to a
     *     route or to a block. Should therefore be "block" or "route".
     */
    private void updateVehicleStateFromAssignment(
            TemporalMatch bestMatch,
            VehicleStatus vehicleStatus,
            BlockAssignmentMethod possibleBlockAssignmentMethod,
            String assignmentId,
            String assignmentType) {
        // Convenience variables
        AvlReport avlReport = vehicleStatus.getAvlReport();
        String vehicleId = avlReport.getVehicleId();

        // Make sure no other vehicle is using that assignment
        // if it is supposed to be exclusive. This needs to be done before
        // the VehicleDataCache is updated with info from the current
        // vehicle since this will affect all vehicles assigned to the
        // block.
        if (bestMatch != null) {
            unassignOtherVehiclesFromBlock(bestMatch.getBlock(), vehicleId);
        }

        // If got a valid match then keep track of state
        BlockAssignmentMethod blockAssignmentMethod = null;
        boolean predictable = false;
        Block block = null;
        if (bestMatch != null) {
            blockAssignmentMethod = possibleBlockAssignmentMethod;
            predictable = true;
            block = bestMatch.getBlock();
            logger.info(
                    "vehicleId={} matched to {}Id={}. Vehicle is now predictable.",
                    vehicleId,
                    assignmentType,
                    assignmentId);

            // Record a corresponding VehicleEvent
            String eventDescription =
                    "Vehicle successfully matched to " + assignmentType + " assignment and is now predictable.";
            VehicleEvent vehicleEvent = new VehicleEvent(
                    avlReport,
                    bestMatch,
                    VehicleEventType.PREDICTABLE,
                    eventDescription,
                    true, // predictable
                    false, // becameUnpredictable
                    null);// supervisor
            dataDbLogger.add(vehicleEvent);
        } else {
            logger.debug(
                    "For vehicleId={} could not assign to {}Id={}. "
                            + "Therefore vehicle is not being made predictable.",
                    vehicleId,
                    assignmentType,
                    assignmentId);
        }

        // Update the vehicle state with the determined block assignment
        // and match. Of course might not have been successful in
        // matching vehicle, but still should update VehicleState.
        vehicleStatus.setMatch(bestMatch);
        vehicleStatus.setBlock(block, blockAssignmentMethod, assignmentId, predictable);

        if (bestMatch != null) {
            logConflictingSpatialAssigment(bestMatch, vehicleStatus);
        }
    }

    /**
     * compare the match to the avl location and log if they differ greatly. Note we just log this,
     * we do not make the vehicle unpredictable.
     *
     * @param bestMatch
     * @param vehicleStatus
     */
    private void logConflictingSpatialAssigment(TemporalMatch bestMatch, VehicleStatus vehicleStatus) {
        if (vehicleStatus == null || vehicleStatus.getAvlReport() == null) return;

        // avl location
        double avlLat = vehicleStatus.getAvlReport().getLat();
        double avlLon = vehicleStatus.getAvlReport().getLon();
        Location avlLocation = new Location(avlLat, avlLon);

        // match location
        VectorWithHeading segment = bestMatch.getIndices().getSegment();
        double distanceAlongSegment = bestMatch.getDistanceAlongSegment();
        Location matchLocation = segment.locAlongVector(distanceAlongSegment);

        long tripStartTime = bestMatch.getTrip().getStartTime() * 1000 + Time.getStartOfDay(new Date());
        // ignore future trips as we are deadheading
        if (tripStartTime > SystemTime.getMillis()) return;

        // difference
        double deltaDistance = Math.abs(Geo.distance(avlLocation, matchLocation));

        if (vehicleStatus.isPredictable() && deltaDistance > coreProperties.getMaxMatchDistanceFromAVLRecord()) {
            String eventDescription = "Vehicle match conflict from AVL report of "
                    + Geo.distanceFormat(deltaDistance)
                    + " from match "
                    + matchLocation;

            VehicleEvent vehicleEvent = new VehicleEvent(
                    vehicleStatus.getAvlReport(),
                    bestMatch,
                    VehicleEventType.AVL_CONFLICT,
                    eventDescription,
                    true, // predictable
                    false, // becameUnpredictable
                    null);// supervisor
            dataDbLogger.add(vehicleEvent);
        }
    }

    /**
     * Attempts to match vehicle to the specified route by finding appropriate block assignment.
     * Updates the VehicleState with the new block assignment and match. These will be null if
     * vehicle could not successfully be matched to route.
     *
     * @param routeId
     * @param vehicleStatus
     * @return True if successfully matched vehicle to block assignment for specified route
     */
    private boolean matchVehicleToRouteAssignment(String routeId, VehicleStatus vehicleStatus) {
        // Make sure params are good
        if (routeId == null) {
            logger.error("matchVehicleToRouteAssignment() called with null " + "routeId. {}", vehicleStatus);
        }

        logger.debug("Matching unassigned vehicle to routeId={}. {}", routeId, vehicleStatus);

        // Convenience variables
        AvlReport avlReport = vehicleStatus.getAvlReport();

        // Determine which blocks are currently active for the route.
        // Multiple services can be active on a given day. Therefore need
        // to look at all the active ones to find out what blocks are active...
        List<Block> allBlocksForRoute = new ArrayList<>();
        ServiceUtils serviceUtils = dbConfig.getServiceUtils();
        Collection<String> serviceIds = serviceUtils.getServiceIds(avlReport.getDate());
        for (String serviceId : serviceIds) {
            List<Block> blocksForService = dbConfig.getBlocksForRoute(serviceId, routeId);
            if (blocksForService != null) {
                allBlocksForRoute.addAll(blocksForService);
            }
        }

        List<SpatialMatch> allPotentialSpatialMatchesForRoute = new ArrayList<>();

        // Go through each block and determine best spatial matches
        for (Block block : allBlocksForRoute) {
            // If the block isn't active at this time then ignore it. This way
            // don't look at each trip to see if it is active which is important
            // because looking at each trip means all the trip data including
            // travel times needs to be lazy loaded, which can be slow.
            // Override by setting transitclock.core.ignoreInactiveBlocks to false
            if (!block.isActive(dbConfig, avlReport.getDate()) && coreProperties.getIgnoreInactiveBlocks()) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "For vehicleId={} ignoring block ID {} with "
                                    + "start_time={} and end_time={} because not "
                                    + "active for time {}",
                            avlReport.getVehicleId(),
                            block.getId(),
                            Time.timeOfDayStr(block.getStartTime()),
                            Time.timeOfDayStr(block.getEndTime()),
                            Time.timeStr(avlReport.getDate()));
                }
                continue;
            }

            // Determine which trips for the block are active. If none then
            // continue to the next block
            List<Trip> potentialTrips = block.getTripsCurrentlyActive(dbConfig, avlReport);
            if (potentialTrips.isEmpty()) continue;

            logger.debug(
                    "For vehicleId={} examining potential trips for " + "match to block ID {}. {}",
                    avlReport.getVehicleId(),
                    block.getId(),
                    potentialTrips);

            // Get the potential spatial matches
            List<SpatialMatch> spatialMatchesForBlock = new SpatialMatcher(dbConfig, coreProperties, avlProperties).getSpatialMatches(
                    vehicleStatus.getAvlReport(), block, potentialTrips, MatchingType.AUTO_ASSIGNING_MATCHING);

            // Add appropriate spatial matches to list
            for (SpatialMatch spatialMatch : spatialMatchesForBlock) {
                SpatialMatcher spatialMatcher = new SpatialMatcher(dbConfig, coreProperties, avlProperties);
                if (!spatialMatcher.problemMatchDueToLackOfHeadingInfo(
                                spatialMatch, vehicleStatus, MatchingType.AUTO_ASSIGNING_MATCHING)
                        && matchOkForRouteMatching(spatialMatch)) allPotentialSpatialMatchesForRoute.add(spatialMatch);
            }
        } // End of going through each block to determine spatial matches

        // For the spatial matches get the best temporal match
        TemporalMatch bestMatch = temporalMatcher
                .getBestTemporalMatchComparedToSchedule(avlReport, allPotentialSpatialMatchesForRoute);
        logger.debug("For vehicleId={} best temporal match is {}", avlReport.getVehicleId(), bestMatch);

        // Update the state of the vehicle
        updateVehicleStateFromAssignment(
                bestMatch, vehicleStatus, BlockAssignmentMethod.AVL_FEED_ROUTE_ASSIGNMENT, routeId, "route");

        // Return true if predictable
        return bestMatch != null;
    }

    /**
     * Attempts to match the vehicle to the new block assignment. Updates the VehicleState with the
     * new block assignment and match. These will be null if vehicle could not successfully be
     * matched to block.
     *
     * @param block
     * @param vehicleStatus
     * @return True if successfully matched vehicle to block assignment
     */
    private boolean matchVehicleToBlockAssignment(Block block, VehicleStatus vehicleStatus) {
        // Make sure params are good
        if (block == null) {
            logger.error("matchVehicleToBlockAssignment() called with null " + "block. {}", vehicleStatus);
        }

        logger.debug("Matching unassigned vehicle to block assignment {}. {}", block.getId(), vehicleStatus);

        // Convenience variables
        AvlReport avlReport = vehicleStatus.getAvlReport();

        // Determine best spatial matches for trips that are currently
        // active. Currently active means that the AVL time is within
        // reasonable range of the start time and within the end time of
        // the trip. Matching type is set to MatchingType.STANDARD_MATCHING,
        // which means the matching can be more lenient than with
        // MatchingType.AUTO_ASSIGNING_MATCHING, because the AVL feed is
        // specifying the block assignment so it should find a match even
        // if it pretty far off.
        List<Trip> potentialTrips = block.getTripsCurrentlyActive(dbConfig, avlReport);
        List<SpatialMatch> spatialMatches = new SpatialMatcher(dbConfig, coreProperties, avlProperties).getSpatialMatches(
                vehicleStatus.getAvlReport(), block, potentialTrips, MatchingType.STANDARD_MATCHING);
        logger.debug(
                "For vehicleId={} and blockId={} spatial matches={}",
                avlReport.getVehicleId(),
                block.getId(),
                spatialMatches);

        // Determine the best temporal match
        TemporalMatch bestMatch =
                temporalMatcher.getBestTemporalMatchComparedToSchedule(avlReport, spatialMatches);
        logger.debug("Best temporal match for vehicleId={} is {}", avlReport.getVehicleId(), bestMatch);

        // If best match is a non-layover but cannot confirm that the heading
        // is acceptable then don't consider this a match. Instead, wait till
        // get another AVL report at a different location so can see if making
        // progress along route in proper direction.
        SpatialMatcher spatialMatcher = new SpatialMatcher(dbConfig, coreProperties, avlProperties);
        if (spatialMatcher.problemMatchDueToLackOfHeadingInfo(
                bestMatch, vehicleStatus, MatchingType.STANDARD_MATCHING)) {
            logger.debug(
                    "Found match but could not confirm that heading is "
                            + "proper. Therefore not matching vehicle to block. {}",
                    bestMatch);
            return false;
        }

        // If couldn't find an adequate spatial/temporal match then resort
        // to matching to a layover stop at a terminal.
        if (bestMatch == null) {
            logger.debug(
                    "For vehicleId={} could not find reasonable " + "match so will try to match to layover stop.",
                    avlReport.getVehicleId());

            Trip trip = temporalMatcher.matchToLayoverStopEvenIfOffRoute(avlReport, potentialTrips);
            if (trip != null) {
                // Determine distance to first stop of trip
                Location firstStopInTripLoc = trip.getStopPath(0).getStopLocation();
                double distanceToSegment = firstStopInTripLoc.distance(avlReport.getLocation());

                SpatialMatch beginningOfTrip = new SpatialMatch(
                        avlReport.getTime(),
                        block,
                        block.getTripIndex(trip),
                        0, // stopPathIndex
                        0, // segmentIndex
                        distanceToSegment,
                        0.0,
                        coreProperties); // distanceAlongSegment

                bestMatch = new TemporalMatch(beginningOfTrip, new TemporalDifference(0, coreProperties));
                logger.debug(
                        "For vehicleId={} could not find reasonable "
                                + "match for blockId={} so had to match to layover. "
                                + "The match is {}",
                        avlReport.getVehicleId(),
                        block.getId(),
                        bestMatch);
            } else {
                logger.debug(
                        "For vehicleId={} couldn't find match for " + "blockId={}",
                        avlReport.getVehicleId(),
                        block.getId());
            }
        }

        // Sometimes get a bad assignment where there is already a valid vehicle
        // with the assignment and the new match is actually far away from the
        // route, indicating driver might have entered wrong ID or never
        // logged out. For this situation ignore the match.
        if (matchProblematicDueOtherVehicleHavingAssignment(bestMatch, vehicleStatus)) {
            logger.error(
                    "Got a match for vehicleId={} but that assignment is "
                            + "already taken by another vehicle and the new match "
                            + "doesn't appear to be valid because it is far away from "
                            + "the route. {} {}",
                    avlReport.getVehicleId(),
                    bestMatch,
                    avlReport);
            return false;
        }

        // Update the state of the vehicle
        updateVehicleStateFromAssignment(
                bestMatch, vehicleStatus, BlockAssignmentMethod.AVL_FEED_BLOCK_ASSIGNMENT, block.getId(), "block");

        // Return true if predictable
        return bestMatch != null;
    }

    /**
     * Determines if match is problematic since other vehicle already has assignment and the other
     * vehicle seems to be more appropriate. Match is problematic if 1) exclusive matching is
     * enabled, 2) other non-schedule based vehicle already has the assignment, 3) the other vehicle
     * that already has the assignment isn't having any problems such as vehicle being delayed, and
     * 4) the new match is far away from the route which implies that it might be a mistaken login.
     *
     * <p>Should be noted that this issue was encountered with sfmta on 1/1/2016 around 15:00 for
     * vehicle 8660 when avl feed showed it getting block assignment 573 even though it was far from
     * the route and vehicle 8151 already had that assignment and actually was on the route. This
     * can happen if driver enters wrong assignment, or perhaps if they never log out.
     *
     * @param match
     * @param vehicleStatus
     * @return true if the match is problematic and should not be used
     */
    private boolean matchProblematicDueOtherVehicleHavingAssignment(TemporalMatch match, VehicleStatus vehicleStatus) {
        // If no match in first place then not a problem
        if (match == null) return false;

        // If matches don't need to be exclusive then don't have a problem
        Block block = match.getBlock();
        if (!coreProperties.isExclusiveBlockAssignments()) return false;

        // If no other non-schedule based vehicle assigned to the block then
        // not a problem
        Collection<String> vehiclesAssignedToBlock =
                vehicleDataCache.getVehiclesByBlockId(block.getId());
        if (vehiclesAssignedToBlock.isEmpty())
            // No other vehicle has assignment so not a problem
            return false;
        String otherVehicleId = null;
        for (String vehicleId : vehiclesAssignedToBlock) {
            otherVehicleId = vehicleId;
            VehicleStatus otherVehicleStatus = vehicleStatusManager.getStatus(otherVehicleId);

            // If other vehicle that has assignment is schedule based then not
            // a problem to take its assignment away
            if (otherVehicleStatus.isForSchedBasedPreds()) return false;

            // If that other vehicle actually having any problem then not a
            // problem to take assignment away
            if (!otherVehicleStatus.isPredictable() || vehicleStatus.isDelayed()) return false;
        }

        // So far we know that another vehicle has exclusive assignment and
        // there are no problems with that vehicle. This means the new match
        // could be a mistake. Shouldn't use it if the new vehicle is far
        // away from route (it matches to a layover where matches are lenient),
        // indicating that the vehicle might have gotten wrong assignment while
        // doing something else.
        if (match.getDistanceToSegment() > coreProperties.getMaxDistanceForAssignmentGrab()) {
            // Match is far away from route so consider it to be invalid.
            // Log an error
            logger.error(
                    "For agencyId={} got a match for vehicleId={} but that "
                            + "assignment is already taken by vehicleId={} and the new "
                            + "match doesn't appear to be valid because it is more "
                            + "than {}m from the route. {} {}",
                    coreProperties.getAgencyId(),
                    vehicleStatus.getVehicleId(),
                    otherVehicleId,
                    coreProperties.getMaxDistanceForAssignmentGrab(),
                    match,
                    vehicleStatus.getAvlReport());

            // Only send e-mail error rarely
            if (shouldSendMessage(vehicleStatus.getVehicleId(), vehicleStatus.getAvlReport())) {
                logger.error(
                        "For agencyId={} got a match for vehicleId={} but that "
                                + "assignment is already taken by vehicleId={} and the new "
                                + "match doesn't appear to be valid because it is more "
                                + "than {}m from the route. {} {}",
                        coreProperties.getAgencyId(),
                        vehicleStatus.getVehicleId(),
                        otherVehicleId,
                        coreProperties.getMaxDistanceForAssignmentGrab(),
                        match,
                        vehicleStatus.getAvlReport());
            }

            return true;
        } else {
            // The new match is reasonably close to the route so should consider
            // it valid
            return false;
        }
    }

    // Keyed on vehicleId. Contains last time problem grabbing assignment
    // message sent for the vehicle. For reducing number of emails sent
    // when there is a problem.
    private final Map<String, Long> problemGrabbingAssignmentMap = new HashMap<>();

    /**
     * For reducing e-mail logging messages when problem grabbing assignment. Java property
     * transitclock.avl.emailMessagesWhenAssignmentGrabImproper must be true for e-mail to be sent
     * when there is an error.
     *
     * @param vehicleId
     * @param avlReport
     * @return true if should send message
     */
    private boolean shouldSendMessage(String vehicleId, AvlReport avlReport) {
        Long lastTimeSentForVehicle = problemGrabbingAssignmentMap.get(vehicleId);
        // If message not yet sent for vehicle or it has been more than 10 minutes...
        if (coreProperties.isEmailMessagesWhenAssignmentGrabImproper()
                && (lastTimeSentForVehicle == null || avlReport.getTime() > lastTimeSentForVehicle + 30 * Time.MS_PER_MIN)) {
            problemGrabbingAssignmentMap.put(vehicleId, avlReport.getTime());
            return true;
        } else return false;
    }

    /**
     * If the block assignment is supposed to be exclusive then looks for any vehicles assigned to
     * the specified block and removes the assignment from them. This of course needs to be called
     * before a vehicle is assigned to a block since *ALL* vehicles assigned to the block will have
     * their assignment removed.
     *
     * @param block
     * @param newVehicleId for logging message
     */
    private void unassignOtherVehiclesFromBlock(Block block, String newVehicleId) {
        // Determine vehicles assigned to block
        Collection<String> vehiclesAssignedToBlock =
                vehicleDataCache.getVehiclesByBlockId(block.getId());

        // For each vehicle assigned to the block unassign it
        VehicleStatusManager stateManager = vehicleStatusManager;
        for (String vehicleId : vehiclesAssignedToBlock) {
            VehicleStatus vehicleStatus = stateManager.getStatus(vehicleId);
            if (coreProperties.isExclusiveBlockAssignments() || vehicleStatus.isForSchedBasedPreds()) {
                String description = "Assigning vehicleId="
                        + newVehicleId
                        + " to blockId="
                        + block.getId()
                        + " but "
                        + "vehicleId="
                        + vehicleId
                        + " already assigned to that block so "
                        + "removing assignment from vehicleId="
                        + vehicleId
                        + ".";
                logger.info(description);
                makeVehicleUnpredictableAndGrabAssignment(vehicleStatus, description, VehicleEventType.ASSIGNMENT_GRABBED);
            }
        }
    }

    /**
     * To be called when vehicle doesn't already have a block assignment or the vehicle is being
     * reassigned. Uses block assignment from the AvlReport to try to match the vehicle to the
     * assignment. If successful then the vehicle can be made predictable. The AvlReport is obtained
     * from the vehicleState parameter.
     *
     * @param vehicleStatus provides current AvlReport plus is updated by this method with the new
     *     state.
     * @return true if successfully assigned vehicle
     */
    public boolean matchVehicleToAssignment(VehicleStatus vehicleStatus) {
        logger.debug("Matching unassigned vehicle to assignment. {}", vehicleStatus);

        // Initialize some variables
        AvlReport avlReport = vehicleStatus.getAvlReport();

        // Remove old block assignment if there was one
        if (vehicleStatus.isPredictable() && vehicleStatus.hasNewAssignment(avlReport, blockAssigner)) {
            String eventDescription = "For vehicleId="
                    + vehicleStatus.getVehicleId()
                    + " the vehicle assignment is being "
                    + "changed to assignmentId="
                    + vehicleStatus.getAssignmentId();
            makeVehicleUnpredictableAndTerminateAssignment(
                vehicleStatus, eventDescription, VehicleEventType.ASSIGNMENT_CHANGED);
        }

        // If the vehicle has a block assignment from the AVLFeed
        // then use it.
        Block block = blockAssigner.getBlockAssignment(avlReport);
        if (block != null) {
            // There is a block assignment from AVL feed so use it.
            return matchVehicleToBlockAssignment(block, vehicleStatus);
        } else {
            // If there is a route assignment from AVL feed us it
            String routeId = blockAssigner.getRouteIdAssignment(avlReport);
            if (routeId != null) {
                // There is a route assignment so use it
                return matchVehicleToRouteAssignment(routeId, vehicleStatus);
            }
        }

        // This method called when there is an assignment from AVL feed. But
        // if that assignment is invalid then will make it here. Try the
        // auto assignment feature in case it is enabled.
        boolean autoAssigned = automaticallyMatchVehicleToAssignment(vehicleStatus);
        if (autoAssigned) return true;

        // There was no valid block or route assignment from AVL feed so can't
        // do anything. But set the block assignment for the vehicle
        // so it is up to date. This call also sets the vehicle state
        // to be unpredictable.
        BlockAssignmentMethod blockAssignmentMethod = null;
        vehicleStatus.unsetBlock(blockAssignmentMethod);
        return false;
    }

    /**
     * For when vehicle didn't get an assignment from the AVL feed and the vehicle previously was
     * predictable and was matched to an assignment then see if can continue to use the old
     * assignment.
     *
     * @param vehicleStatus
     */
    private void handlePredictableVehicleWithoutAvlAssignment(VehicleStatus vehicleStatus) {
        String oldAssignment = vehicleStatus.getAssignmentId();

        // Had a valid old assignment. If haven't had too many bad
        // assignments in a row then use the old assignment.
        if (vehicleStatus.getBadAssignmentsInARow() < coreProperties.getAllowableBadAssignments()) {
            logger.warn(
                    "AVL report did not include an assignment for "
                            + "vehicleId={} but badAssignmentsInARow={} which "
                            + "is less than allowableBadAssignments={} so using "
                            + "the old assignment={}",
                    vehicleStatus.getVehicleId(),
                    vehicleStatus.getBadAssignmentsInARow(),
                    coreProperties.getAllowableBadAssignments(),
                    vehicleStatus.getAssignmentId());

            // Create AVL report with the old assignment and then use it
            // to update the vehicle state
            AvlReport modifiedAvlReport = vehicleStatus.getAvlReport().toBuilder()
                .withAssignmentId(oldAssignment)
                .withAssignmentType(AssignmentType.PREVIOUS)
                .withVehicleName(null) // TODO: check if this is really needed
                .build();
            vehicleStatus.setAvlReport(modifiedAvlReport);
            matchNewFixForPredictableVehicle(vehicleStatus);

            // Increment the bad assignments count
            vehicleStatus.setBadAssignmentsInARow(vehicleStatus.getBadAssignmentsInARow() + 1);
        } else {
            // Vehicle was predictable but now have encountered too many
            // problem assignments. Therefore make vehicle unpredictable.
            String eventDescription = "VehicleId="
                    + vehicleStatus.getVehicleId()
                    + " was assigned to blockId="
                    + oldAssignment
                    + " but received "
                    + vehicleStatus.getBadAssignmentsInARow()
                    + " null assignments in a row, which is configured by "
                    + "transitclock.core.allowableBadAssignments to be too many, "
                    + "so making vehicle unpredictable.";
            makeVehicleUnpredictable(vehicleStatus.getVehicleId(), eventDescription, VehicleEventType.ASSIGNMENT_CHANGED);
        }
    }

    /**
     * For when vehicle is not predictable and didn't have previous assignment. Since this method is
     * to be called when vehicle isn't assigned and didn't get a valid assignment through the feed
     * should try to automatically assign the vehicle based on how it matches to a currently
     * unmatched block. If it can match the vehicle then this method fully processes the match,
     * generating predictions and such.
     *
     * @param vehicleStatus
     * @return true if auto assigned vehicle
     */
    private boolean automaticallyMatchVehicleToAssignment(VehicleStatus vehicleStatus) {
        // If actually creating a schedule based prediction
        if (vehicleStatus.isForSchedBasedPreds()) return false;

        if (!autoBlockAssignerProperties.isAutoAssignerEnabled()) {
            logger.info(
                    "Could not automatically assign vehicleId={} because " + "AutoBlockAssigner not enabled.",
                    vehicleStatus.getVehicleId());
            return false;
        }

        logger.info("Trying to automatically assign vehicleId={}", vehicleStatus.getVehicleId());

        // Try to match vehicle to a block assignment if that feature is enabled
        var autoAssigner = autoBlockAssignerFactory.createAssigner(vehicleStatus);
        TemporalMatch bestMatch = autoAssigner.autoAssignVehicleToBlockIfEnabled();
        if (bestMatch != null) {
            // Successfully matched vehicle to block so make vehicle predictable
            logger.info("Auto matched vehicleId={} to a block assignment. {}", vehicleStatus.getVehicleId(), bestMatch);

            // Update the state of the vehicle
            updateVehicleStateFromAssignment(
                    bestMatch,
                vehicleStatus,
                    BlockAssignmentMethod.AUTO_ASSIGNER,
                    bestMatch.getBlock().getId(),
                    "block");
            return true;
        }

        return false;
    }

    /**
     * For when don't have valid assignment for vehicle. If have a valid old assignment and haven't
     * gotten too many bad assignments in a row then simply use the old assignment. This is handy
     * for when the assignment portion of the AVL feed does not send assignment data for every
     * report.
     *
     * @param vehicleStatus
     */
    private void handleProblemAssignment(VehicleStatus vehicleStatus) {
        String oldAssignment = vehicleStatus.getAssignmentId();
        boolean wasPredictable = vehicleStatus.isPredictable();

        logger.info(
                "No assignment info for vehicleId={} so trying to assign " + "vehicle without it.",
                vehicleStatus.getVehicleId());

        // If the vehicle previously was predictable and had an assignment
        // then see if can continue to use the old assignment.
        if (wasPredictable && oldAssignment != null) {
            handlePredictableVehicleWithoutAvlAssignment(vehicleStatus);
        } else {
            // Vehicle wasn't predictable and didn't have previous assignment.
            // Since this method is to be called when vehicle isn't assigned
            // and didn't get an assignment through the feed should try to
            // automatically assign the vehicle based on how it matches to
            // a currently unmatched block.
            automaticallyMatchVehicleToAssignment(vehicleStatus);
        }
    }

    /**
     * Looks at the last match in vehicleState to determine if at end of block assignment. Updates
     * vehicleState if at end of block. Note that this will not always work since might not actually
     * get an AVL report that matches to the last stop.
     *
     * @param vehicleStatus
     * @return True if end of the block was reached with the last match.
     */
    private boolean handlePossibleEndOfBlock(VehicleStatus vehicleStatus) {
        // Determine if at end of block assignment
        TemporalMatch temporalMatch = vehicleStatus.getMatch();
        if (temporalMatch != null) {
            VehicleAtStopInfo atStopInfo = temporalMatch.getAtStop();
            if (atStopInfo != null && atStopInfo.atEndOfBlock(dbConfig, coreProperties)) {
                logger.info(
                        "For vehicleId={} the end of the block={} " + "was reached so will make vehicle unpredictable",
                        vehicleStatus.getVehicleId(),
                        temporalMatch.getBlock().getId());

                // At end of block assignment so remove it
                String eventDescription = "Block assignment "
                        + vehicleStatus.getBlock().getId()
                        + " ended for vehicle so it was made unpredictable.";
                makeVehicleUnpredictableAndTerminateAssignment(
                    vehicleStatus, eventDescription, VehicleEventType.END_OF_BLOCK);

                // Return that end of block reached
                return true;
            }
        }

        // End of block wasn't reached so return false
        return false;
    }

    /**
     * If schedule adherence is not within bounds then will try to match the vehicle to the
     * assignment again. This can be important if system is run for a while and then paused and then
     * started up again. Vehicle might continue to match to the pre-paused match, but by then the
     * vehicle might be on a whole different trip, causing schedule adherence to be really far off.
     * To prevent this the vehicle is re-matched to the assignment.
     *
     * <p>Updates vehicleState accordingly.
     *
     * @param vehicleStatus
     */
    private void verifyRealTimeSchAdh(VehicleStatus vehicleStatus) {
        // If no schedule then there can't be real-time schedule adherence
        if (vehicleStatus.getBlock() == null || vehicleStatus.getBlock().isNoSchedule()) return;

        logger.debug("Confirming real-time schedule adherence for vehicleId={}", vehicleStatus.getVehicleId());

        // Determine the schedule adherence for the vehicle
        TemporalDifference scheduleAdherence = realTimeSchedAdhProcessor.generate(vehicleStatus);

        // If vehicle is just sitting at terminal past its scheduled departure
        // time then indicate such as an event.
        if (vehicleStatus.getMatch().isWaitStop()
                && scheduleAdherence != null
                && scheduleAdherence.isLaterThan(coreProperties.getAllowableLateAtTerminalForLoggingEvent())
                && vehicleStatus.getMatch().getAtStop() != null) {
            // Create description for VehicleEvent
            String stopId = vehicleStatus.getMatch().getStopPath().getStopId();
            Stop stop = dbConfig.getStop(stopId);
            Route route = vehicleStatus.getMatch().getRoute(dbConfig);
            VehicleAtStopInfo stopInfo = vehicleStatus.getMatch().getAtStop();
            Integer scheduledDepartureTime = stopInfo.getScheduleTime().getDepartureTime();

            String description = "Vehicle "
                    + vehicleStatus.getVehicleId()
                    + " still at stop "
                    + stopId
                    + " \""
                    + stop.getName()
                    + "\" for route \""
                    + route.getName()
                    + "\" "
                    + scheduleAdherence
                    + ". Scheduled departure time was "
                    + Time.timeOfDayStr(scheduledDepartureTime);

            // Create, store in db, and log the VehicleEvent
            VehicleEvent vehicleEvent = new VehicleEvent(
                    vehicleStatus.getAvlReport(),
                    vehicleStatus.getMatch(),
                    VehicleEventType.NOT_LEAVING_TERMINAL,
                    description,
                    true, // predictable
                    false, // becameUnpredictable
                    null);// supervisor
            dataDbLogger.add(vehicleEvent);
        }

        // Make sure the schedule adherence is reasonable
        if (scheduleAdherence != null && !scheduleAdherence.isWithinBounds()) {
            logger.warn(
                    "For vehicleId={} schedule adherence {} is not "
                            + "between the allowable bounds. Therefore trying to match "
                            + "the vehicle to its assignmet again to see if get better "
                            + "temporal match by matching to proper trip.",
                    vehicleStatus.getVehicleId(),
                    scheduleAdherence);

            // Log that vehicle is being made unpredictable as a VehicleEvent
            String eventDescription = "Vehicle had schedule adherence of "
                    + scheduleAdherence
                    + " which is beyond acceptable "
                    + "limits. Therefore vehicle made unpredictable.";

            // Clear out match, make vehicle event, clear predictions.
            makeVehicleUnpredictable(vehicleStatus.getVehicleId(), eventDescription, VehicleEventType.NO_MATCH);

            // Schedule adherence not reasonable so match vehicle to assignment
            // again.
            matchVehicleToAssignment(vehicleStatus);
        }
    }

    /**
     * Determines the real-time schedule adherence and stores the value in the vehicleState. To be
     * called after the vehicle is matched.
     *
     * @param vehicleStatus
     */
    private void determineAndSetRealTimeSchAdh(VehicleStatus vehicleStatus) {
        // If no schedule then there can't be real-time schedule adherence
        if (vehicleStatus.getBlock() == null || vehicleStatus.getBlock().isNoSchedule()) return;

        logger.debug(
                "Determining and setting real-time schedule adherence for " + "vehicleId={}",
                vehicleStatus.getVehicleId());

        // Determine the schedule adherence for the vehicle
        TemporalDifference scheduleAdherence = realTimeSchedAdhProcessor.generate(vehicleStatus);

        // Store the schedule adherence with the vehicle
        vehicleStatus.setRealTimeSchedAdh(scheduleAdherence);
    }

    private static boolean unpredictableAssignmentsPatternInitialized = false;
    private static Pattern regExPattern = null;

    /**
     * Returns true if the assignment specified matches the regular expression for unpredictable
     * assignments.
     *
     * @param assignment
     * @return true if assignment matches regular expression
     */
    private boolean matchesUnpredictableAssignment(String assignment) {
        if (!unpredictableAssignmentsPatternInitialized) {
            String regEx = avlProperties.getUnpredictableAssignmentsRegEx();
            if (regEx != null && !regEx.isEmpty()) {
                regExPattern = Pattern.compile(regEx);
            }
            unpredictableAssignmentsPatternInitialized = true;
        }

        if (regExPattern == null) {
            return false;
        }

        return regExPattern.matcher(assignment).matches();
    }

    /**
     * Processes the AVL report by matching to the assignment and generating predictions and such.
     * Sets VehicleState for the vehicle based on the results. Also stores AVL report into the
     * database (if not in playback mode).
     *
     * @param avlReport The new AVL report to be processed
     * @param recursiveCall Set to true if this method is calling itself. Used to make sure that any
     *     bug can't cause infinite recursion.
     */
    private void lowLevelProcessAvlReport(AvlReport avlReport, boolean recursiveCall) {
        // Determine previous state of vehicle
        String vehicleId = avlReport.getVehicleId();
        VehicleStatus vehicleStatus = vehicleStatusManager.getStatus(vehicleId);

        // Since modifying the VehicleState should synchronize in case another
        // thread simultaneously processes data for the same vehicle. This
        // would be extremely rare but need to be safe.
        synchronized (vehicleStatus) {
            // Keep track of last AvlReport even if vehicle not predictable.
            vehicleStatus.setAvlReport(avlReport);

            // If part of consist and shouldn't be generating predictions
            // and such and shouldn't grab assignment the simply return
            // not that the last AVL report has been set for the vehicle.
            if (avlReport.ignoreBecauseInConsist()) {
                return;
            }

            // Do the matching depending on the old and the new assignment
            // for the vehicle.
            boolean matchAlreadyPredictableVehicle =
                    vehicleStatus.isPredictable() && !vehicleStatus.hasNewAssignment(avlReport, blockAssigner);
            boolean matchToNewAssignment = avlReport.hasValidAssignment()
                    && !matchesUnpredictableAssignment(avlReport.getAssignmentId())
                    && (!vehicleStatus.isPredictable() || vehicleStatus.hasNewAssignment(avlReport, blockAssigner))
                    && !vehicleStatus.previousAssignmentProblematic(avlReport, blockAssigner);

            if (matchAlreadyPredictableVehicle) {
                // Vehicle was already assigned and assignment hasn't
                // changed so update the match of where the vehicle is
                // within the assignment.
                matchNewFixForPredictableVehicle(vehicleStatus);
            } else if (matchToNewAssignment) {
                // New assignment from AVL feed so match the vehicle to it
                matchVehicleToAssignment(vehicleStatus);
            } else {
                // Handle bad assignment where don't have assignment or such.
                // Will try auto assigning a vehicle if that feature is enabled.
                handleProblemAssignment(vehicleStatus);
            }

            // If the last match is actually valid then generate associated
            // data like predictions and arrival/departure times.
            if (vehicleStatus.isPredictable() && vehicleStatus.lastMatchIsValid()) {
                // Reset the counter
                vehicleStatus.setBadAssignmentsInARow(0);

                // If vehicle is delayed as indicated by not making forward
                // progress then store that in the vehicle state
                handlePossibleVehicleDelay(vehicleStatus);

                // Determine and store the schedule adherence.
                determineAndSetRealTimeSchAdh(vehicleStatus);

                // Only continue processing if vehicle is still predictable
                // since calling checkScheduleAdherence() can make it
                // unpredictable if schedule adherence is really bad.
                if (vehicleStatus.isPredictable()) {
                    // Generates the corresponding data for the vehicle such as
                    // predictions and arrival times
                    matchProcessor.generateResultsOfMatch(vehicleStatus);

                    // If finished block assignment then should remove
                    // assignment
                    boolean endOfBlockReached = handlePossibleEndOfBlock(vehicleStatus);

                    // If just reached the end of the block and took the block
                    // assignment away and made the vehicle unpredictable then
                    // should see if the AVL report could be used to assign
                    // vehicle to the next assignment. This is needed for
                    // agencies like Zhengzhou which is frequency based and
                    // where each block assignment is only a single trip and
                    // when vehicle finishes one trip/block it can go into the
                    // next block right away.
                    if (endOfBlockReached) {
                        if (recursiveCall) {
                            // This method was already called recursively which
                            // means unassigned vehicle at end of block but then
                            // it got assigned to end of block again. This
                            // indicates a bug since vehicles at end of block
                            // shouldn't be reassigned to the end of the block
                            // again. Therefore log problem and don't try to
                            // assign vehicle again.
                            logger.error(
                                "lowLevelProcessAvlReport() called recursively, which is wrong. {}",
                                vehicleStatus);
                        } else {
                            // Actually process AVL report again to see if you can
                            // assign to new assignment.
                            lowLevelProcessAvlReport(avlReport, true);
                        }
                    } // End of if end of block reached
                }
            }

            // If called recursively (because end of block reached) but
            // didn't match to new assignment then don't want to store the
            // vehicle state since already did that.
            if (recursiveCall && !vehicleStatus.isPredictable())
                return;

            // Now that VehicleState has been updated need to update the
            // VehicleDataCache so that when data queried for API the proper
            // info is provided.
            vehicleDataCache.updateVehicle(vehicleStatus);

            // Write out current vehicle state to db so can join it with AVL
            // data from db and get historical context of AVL report.
            var dbVehicleState = new VehicleState(vehicleStatus, dbConfig);
            dataDbLogger.add(dbVehicleState);
        }
    }

    /**
     * Returns the GPS time of the last regular (non-schedule based) GPS report processed. Since
     * AvlClient filters out reports that are for a previous time for a vehicle even if the AVL feed
     * continues to feed old data that data will be ignored. In other words, the last AVL time will
     * be for that last valid AVL report.
     *
     * @return The GPS time in msec epoch time of last AVL report, or 0 if no last AVL report
     */
    public long lastAvlReportTime() {
        if (lastRegularReportProcessed == null)
            return 0;

        return lastRegularReportProcessed.getTime();
    }

    /**
     * For storing the last regular (non-schedule based) AvlReport so can determine if the AVL feed
     * is working. Makes sure that report is newer than the previous last regular report so that
     * ignore possibly old data that might come in from the AVL feed.
     *
     * @param avlReport The new report to possibly store
     */
    private void setLastAvlReport(AvlReport avlReport) {
        // Ignore schedule based predictions AVL reports since those are faked
        // and don't represent what is going on with the AVL feed
        if (avlReport.isForSchedBasedPreds())
            return;

        // Only store report if it is a newer one. In this way we ignore
        // possibly old data that might come in from the AVL feed.
        if (lastRegularReportProcessed == null || avlReport.getTime() > lastRegularReportProcessed.getTime()) {
            lastRegularReportProcessed = avlReport;
        }
    }

    /**
     * Returns the last regular (non-schedule based) AvlReport.
     */
    public AvlReport getLastAvlReport() {
        return lastRegularReportProcessed;
    }

    /**
     * Updates the VehicleState in the cache to have the new avlReport. Intended for when want to
     * update VehicleState AVL report but don't want to actually process the report, such as for
     * when get data too frequently and only want to fully process some of it yet still use latest
     * vehicle location so that vehicles move on map really smoothly.
     *
     * @param avlReport
     */
    void cacheAvlReportWithoutProcessing(AvlReport avlReport) {
        VehicleStatus vehicleStatus = vehicleStatusManager.getStatus(avlReport.getVehicleId());

        // Since modifying the VehicleState should synchronize in case another
        // thread simultaneously processes data for the same vehicle. This
        // would be extremely rare but need to be safe.
        synchronized (vehicleStatus) {
            // Update AVL report for cached VehicleState
            vehicleStatus.setAvlReport(avlReport);

            // Let vehicle data cache know that the vehicle state was updated
            // so that new IPC vehicle data will be created and cached and
            // made available to the API.
            vehicleDataCache.updateVehicle(vehicleStatus);
        }
    }

    /**
     * First does housekeeping for the AvlReport (stores it in db, logs it, etc). Processes the AVL
     * report by matching to the assignment and generating predictions and such. Sets VehicleState
     * for the vehicle based on the results. Also stores AVL report into the database (if not in
     * playback mode).
     *
     * @param avlReport The new AVL report to be processed
     */
    public void processAvlReport(AvlReport avlReport) {
        IntervalTimer timer = new IntervalTimer();

        // Handle special case where want to not use assignment from AVL
        // report, most likely because want to test automatic assignment
        // capability
        if (autoBlockAssignerProperties.isIgnoreAvlAssignments() && !avlReport.isForSchedBasedPreds()) {
            logger.info("Removing assignment from AVL report [{}] because transitclock.autoBlockAssigner.ignoreAvlAssignments=true!", avlReport);
            avlReport.setAssignment(null, AssignmentType.UNSET);
        }

        try (Session session = HibernateUtils.getSession()) {
            var config = VehicleToBlockConfigRepository.getVehicleToBlockConfigs(session, avlReport.getVehicleId(), new Date());
            Optional.ofNullable(config).ifPresent(c -> {
                avlReport.setAssignment(c.getBlockId(), AssignmentType.BLOCK_ID);
            });
        } catch (Exception e) {
            logger.error("Something happened while processing {}", avlReport, e);
        }

        // The beginning of processing AVL data is an important milestone
        // in processing data so log it as info.
        logger.debug("Processing {}", avlReport);

        // Record when the AvlReport was actually processed. This is done here
        // so that the value will be set when the avlReport is stored in the
        // database using the DbLogger.
        avlReport.setTimeProcessed();

        // Keep track of last AVL report processed so can determine if AVL
        // feed is up
        setLastAvlReport(avlReport);

        // Make sure that vehicle configuration is in cache and database
        vehicleDataCache.cacheVehicleConfig(avlReport);

        // Store the AVL report into the database
        if (!coreProperties.isOnlyNeedArrivalDepartures() && !avlReport.isForSchedBasedPreds())
            dataDbLogger.add(avlReport);

        // If any vehicles have timed out then handle them. This is done
        // here instead of using a regular timer so that it will work
        // even when in playback mode or when reading batch data.
        avlReportRegistry.storeAvlReport(avlReport);

        // Do the low level work of matching vehicle and then generating results
        lowLevelProcessAvlReport(avlReport, false);
        logger.debug("Processing AVL report took {}msec", timer);
    }
}
