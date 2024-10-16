/* (C)2023 */
package org.transitclock.core.avl.assigner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.transitclock.core.TemporalDifference;
import org.transitclock.core.TravelTimes;
import org.transitclock.core.VehicleStatus;
import org.transitclock.core.avl.space.SpatialMatch;
import org.transitclock.core.avl.space.SpatialMatcher;
import org.transitclock.core.avl.time.TemporalMatch;
import org.transitclock.core.avl.time.TemporalMatcher;
import org.transitclock.core.dataCache.VehicleDataCache;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.domain.structs.Block;
import org.transitclock.domain.structs.Trip;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.AutoBlockAssignerProperties;
import org.transitclock.properties.AvlProperties;
import org.transitclock.properties.CoreProperties;
import org.transitclock.utils.IntervalTimer;
import org.transitclock.utils.Time;

import lombok.extern.slf4j.Slf4j;

/**
 * For automatically assigning a vehicle to an available block by determining both spatial and
 * temporal match. When there is a match for an AVL report then a previous AVL report is also
 * investigated to make sure that the vehicle really is moving along a trip.
 *
 * <p>Tries to match to every non-assigned block. Looks only at the trips that are currently active
 * so that doesn't try to look at all possibilities. Caches matches for trip patterns so that
 * doesn't need to do a spatial match to a trip pattern multiple times. Stationary vehicles are not
 * matched because system requires a previous AVL report that is a minimum distance away from the
 * current report. Even with all of this optimization it can take a while to match a vehicle since
 * have to look at every stop path for each available trip pattern. For an agency with ~250
 * available blocks this can take about 1/2 a second.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class AutoBlockAssigner {

    // For keeping track of last time vehicle auto assigned so that can limit
    // how frequently it is done. Keyed on vehicleId
    private static final Map<String, Long> timeVehicleLastAutoAssigned = new HashMap<>();

    // Contains the results of spatial matching the avl report to the
    // specified trip pattern. Keyed on trip pattern ID. Note: since the spatial
    // matches are cached and reused the block member will not be correct
    private final Map<String, SpatialMatch> spatialMatchCache = new HashMap<>();

    // The vehicle state is repeatedly used, so it is a member, so it doesn't
    // have to be passed around to various methods.
    private final VehicleStatus vehicleStatus;

    private final AutoBlockAssignerProperties autoBlockAssignerProperties;
    private final CoreProperties coreProperties;
    private final AvlProperties avlProperties;
    private final VehicleDataCache vehicleDataCache;
    private final TravelTimes travelTimes;
    private final VehicleStatusManager vehicleStatusManager;
    private final TemporalMatcher temporalMatcher;
    private final DbConfig dbConfig;
    private final BlockInfoProvider blockInfoProvider;

    public AutoBlockAssigner(AutoBlockAssignerProperties autoBlockAssignerProperties,
                             CoreProperties coreProperties,
                             AvlProperties avlProperties,
                             VehicleStatus vehicleStatus,
                             VehicleDataCache vehicleDataCache,
                             TravelTimes travelTimes,
                             VehicleStatusManager vehicleStatusManager,
                             TemporalMatcher temporalMatcher,
                             DbConfig dbConfig,
                             BlockInfoProvider blockInfoProvider) {
        this.autoBlockAssignerProperties = autoBlockAssignerProperties;
        this.coreProperties = coreProperties;
        this.avlProperties = avlProperties;
        this.vehicleStatus = vehicleStatus;
        this.vehicleDataCache = vehicleDataCache;
        this.travelTimes = travelTimes;
        this.vehicleStatusManager = vehicleStatusManager;
        this.temporalMatcher = temporalMatcher;
        this.dbConfig = dbConfig;
        this.blockInfoProvider = blockInfoProvider;
    }


    /**
     * @return the current AVL report from vehicleState member
     */
    private AvlReport getAvlReport() {
        return vehicleStatus.getAvlReport();
    }

    /**
     * The previousAvlReport should be a good distance away from the current AVL report in order to
     * really be sure that vehicle is traveling along the trip.
     *
     * @return the previous AVL report, at least min distance away from current AVL report, from
     *     vehicleState member
     */
    private AvlReport getPreviousAvlReport() {
        double minDistance = autoBlockAssignerProperties.getMinDistanceFromCurrentReport();
        return vehicleStatus.getPreviousAvlReport(minDistance);
    }

    /**
     * Determines if a block doesn't have a non-schedule based vehicle associated with it. This
     * means that the block assignment is available for trying to automatically assign a vehicle to
     * it. Schedule based vehicles don't count because even when have a schedule based vehicle still
     * want to assign a real vehicle to that assignment when possible.
     *
     * @param blockId The block assignment to examine
     * @return True if block is available to be assigned (doesn't have a regular vehicle assigned to
     *     it.
     */
    private boolean isBlockUnassigned(String blockId) {
        Collection<String> vehicleIdsForBlock = vehicleDataCache.getVehiclesByBlockId(blockId);
        // If no vehicles associated with the block then it is definitely
        // unassigned.
        if (vehicleIdsForBlock.isEmpty()) {
            return true;
        }

        // There are vehicles assigned to the block but still need to see if
        // they are schedule based vehicles or not
        for (String vehicleId : vehicleIdsForBlock) {
            // If a regular vehicle instead of one for schedule based
            // predictions then the block has a vehicle assigned to it,
            // meaning it is not unassigned
            VehicleStatus vehiclestate = vehicleStatusManager.getStatus(vehicleId);
            if (!vehiclestate.isForSchedBasedPreds()) {
                return false;
            }
        }

        // Block doesn't have a non-schedule based vehicle so it is unassigned
        return true;
    }

    /**
     * Determines which blocks are currently active and are not assigned to a vehicle, meaning that
     * they are available for assignment.
     *
     * @return List of blocks that are available for assignment. Can be empty but not null
     */
    private List<Block> unassignedActiveBlocks() {
        List<Block> currentlyUnassignedBlocks = new ArrayList<>();
        List<Block> activeBlocks = blockInfoProvider.getCurrentlyActiveBlocks();
        for (Block block : activeBlocks) {
            if (isBlockUnassigned(block.getId())) {
                // No vehicles assigned to this active block so should see
                // if the vehicle currently trying to assign can match to it
                currentlyUnassignedBlocks.add(block);
            }
        }

        return currentlyUnassignedBlocks;
    }

    /**
     * Determines the best match by looking at both the current AVL report and the previous one.
     * Only for block assignments that do not have a schedule.
     *
     * @param block
     * @return The best adequate match, or null if there isn't an adequate match
     */
    private TemporalMatch bestNoScheduleMatch(Block block) {
        if (!block.isNoSchedule()) {
            logger.error("Called bestNoScheduleMatch() on block that has a " + "schedule. {}", block);
            return null;
        }

        // Determine all potential spatial matches for the block that are
        // not layovers. Won't be a layover match anyways since this method
        // is only for use with no schedule assignments.
        AvlReport avlReport = vehicleStatus.getAvlReport();
        List<Trip> potentialTrips = block.getTripsCurrentlyActive(dbConfig, avlReport);
        var spatialMatches = new SpatialMatcher(dbConfig, coreProperties, avlProperties).getSpatialMatchesForAutoAssigning(getAvlReport(), block, potentialTrips);
        if (spatialMatches.isEmpty()) {
            return null;
        }

        // Determine all possible spatial matches for the previous AVL report so
        // that can make sure that it too matches the assignment.
        AvlReport previousAvlReport = getPreviousAvlReport();
        var prevSpatialMatches = new SpatialMatcher(dbConfig, coreProperties, avlProperties).getSpatialMatchesForAutoAssigning(previousAvlReport, block, potentialTrips);
        if (prevSpatialMatches.isEmpty()) {
            return null;
        }

        // Determine params needed in the following for loop
        int timeOfDayInSecs = dbConfig.getTime().getSecondsIntoDay(previousAvlReport.getTime());
        long avlTimeDifferenceMsec = avlReport.getTime() - previousAvlReport.getTime();

        // Go through each spatial match for both the current AVL report and
        // the previous AVL report. Find one where the expected travel time
        // closely matches the time between the AVL reports.
        TemporalDifference bestTemporalDifference = null;
        SpatialMatch bestSpatialMatch = null;
        for (SpatialMatch prevSpatialMatch : prevSpatialMatches) {
            for (SpatialMatch spatialMatch : spatialMatches) {
                // Determine according to the historic travel times how long
                // it was expected to take to travel from the previous match
                // to the current one.
                int expectedTravelTimeMsec = travelTimes
                        .expectedTravelTimeBetweenMatches(avlReport.getVehicleId(), timeOfDayInSecs, prevSpatialMatch, spatialMatch);
                TemporalDifference differenceFromExpectedTime =
                        new TemporalDifference((int) (expectedTravelTimeMsec - avlTimeDifferenceMsec), coreProperties);

                // If the travel time is too far off from the time between the
                // AVL reports then it is not a good match so continue on to
                // check out the other spatial matches.
                if (!differenceFromExpectedTime.isWithinBounds(autoBlockAssignerProperties.getAllowableEarlySeconds(), autoBlockAssignerProperties.getAllowableLateSeconds()))
                    continue;

                // If this match is the best one found temporally then remember it
                if (differenceFromExpectedTime.betterThan(bestTemporalDifference)) {
                    bestTemporalDifference = differenceFromExpectedTime;
                    bestSpatialMatch = spatialMatch;
                }
            }
        }

        // Return the best temporal match if an adequate one was found
        if (bestSpatialMatch == null) {
            return null;
        }

        return new TemporalMatch(bestSpatialMatch, bestTemporalDifference);
    }

    /**
     * Gets the spatial matches of the AVL report for the specified block. Only looks at trips that
     * are currently active in order to speed things up. Checking each active trip is still far too
     * costly. Therefore uses a cache of spatial matches by trip pattern ID. If a spatial match was
     * already determine for the trip pattern then the cached value is returned.
     *
     * @param avlReport The AVL report to be matched
     * @param block The block to match the AVL report to
     * @return All possible spatial matches
     */
    private List<SpatialMatch> getSpatialMatches(AvlReport avlReport, Block block) {
        // Convenience variable
        final String vehicleId = avlReport.getVehicleId();

        // For returning results of this method
        final List<SpatialMatch> spatialMatches = new ArrayList<>();

        // Determine which trips are currently active so that don't bother
        // looking at all trips
        final List<Trip> activeTrips = block.getTripsCurrentlyActive(dbConfig, avlReport);

        // Determine trips that need to look at for spatial matches because
        // haven't looked at the associated trip pattern yet.
        final List<Trip> tripsNeedToInvestigate = new ArrayList<>();

        // Go through the activeTrips and determine which ones actually need
        // to be investigated. If the associated trip pattern was already
        // examined then use the spatial match (or null) previous found
        // and cached. If it is a new trip pattern then add the trip to the
        // list of trips that need to be investigated.
        for (Trip trip : activeTrips) {
            String tripPatternId = trip.getTripPattern().getId();

            logger.debug(
                    "For vehicleId={} checking tripId={} with " + "tripPatternId={} for spatial " + "matches.",
                    vehicleId,
                    trip.getId(),
                    trip.getTripPattern().getId());

            // If spatial match results already in cache...
            if (spatialMatchCache.containsKey(tripPatternId)) {
                // Already processed this trip pattern so use cached results.
                // Can be null
                SpatialMatch previouslyFoundMatch = spatialMatchCache.get(tripPatternId);

                // If there actually was a successful spatial match to the
                // trip pattern in the cache then add it to spatialMatches list
                if (previouslyFoundMatch != null) {
                    // The cached match has the wrong trip info so need
                    // to create an equivalent match with the proper trip block
                    // info
                    SpatialMatch matchWithProperBlock = new SpatialMatch(dbConfig, previouslyFoundMatch, trip, coreProperties);

                    // Add to list of spatial matches to return
                    spatialMatches.add(matchWithProperBlock);

                    logger.debug(
                            "For vehicleId={} for tripId={} with "
                                    + "tripPatternId={} using previously cached "
                                    + "spatial match.",
                            vehicleId,
                            trip.getId(),
                            tripPatternId);
                } else {
                    logger.debug(
                            "For vehicleId={} for tripId={} with "
                                    + "tripPatternId={} found from cache that there "
                                    + "is no spatial match.",
                            vehicleId,
                            trip.getId(),
                            tripPatternId);
                }
            } else {
                // New trip pattern so need to investigate it to search for
                // potential spatial matches
                tripsNeedToInvestigate.add(trip);

                logger.debug(
                        "For vehicleId={} for tripId={} with "
                                + "tripPatternId={} have not previously determined "
                                + "spatial matches so will do so now.",
                        vehicleId,
                        trip.getId(),
                        tripPatternId);
            }
        }

        // Investigate the trip patterns not in the cache. Determine potential
        // spatial matches that are not layovers. If match is to a layover can
        // ignore it since layover matches are far too flexible to really be
        // considered a spatial match
        var newSpatialMatches = new SpatialMatcher(dbConfig, coreProperties, avlProperties).getSpatialMatchesForAutoAssigning(avlReport, block, tripsNeedToInvestigate);

        // Add newly discovered matches to the cache and to the list of spatial
        // matches to be returned
        for (SpatialMatch newSpatialMatch : newSpatialMatches) {
            logger.debug(
                    "For vehicleId={} for tripId={} with " + "tripPatternId={} found new spatial match {}.",
                    vehicleId,
                    newSpatialMatch.getTrip().getId(),
                    newSpatialMatch.getTrip().getTripPattern().getId(),
                    newSpatialMatch);

            // Cache it
            spatialMatchCache.put(newSpatialMatch.getTrip().getTripPattern().getId(), newSpatialMatch);

            // Add to list of spatial matches to return
            spatialMatches.add(newSpatialMatch);
        }

        // Also need to add to the cache the trips patterns that investigated
        // but did not find a spatial match. This is really important because
        // when don't find a spatial match for a trip pattern don't want to
        // waste time searching it again to find out again that it doesn't
        // have a match.
        for (Trip tripInvestigated : tripsNeedToInvestigate) {
            // If the trip that was investigated did not result in spatial
            // match then remember that by storing a null spatial match
            // for the trip pattern
            String tripPatternId = tripInvestigated.getTripPattern().getId();
            boolean spatialMatchFound = false;
            for (SpatialMatch newSpatialMatch : newSpatialMatches) {
                String spatialMatchTripPatternId = newSpatialMatch.getTrip().getTripPattern().getId();
                if (spatialMatchTripPatternId.equals(tripPatternId)) {
                    spatialMatchFound = true;
                }
            }
            // If no spatial match found for the trip pattern that just
            // investigated then mark in cache that no match
            if (!spatialMatchFound) {
                spatialMatchCache.put(tripPatternId, null);

                logger.debug(
                        "For vehicleId={} for tripId={} with "
                                + "tripPatternId={} no spatial match found so storing "
                                + "that info in cache for investigating next block.",
                        vehicleId,
                        tripInvestigated.getId(),
                        tripInvestigated.getTripPattern().getId());
            }
        }

        // Return the results
        return spatialMatches;
    }

    /**
     * Gets the spatial matches of the AVL report for the specified block. Only looks at trips that
     * are currently active in order to speed things up. Doesn't use cached value from when
     * investigating the current AVL report. Therefore this method is useful for checking previous
     * AVL reports.
     *
     * @param avlReport The AVL report to be matched
     * @param block The block to match the AVL report to
     * @return list of spatial matches for the avlReport
     */
    private List<SpatialMatch> getSpatialMatchesWithoutCache(AvlReport avlReport, Block block) {
        // Determine which trips are currently active so that don't bother
        // looking at all trips
         var activeTrips = block.getTripsCurrentlyActive(dbConfig, avlReport);

        // Get and return the spatial matches
        return new SpatialMatcher(dbConfig, coreProperties, avlProperties).getSpatialMatchesForAutoAssigning(avlReport, block, activeTrips);
    }

    /**
     * Determines best non-layover match for the AVL report to the specified block. First finds
     * spatial matches and then finds one that best matches to the schedule. If the match is
     * adequate spatial and temporally then the match is returned. Intended for schedule based
     * blocks only.
     *
     * @param avlReport The AVL report to be matched
     * @param block The block to match the AVL report to
     * @param useCache true if can use match cache. The match cache is useful for when matching the
     *     current AVL report because it is more efficient. But for matching the previous AVL report
     *     don't want to use the cache because the cache was for the original AVL report.
     * @return The best match if there is one. Null if there is not a valid match
     */
    private TemporalMatch bestTemporalMatch(AvlReport avlReport, Block block, boolean useCache) {
        // Determine all potential spatial matches for the block
        var spatialMatches = useCache ? getSpatialMatches(avlReport, block) : getSpatialMatchesWithoutCache(avlReport, block);

        // Now that have the spatial matches determine the best temporal match
        TemporalMatch bestMatch = temporalMatcher.getBestTemporalMatchComparedToSchedule(avlReport, spatialMatches);

        // Want to be pretty restrictive about matching to avoid false
        // positives. At the same time, want to not have a temporal match
        // that matches but not all that well cause the auto matcher to
        // think that can't match the vehicle due to the matches being
        // ambiguous. Therefore want to be more restrictive temporally
        // with respect to the temporal matches used. So throw out the
        // temporal match unless it is pretty close to the scheduled time.
        if (bestMatch != null) {
            TemporalDifference diff = bestMatch.getTemporalDifference();
            boolean isWithinBounds = diff.isWithinBounds(autoBlockAssignerProperties.getAllowableEarlySeconds(), autoBlockAssignerProperties.getAllowableLateSeconds());
            if (!isWithinBounds) {
                // Vehicle is too early or too late to auto match the block so
                // return null.
                logger.info(
                        "When trying to automatically assign vehicleId={} "
                                + "to blockId={} got a temporal match but the time "
                                + "difference {} was not within allowed bounds of "
                                + "allowableEarlySeconds={} and "
                                + "allowableLateSeconds={}. {}",
                        avlReport.getVehicleId(),
                        block.getId(),
                        diff,
                        autoBlockAssignerProperties.getAllowableEarlySeconds(),
                        autoBlockAssignerProperties.getAllowableLateSeconds(),
                        bestMatch);
                return null;
            }
        }

        // Return the temporal match, which could be null
        return bestMatch;
    }

    /**
     * Returns best schedule based match. Only for block assignments that have a schedule (are not
     * frequency based).
     *
     * @param block The block to try to match to
     * @return Best TemporalMatch to the block assignment, or null if no adequate match
     */
    private TemporalMatch bestScheduleMatch(Block block) {
        IntervalTimer timer = new IntervalTimer();
        AvlReport avlReport = getAvlReport();
        String vehicleId = avlReport.getVehicleId();
        String blockId = block.getId();

        // Make sure this method is called appropriately
        if (block.isNoSchedule()) {
            logger.error(
                    "Called bestScheduleMatch() on block that does not " + "have a schedule. {}",
                    block.toShortString());
            return null;
        }

        // Determine the best temporal match if there is one. Use cache to speed
        // up processing.
        TemporalMatch bestMatch = bestTemporalMatch(avlReport, block, true);

        logger.debug(
                "For vehicleId={} and blockId={} calling " + "bestTemporalMatch() took {}msec",
                vehicleId,
                blockId,
                timer);

        // If did not find an adequate temporal match then done
        if (bestMatch == null) {
            return null;
        }

        // Found a valid temporal match for the AVL report to the block
        logger.debug(
                "Found valid match for vehicleId={} and blockId={} "
                        + "and AVL report={} . Therefore will see if previous AVL "
                        + "report also matches. The bestMatch={}",
                vehicleId,
                blockId,
                avlReport,
                bestMatch);

        // Make sure that previous AVL report also matches and
        // that it matches to block before the current AVL report.
        // Don't use cache since cache contains matches using the
        // current AVL report whereas here we are interested in the
        // previous AVL report.
        AvlReport previousAvlReport = getPreviousAvlReport();
        TemporalMatch previousAvlReportBestMatch = bestTemporalMatch(previousAvlReport, block, false);

        logger.debug(
                "For vehicleId={} and blockId={} calling " + "bestTemporalMatch() for previous AVL report took {}msec",
                vehicleId,
                blockId,
                timer);

        if (previousAvlReportBestMatch != null && previousAvlReportBestMatch.lessThanOrEqualTo(bestMatch)) {
            // Previous AVL report also matches appropriately.
            // Therefore, return this temporal match as appropriate one.
            logger.debug(
                    "For vehicleId={} also found appropriate "
                            + "match for previous AVL report {}. Previous "
                            + "match was {}",
                    avlReport.getVehicleId(),
                    previousAvlReport,
                    previousAvlReportBestMatch);
            return bestMatch;
        }

        // The previous AVL report did not match the block
        logger.debug(
                "For vehicleId={} did NOT get valid match for " + "previous AVL report {}. Previous match was {} ",
                avlReport.getVehicleId(),
                previousAvlReport,
                previousAvlReportBestMatch);
        return null;
    }

    /**
     * Goes through all the currently active blocks and tries to match the AVL report to them.
     * Returns list of valid temporal matches. Ignores layover matches since they are too lenient to
     * indicate a valid match. Also requires a previous AVL report to match appropriately to make
     * sure that vehicle really matches and isn't just sitting there and isn't going in other
     * direction or crossing route and matching only momentarily.
     *
     * @return A non-null list of TemporalMatches. Will be empty if there are no valid matches.
     */
    private List<TemporalMatch> determineTemporalMatches() {
        // Convenience variable for logging
        String vehicleId = vehicleStatus.getVehicleId();

        // The list of matches to return
        List<TemporalMatch> validMatches = new ArrayList<>();

        // Only want to try to auto assign if there is also a previous AVL
        // report that is significantly away from the current report. This
        // way we avoid trying to match non-moving vehicles which are
        // not in service.
        if (getPreviousAvlReport() == null) {
            // There was no previous AVL report far enough away from the
            // current one so return empty list of matches
            logger.info(
                    "In AutoBlockAssigner.bestMatch() cannot auto "
                            + "assign vehicle because could not find valid previous "
                            + "AVL report in history for vehicleId={} further away "
                            + "than {}m from current AVL report {}",
                    vehicleId,
                    autoBlockAssignerProperties.getMinDistanceFromCurrentReport(),
                    getAvlReport());
            return validMatches;
        }

        // So can see how long the search takes
        IntervalTimer timer = new IntervalTimer();

        // Determine which blocks to examine. If agency configured such that
        // blocks are to be exclusive then only look at the ones currently
        // not used. But if not to be exclusive, such as for no schedule based
        // routes, then look at all active blocks.
        List<Block> blocksToExamine = coreProperties.isExclusiveBlockAssignments()
                ? unassignedActiveBlocks()
                : blockInfoProvider.getCurrentlyActiveBlocks();

        if (blocksToExamine.isEmpty()) {
            logger.info("No currently active blocks to assign vehicleId={} to.", vehicleId);
        } else {
            logger.info("For vehicleId={} examining {} blocks for matches.", vehicleId, blocksToExamine.size());
        }

        // For each active block that is currently unassigned...
        for (Block block : blocksToExamine) {
            logger.debug(
                    "For vehicleId={} examining blockId={} for match. The block contains the routes {}. {}",
                    vehicleId,
                    block.getId(),
                    block.getRouteIds(),
                    block.toShortString());

            // Determine best match for the block depending on whether the
            // block is schedule based or not
            TemporalMatch bestMatch = block.isNoSchedule() ? bestNoScheduleMatch(block) : bestScheduleMatch(block);
            if (bestMatch != null) {
                validMatches.add(bestMatch);
            }
        }

        // Return the valid matches that were found
        logger.info(
                "Total time for determining possible auto assignment " + "temporal matches for vehicleId={} was {}msec",
                vehicleId,
                timer);
        return validMatches;
    }

    /**
     * Determines if the auto assigner is being called too recently, as specified by the
     * transitclock.autoBlockAssigner.minTimeBetweenAutoAssigningSecs property. This is important
     * because auto assigning is quite costly for agencies with many available blocks. If have high
     * reporting rate and many available blocks then the system can get bogged down just doing auto
     * assigning.
     *
     * @param vehicleStatus
     * @return true if was too recently called for the vehicle
     */
    private boolean tooRecent(VehicleStatus vehicleStatus) {
        // Convenience variables
        String vehicleId = vehicleStatus.getVehicleId();
        long gpsTime = vehicleStatus.getAvlReport().getTime();

        // Determine last time vehicle was auto assigned
        Long lastTime = timeVehicleLastAutoAssigned.get(vehicleId);

        // If first time dealing with the vehicle then it is not too recent
        if (lastTime == null) {
            // Store the time for the vehicle for next time this method is called
            timeVehicleLastAutoAssigned.put(vehicleId, gpsTime);
            return false;
        }

        // Return true if not enough time elapsed
        long elapsedSecs = (gpsTime - lastTime) / Time.MS_PER_SEC;
        boolean tooRecent = elapsedSecs < autoBlockAssignerProperties.getMinTimeBetweenAutoAssigningSecs();

        logger.debug(
                "For vehicleId={} tooRecent={} elapsedSecs={} lastTime={} "
                        + "gpsTime={} minTimeBetweenAutoAssigningSecs={} ",
                vehicleId,
                tooRecent,
                elapsedSecs,
                Time.timeStrMsec(lastTime),
                Time.timeStrMsec(gpsTime),
                autoBlockAssignerProperties.getMinTimeBetweenAutoAssigningSecs());

        if (tooRecent) {
            logger.info(
                    "For vehicleId={} too recent to previous time that "
                            + "tried to autoassign. Therefore not autoassigning."
                            + "ElapsedSecs={} lastTime={} gpsTime={} "
                            + "minTimeBetweenAutoAssigningSecs={} ",
                    vehicleId,
                    elapsedSecs,
                    Time.timeStrMsec(lastTime),
                    Time.timeStrMsec(gpsTime),
                    autoBlockAssignerProperties.getMinTimeBetweenAutoAssigningSecs());
        } else {
            // Not too recent so should auto assign. Therefore store the time
            // for the vehicle for next time this method is called
            timeVehicleLastAutoAssigned.put(vehicleId, gpsTime);
        }

        return tooRecent;
    }

    /**
     * For trying to match vehicle to a active but currently unused block and the auto assigner is
     * enabled. If auto assigner is not enabled then returns null. Goes through all the currently
     * active blocks and tries to match the AVL report to them. Returns a TemporalMatch if there is
     * a single block that can be successfully matched to. Ignores layover matches since they are
     * too lenient to indicate a valid match. Also requires a previous AVL report to match
     * appropriately to make sure that vehicle really matches and isn't just sitting there and isn't
     * going in other direction or crossing route and matching only momentarily.
     *
     * @return A TemporalMatch if there is a single valid one, otherwise null
     */
    public TemporalMatch autoAssignVehicleToBlockIfEnabled() {
        // If the auto assigner is not enabled then simply return null for
        // the match
        if (!autoBlockAssignerProperties.isAutoAssignerEnabled())
            return null;

        // If auto assigner called too recently for vehicle then return
        if (tooRecent(vehicleStatus)) return null;

        String vehicleId = vehicleStatus.getVehicleId();
        logger.info("Determining possible auto assignment match for {}", vehicleStatus.getAvlReport());

        // Determine all the valid matches
        List<TemporalMatch> matches = determineTemporalMatches();

        // If no matches then not successful
        if (matches.isEmpty()) {
            logger.info("Found no valid matches for vehicleId={}", vehicleId);
            return null;
        }

        // If more than a single match then situation is ambiguous and we can't
        // consider that a match
        if (matches.size() > 1) {
            logger.info(
                    "Found multiple matches ({}) for vehicleId={}. " + "Therefore could not auto assign vehicle. {}",
                    matches.size(),
                    vehicleId,
                    matches);
            return null;
        }

        // Found a single match so return it
        logger.info("Found single valid match for vehicleId={}. {}", vehicleId, matches.get(0));
        return matches.get(0);
    }
}
