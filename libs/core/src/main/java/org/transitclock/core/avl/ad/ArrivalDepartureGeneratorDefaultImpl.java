/* (C)2023 */
package org.transitclock.core.avl.ad;

import java.util.ArrayList;
import java.util.Date;

import org.transitclock.core.Indices;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.TravelTimes;
import org.transitclock.core.VehicleAtStopInfo;
import org.transitclock.core.VehicleStatus;
import org.transitclock.core.avl.space.SpatialMatch;
import org.transitclock.core.dataCache.DwellTimeModelCacheInterface;
import org.transitclock.core.dataCache.HoldingTimeCache;
import org.transitclock.core.dataCache.StopArrivalDepartureCacheInterface;
import org.transitclock.core.dataCache.TripDataHistoryCacheInterface;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.core.dataCache.frequency.FrequencyBasedHistoricalAverageCache;
import org.transitclock.core.dataCache.scheduled.ScheduleBasedHistoricalAverageCache;
import org.transitclock.core.holdingmethod.HoldingTimeGenerator;
import org.transitclock.core.prediction.accuracy.PredictionAccuracyModule;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.domain.structs.Arrival;
import org.transitclock.domain.structs.ArrivalDeparture;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.domain.structs.Block;
import org.transitclock.domain.structs.Departure;
import org.transitclock.domain.structs.HoldingTime;
import org.transitclock.domain.structs.Route;
import org.transitclock.domain.structs.Stop;
import org.transitclock.domain.structs.Trip;
import org.transitclock.domain.structs.VehicleEvent;
import org.transitclock.domain.structs.VehicleEventType;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.ArrivalsDeparturesProperties;
import org.transitclock.properties.CoreProperties;
import org.transitclock.service.dto.IpcArrivalDeparture;
import org.transitclock.utils.Time;

import lombok.extern.slf4j.Slf4j;

/**
 * For determining Arrival/Departure times based on a new GPS report and corresponding
 * TemporalMatch.
 *
 * <p>This code unfortunately turned out to be rather complicated such that all the goals could be
 * met. But the Arrival/Departure generation is critical because it servers as the foundation for
 * both determining historic travel and stop times and for schedule adherence reports. Therefore
 * they must be as accurate as possible.
 *
 * <p>The goals for the Arrival/Departure generation are:
 *
 * <ul>
 *   <li>Must be as accurate as possible
 *   <li>Must work whether the AVL reporting rate is every few seconds or just once every few
 *       minutes
 *   <li>Must work even though stop locations and AVL locations are not completely accurate. If
 *       vehicle stops 40m before the stop the arrival should still be determined as accurately as
 *       possible.
 *   <li>Arrival, Departure, and Match times must be unique for a vehicle such that Departure for a
 *       stop is always after the Arrival. And the Match which is not at a stop will be between the
 *       Departure time for one stop and the Arrival time for the subsequent stop.
 *   <li>Arrival times at end of trip are recorded even if there are no other AVL reports associated
 *       with that trip. This is important because the last stop for a trip is always considered a
 *       timepoint for schedule adherence reports.
 * </ul>
 *
 * <p>Key method used to achieve the goals is to not just interpolate between AVL reports but to
 * extrapolate in order to determine when a vehicle really arrives/departs a stop. If would just
 * interpolate then wouldn't be taking the time actually stopped at the stop into account. Instead,
 * need to use travel speed and distance to determine from the last AVL report when arrived or
 * departed a stop.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class ArrivalDepartureGeneratorDefaultImpl implements ArrivalDepartureGenerator {
    private final ScheduleBasedHistoricalAverageCache scheduleBasedHistoricalAverageCache;
    private final FrequencyBasedHistoricalAverageCache frequencyBasedHistoricalAverageCache;
    private final HoldingTimeCache holdingTimeCache;
    private final VehicleStatusManager vehicleStatusManager;
    private final HoldingTimeGenerator holdingTimeGenerator;
    private final TravelTimes travelTimes;
    private final TripDataHistoryCacheInterface tripDataHistoryCacheInterface;
    private final StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface;
    private final DwellTimeModelCacheInterface dwellTimeModelCacheInterface;
    private final DataDbLogger dataDbLogger;
    private final DbConfig dbConfig;
    private final ArrivalsDeparturesProperties arrivalsDeparturesProperties;
    private final CoreProperties coreProperties;
    private final PredictionAccuracyModule predictionAccuracyModule;

    public ArrivalDepartureGeneratorDefaultImpl(ScheduleBasedHistoricalAverageCache scheduleBasedHistoricalAverageCache,
                                                FrequencyBasedHistoricalAverageCache frequencyBasedHistoricalAverageCache,
                                                HoldingTimeCache holdingTimeCache,
                                                VehicleStatusManager vehicleStatusManager,
                                                HoldingTimeGenerator holdingTimeGenerator,
                                                TravelTimes travelTimes,
                                                TripDataHistoryCacheInterface tripDataHistoryCacheInterface,
                                                StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface,
                                                DwellTimeModelCacheInterface dwellTimeModelCacheInterface,
                                                DataDbLogger dataDbLogger,
                                                DbConfig dbConfig,
                                                ArrivalsDeparturesProperties arrivalsDeparturesProperties,
                                                CoreProperties coreProperties,
                                                PredictionAccuracyModule predictionAccuracyModule) {
        this.scheduleBasedHistoricalAverageCache = scheduleBasedHistoricalAverageCache;
        this.frequencyBasedHistoricalAverageCache = frequencyBasedHistoricalAverageCache;
        this.holdingTimeCache = holdingTimeCache;
        this.vehicleStatusManager = vehicleStatusManager;
        this.holdingTimeGenerator = holdingTimeGenerator;
        this.travelTimes = travelTimes;
        this.tripDataHistoryCacheInterface = tripDataHistoryCacheInterface;
        this.stopArrivalDepartureCacheInterface = stopArrivalDepartureCacheInterface;
        this.dwellTimeModelCacheInterface = dwellTimeModelCacheInterface;
        this.dataDbLogger = dataDbLogger;
        this.dbConfig = dbConfig;
        this.arrivalsDeparturesProperties = arrivalsDeparturesProperties;
        this.coreProperties = coreProperties;
        this.predictionAccuracyModule = predictionAccuracyModule;
    }


    /**
     * Returns whether going from oldMatch to newMatch traverses so many stops during the elapsed
     * AVL time that it isn't reasonable to think that the vehicle did so. If too many stops would
     * be traversed then logs an error message indicating that this isn't reasonable. When true is
     * returned then shouldn't generate arrival/departure times. This situation can happen when a
     * vehicle does a short turn, there are problems with the AVL data such as the heading is not
     * accurate causing the vehicle to match to the wrong direction, or if there is a software
     * problem that causes an improper match.
     *
     * @param oldMatch For determining how many stops traversed. Can be null
     * @param newMatch
     * @param previousAvlReport For determining how long since last match. Can be null
     * @param avlReport
     * @return
     */
    private boolean tooManyStopsTraversed(
        SpatialMatch oldMatch, SpatialMatch newMatch, AvlReport previousAvlReport, AvlReport avlReport) {
        // If there is no old match then we are fine
        if (oldMatch == null) return false;

        // If there is no old AVL report then we are fine
        if (previousAvlReport == null) return false;

        // Determine how much time elapsed
        long avlTimeDeltaMsec = avlReport.getTime() - previousAvlReport.getTime();

        // Determine number of stops traversed
        Indices indices = oldMatch.getIndices();
        Indices newMatchIndices = newMatch.getIndices();
        int stopsTraversedCnt = 0;
        while (!indices.pastEndOfBlock(avlReport.getTime(), dbConfig) && indices.isEarlierStopPathThan(newMatchIndices)) {
            indices.incrementStopPath(avlReport.getTime(), dbConfig);
            ++stopsTraversedCnt;
        }

        // If traversing more than a stop every 15 seconds then there must be
        // a problem. Also use a minimum of 4 stops to make sure that don't get
        // problems due to problematic GPS data causing issues
        if (stopsTraversedCnt >= 4 && stopsTraversedCnt > avlTimeDeltaMsec / 15 * Time.MS_PER_SEC) {
            logger.error(
                    "vehicleId={} traversed {} stops in {} seconds "
                            + "which seems like too many stops for that amount of time. "
                            + "oldMatch={} , newMatch={}, previousAvlReport={}, "
                            + "avlReport={}",
                    avlReport.getVehicleId(),
                    stopsTraversedCnt,
                    avlTimeDeltaMsec / Time.MS_PER_SEC,
                    oldMatch,
                    newMatch,
                    previousAvlReport,
                    avlReport);
            return true;
        } else return false;
    }

    /**
     * Determines if need to determine arrival/departure times due to vehicle having traversed a
     * stop.
     *
     * @param oldMatch The old match for the vehicle. Should be null if not previous match
     * @param newMatch The new match for the vehicle.
     * @return
     */
    private boolean shouldProcessArrivedOrDepartedStops(SpatialMatch oldMatch, SpatialMatch newMatch) {
        // If there is no old match at all then we likely finally got a
        // AVL report after vehicle had left terminal. Still want to
        // determine arrival/departure times for the first stops of the
        // block assignment. And this makes sure don't get a NPE in the
        // next statements.
        if (oldMatch == null) return true;

        // If jumping too many stops then something is strange, such as
        // matching to a very different part of the assignment. Since don't
        // truly know what is going on it is best to not generate
        // arrivals/departures for between the matches.
        int stopsTraversed = SpatialMatch.numberStopsBetweenMatches(oldMatch, newMatch);
        if (stopsTraversed > arrivalsDeparturesProperties.getMaxStopsBetweenMatches()) {
            logger.error(
                    "Attempting to traverse {} stops between oldMatch "
                            + "and newMatch, which is more thanThere are more than "
                            + "MAX_STOPS_BETWEEN_MATCHES={}. Therefore not generating "
                            + "arrival/departure times. oldMatch={} newMatch={}",
                    stopsTraversed,
                    arrivalsDeparturesProperties.getMaxStopsBetweenMatches(),
                    oldMatch,
                    newMatch);
            return false;
        }

        // Determine if should generate arrivals/departures
        VehicleAtStopInfo oldStopInfo = oldMatch.getAtStop();
        VehicleAtStopInfo newStopInfo = newMatch.getAtStop();
        if (oldStopInfo != null && newStopInfo != null) {
            // Vehicle at stop for both old and new. Determine if they
            // are different stops. If different then return true.
            return oldStopInfo.getTripIndex() != newStopInfo.getTripIndex()
                    || oldStopInfo.getStopPathIndex() != newStopInfo.getStopPathIndex();
        } else if (oldStopInfo != null || newStopInfo != null) {
            // Just one (but not both) of the vehicle stop infos is null which
            // means they are different. Therefore must have arrived or departed
            // stops.
            return true;
        } else {
            // Stop infos for both old and new match are null.
            // See if matches indicate that now on a new path
            return oldMatch.getTripIndex() != newMatch.getTripIndex()
                    || oldMatch.getStopPathIndex() != newMatch.getStopPathIndex();
        }
    }

    /**
     * Writes out departure time to database
     *
     * @param vehicleStatus
     * @param departureTime
     * @param block
     * @param tripIndex
     * @param stopPathIndex
     */
    protected Departure createDepartureTime(
        VehicleStatus vehicleStatus, long departureTime, Block block, int tripIndex, int stopPathIndex) {
        // Store the departure in the database via the db logger

        Date freqStartDate = null;
        if (vehicleStatus.getTripStartTime(vehicleStatus.getTripCounter()) != null) {
            freqStartDate = new Date(vehicleStatus.getTripStartTime(vehicleStatus.getTripCounter()));
        }

        Departure departure = new Departure(
                dbConfig.getConfigRev(),
                vehicleStatus.getVehicleId(),
                new Date(departureTime),
                vehicleStatus.getAvlReport().getDate(),
                block,
                tripIndex,
                stopPathIndex,
                freqStartDate,
                dbConfig);
        updateCache(vehicleStatus, departure);
        logger.debug("Creating departure: {}", departure);
        return departure;
    }

    /**
     * Writes out arrival time to database. Also keeps track of the latest arrival time in
     * VehicleState so that can make sure that subsequent departures are after the last arrival
     * time.
     *
     * @param vehicleStatus
     * @param arrivalTime
     * @param block
     * @param tripIndex
     * @param stopPathIndex
     */
    protected Arrival createArrivalTime(
        VehicleStatus vehicleStatus, long arrivalTime, Block block, int tripIndex, int stopPathIndex) {
        // Store the arrival in the database via the db logger

        Date freqStartDate = null;
        if (vehicleStatus.getTripStartTime(vehicleStatus.getTripCounter()) != null) {
            freqStartDate = new Date(vehicleStatus.getTripStartTime(vehicleStatus.getTripCounter()));
        }

        Arrival arrival = new Arrival(
                dbConfig.getConfigRev(),
                vehicleStatus.getVehicleId(),
                new Date(arrivalTime),
                vehicleStatus.getAvlReport().getDate(),
                block,
                tripIndex,
                stopPathIndex,
                freqStartDate,
                dbConfig);

        updateCache(vehicleStatus, arrival);
        logger.debug("Creating arrival: {}", arrival);

        // Remember this arrival time so that can make sure that subsequent
        // departures are for after the arrival time.
        if (arrivalTime > vehicleStatus.getLastArrivalTime()) vehicleStatus.setLastArrivalTime(arrivalTime);

        return arrival;
    }

    private void updateCache(VehicleStatus vehicleStatus, ArrivalDeparture arrivalDeparture) {

        if (tripDataHistoryCacheInterface != null)
            tripDataHistoryCacheInterface.putArrivalDeparture(arrivalDeparture);

        if (stopArrivalDepartureCacheInterface != null) {
            stopArrivalDepartureCacheInterface.putArrivalDeparture(arrivalDeparture);
        }

        if (dwellTimeModelCacheInterface != null) {
            dwellTimeModelCacheInterface.addSample(arrivalDeparture);
        }

        if (scheduleBasedHistoricalAverageCache != null) {
            try {
                scheduleBasedHistoricalAverageCache.putArrivalDeparture(arrivalDeparture);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        if (frequencyBasedHistoricalAverageCache != null)
            try {
                frequencyBasedHistoricalAverageCache.putArrivalDeparture(arrivalDeparture);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        if (holdingTimeGenerator != null) {
            HoldingTime holdingTime;
            try {
                holdingTime = holdingTimeGenerator
                        .generateHoldingTime(vehicleStatus, new IpcArrivalDeparture(arrivalDeparture));
                if (holdingTime != null) {
                    holdingTimeCache.putHoldingTime(holdingTime);
                    vehicleStatus.setHoldingTime(holdingTime);
                }
                ArrayList<Long> N_List = new ArrayList<Long>();

                holdingTimeGenerator.handleDeparture(vehicleStatus, arrivalDeparture);

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        /*
        if(HoldingTimeGeneratorDefaultImpl.getOrderedListOfVehicles("66")!=null)
        	logger.info("ORDER:"+HoldingTimeGeneratorDefaultImpl.getOrderedListOfVehicles("66").toString());
        */
        /*
        if(HoldingTimeGeneratorFactory.getInstance()!=null)
        {
        	HoldingTimeCacheKey key=new HoldingTimeCacheKey(arrivalDeparture.getStopId(), arrivalDeparture.getVehicleId(), arrivalDeparture.getTripId());
        	if(arrivalDeparture.getVehicleId().equals("966"))
        	{
        		System.out.println("hello");
        	}



        	if(holdingTimeCache.getHoldingTime(key)!=null)
        	{
        		long sinceHoldingTimeGenerated=Math.abs(holdingTimeCache.getHoldingTime(key).getCreationTime().getTime()-arrivalDeparture.getAvlTime().getTime());

        		if((holdingTimeCache.getHoldingTime(key).isArrivalPredictionUsed()==false&&sinceHoldingTimeGenerated>1400000)||holdingTimeCache.getHoldingTime(key).isArrivalPredictionUsed()==true)
        		{
        			HoldingTime holdingTime = HoldingTimeGeneratorFactory.getInstance().generateHoldingTime(vehicleState, arrivalDeparture);
        			if(holdingTime!=null)
        			{
        				holdingTimeCache.putHoldingTime(holdingTime);
        				vehicleState.setHoldingTime(holdingTime);
        			}
        		}else
        		{
        			logger.debug("Don't generate holding time.");
        		}
        	}else
        	{
        		HoldingTime holdingTime = HoldingTimeGeneratorFactory.getInstance().generateHoldingTime(vehicleState, arrivalDeparture);
        		if(holdingTime!=null)
        		{
        			holdingTimeCache.putHoldingTime(holdingTime);
        			vehicleState.setHoldingTime(holdingTime);
        		}
        	}
        	HoldingTimeGeneratorFactory.getInstance().handleDeparture(vehicleState, arrivalDeparture);
        }
        */
    }

    /**
     * For making sure that the arrival/departure time is reasonably close to the AVL time.
     * Otherwise this indicates there was a problem determining the arrival/departure time.
     *
     * @param arrivalDeparture
     * @return true if arrival/departure time within 30 minutes of the AVL report time.
     */
    private boolean timeReasonable(ArrivalDeparture arrivalDeparture) {
        long delta = Math.abs(arrivalDeparture.getAvlTime().getTime() - arrivalDeparture.getDate().getTime());

        if (delta < arrivalsDeparturesProperties.getAllowableDifferenceBetweenAvlTimeSecs() * Time.MS_PER_SEC) {
            return true;
        }

        logger.error(
                "Arrival or departure time of {} is more than "
                        + "{} secs away from the AVL time of {}. Therefore not "
                        + "storing this time. {}",
                arrivalDeparture.getDate(),
                arrivalsDeparturesProperties.getAllowableDifferenceBetweenAvlTimeSecs(),
                arrivalDeparture.getAvlTime(),
                arrivalDeparture);
        return false;
    }

    /**
     * Stores the specified ArrivalDeparture object into the db and log to the ArrivalsDeparatures
     * log file that the object was created.
     *
     * <p>Also generates corresponding prediction accuracy information if a corresponding prediction
     * was found in memory.
     *
     * @param arrivalDeparture
     */
    protected void storeInDbAndLog(ArrivalDeparture arrivalDeparture) {
        // If arrival/departure time too far from the AVL time then something
        // must be wrong. For this situation don't store the arrival/departure
        // into db.
        if (!timeReasonable(arrivalDeparture)) return;

        // Don't want to record arrival/departure time for last stop of a no
        // schedule block/trip since the last stop is also the first stop of
        // a non-schedule trip. We don't duplicate entries.
        if (arrivalDeparture.getBlock().isNoSchedule()) {
            Trip trip = arrivalDeparture.getBlock().getTrip(arrivalDeparture.getTripIndex());
            // If last stop in trip then don't do anything here
            if (arrivalDeparture.getStopPathIndex() == trip.getNumberStopPaths() - 1) return;
        }

        // Queue to store object into db
        dataDbLogger.add(arrivalDeparture);

        /* add event to vehicle state. Will increment tripCounter if the last arrival in a trip */
        VehicleStatus vehicleStatus = vehicleStatusManager.getStatus(arrivalDeparture.getVehicleId());

        vehicleStatus.incrementTripCounter(arrivalDeparture, vehicleStatusManager);

        // Generate prediction accuracy info as appropriate
        predictionAccuracyModule.handleArrivalDeparture(dbConfig, dataDbLogger, arrivalDeparture);
    }

    /**
     * If vehicle departs terminal too early or too late then log an event so that the problem is
     * made more obvious.
     *
     * @param vehicleStatus
     * @param departure
     */
    private void logEventIfVehicleDepartedEarlyOrLate(VehicleStatus vehicleStatus, Departure departure) {
        // If departure not for terminal then can ignore
        if (departure.getStopPathIndex() != 0) return;

        // Determine schedule adherence. If no schedule adherence info available
        // then can ignore.
        TemporalDifference schAdh = departure.getScheduleAdherence();
        if (schAdh == null) return;

        // If vehicle left too early then record an event
        if (schAdh.isEarlierThan(coreProperties.getAllowableEarlyDepartureTimeForLoggingEvent())) {
            // Create description for VehicleEvent
            Stop stop = dbConfig.getStop(departure.getStopId());
            Route route = dbConfig.getRouteById(departure.getRouteId());
            String description = "Vehicle "
                    + departure.getVehicleId()
                    + " left stop "
                    + departure.getStopId()
                    + " \""
                    + stop.getName()
                    + "\" for route \""
                    + route.getName()
                    + "\" "
                    + schAdh
                    + ". Scheduled departure time was "
                    + Time.timeStr(departure.getScheduledTime());

            // Create, store in db, and log the VehicleEvent
            VehicleEvent vehicleEvent = new VehicleEvent(
                    vehicleStatus.getAvlReport(),
                    vehicleStatus.getMatch(),
                    VehicleEventType.LEFT_TERMINAL_EARLY,
                    description,
                    true, // predictable
                    false, // becameUnpredictable
                    null);// supervisor
            dataDbLogger.add(vehicleEvent);
        }

        // If vehicle left too late then record an event
        if (schAdh.isLaterThan(coreProperties.getAllowableLateDepartureTimeForLoggingEvent())) {
            // Create description for VehicleEvent
            Stop stop = dbConfig.getStop(departure.getStopId());
            Route route = dbConfig.getRouteById(departure.getRouteId());
            String description = "Vehicle "
                    + departure.getVehicleId()
                    + " left stop "
                    + departure.getStopId()
                    + " \""
                    + stop.getName()
                    + "\" for route \""
                    + route.getName()
                    + "\" "
                    + schAdh
                    + ". Scheduled departure time was "
                    + Time.timeStr(departure.getScheduledTime());

            // Create, store in db, and log the VehicleEvent
            VehicleEvent vehicleEvent = new VehicleEvent(
                    vehicleStatus.getAvlReport(),
                    vehicleStatus.getMatch(),
                    VehicleEventType.LEFT_TERMINAL_LATE,
                    description,
                    true, // predictable
                    false, // becameUnpredictable
                    null);// supervisor

            dataDbLogger.add(vehicleEvent);
        }
    }

    /**
     * For when there is a new match but not an old match. This means that cannot interpolate the
     * arrival/departure times. Instead need to look backwards and use travel and stop times to
     * determine the arrival/departure times.
     *
     * <p>Only does this if on the first trip of a block. The thought is that if vehicle becomes
     * predictable for subsequent trips that vehicle might have actually started service mid-block,
     * meaning that it didn't traverse the earlier stops and so shouldn't fake arrival/departure
     * times for the earlier stops since there is a good chance they never happened.
     *
     * @param vehicleStatus
     * @return List of ArrivalDepartures created
     */
    private void estimateArrivalsDeparturesWithoutPreviousMatch(VehicleStatus vehicleStatus) {
        // If vehicle got assigned to the same block as before then
        // there is likely a problem. In this case don't want to
        // estimate arrivals/departures because that would likely
        // create duplicates.
        if (vehicleStatus.vehicleNewlyAssignedToSameBlock()) {
            logger.info(
                    "For vehicleId={} There was no previous match so "
                            + "in theory could estimate arrivals/departures for the "
                            + "beginning of the assignment. But the vehicle is being "
                            + "reassigned to blockId={} which probably means that "
                            + "vehicle already had arrivals/departures for the stops. "
                            + "Therefore not estimating arrivals/departures for the "
                            + "early stops.",
                    vehicleStatus.getVehicleId(),
                    vehicleStatus.getBlock().getId());
            return;
        }

        // Couple of convenience variables
        SpatialMatch newMatch = vehicleStatus.getMatch();
        String vehicleId = vehicleStatus.getVehicleId();

        if (newMatch.getTripIndex() == 0
                && newMatch.getStopPathIndex() > 0
                && newMatch.getStopPathIndex() < arrivalsDeparturesProperties.getMaxStopsWhenNoPreviousMatch()) {
            // Couple more convenience variables
            Date avlReportTime = vehicleStatus.getAvlReport().getDate();
            Block block = newMatch.getBlock();
            final int tripIndex = 0;
            int stopPathIndex = 0;

            // Determine departure time for first stop of trip
            SpatialMatch beginningOfTrip = new SpatialMatch(0, block, tripIndex, 0, 0, 0.0, 0.0, coreProperties);
            long travelTimeFromFirstStopToMatch = travelTimes
                    .expectedTravelTimeBetweenMatches(vehicleId, avlReportTime, beginningOfTrip, newMatch);
            long departureTime = avlReportTime.getTime() - travelTimeFromFirstStopToMatch;

            // Create departure time for first stop of trip if it has left that
            // stop
            if (!newMatch.isAtStop(tripIndex, stopPathIndex)) {
                storeInDbAndLog(createDepartureTime(vehicleStatus, departureTime, block, tripIndex, stopPathIndex));
            }

            // Go through remaining intermediate stops to determine
            // arrival/departure times
            for (stopPathIndex = 1; stopPathIndex < newMatch.getStopPathIndex(); ++stopPathIndex) {
                // Create the arrival
                long arrivalTime = departureTime + block.getStopPathTravelTime(tripIndex, stopPathIndex);
                storeInDbAndLog(createArrivalTime(vehicleStatus, arrivalTime, block, tripIndex, stopPathIndex));

                // If the vehicle has left this stop then create the departure
                if (!newMatch.isAtStop(tripIndex, stopPathIndex)) {
                    int stopTime = block.getPathStopTime(tripIndex, stopPathIndex);
                    departureTime = arrivalTime + stopTime;
                    storeInDbAndLog(createDepartureTime(vehicleStatus, departureTime, block, tripIndex, stopPathIndex));
                }
            }

            // Need to add final arrival time if newMatch is at the
            // stop for the match
            if (newMatch.isAtStop(tripIndex, newMatch.getStopPathIndex())) {
                storeInDbAndLog(createArrivalTime(
                    vehicleStatus, avlReportTime.getTime(), block, tripIndex, newMatch.getStopPathIndex()));
            }
        } else {
            logger.debug(
                    "For vehicleId={} no old match but the new "
                            + "match is too far along so not determining "
                            + "arrival/departure times without previous match.",
                    vehicleId);
        }
    }

    /**
     * Makes sure that the departure time is after the arrival time. Also handles the situation
     * where couldn't store the previous arrival time because wasn't certain about it because it was
     * determined to be after the associated AVL report.
     *
     * @param departureTime
     * @param departureTimeBasedOnNewMatch
     * @param vehicleStatus
     * @return
     */
    private long adjustDepartureSoAfterArrival(
            long departureTime, long departureTimeBasedOnNewMatch, VehicleStatus vehicleStatus) {
        String vehicleId = vehicleStatus.getVehicleId();
        AvlReport avlReport = vehicleStatus.getAvlReport();
        AvlReport previousAvlReport = vehicleStatus.getPreviousAvlReportFromSuccessfulMatch();

        // Make sure departure time is after the previous arrival time since
        // don't want arrival/departure times to ever go backwards. That of
        // course looks really bad.
        Arrival arrivalToStoreInDb = vehicleStatus.getArrivalToStoreToDb();
        if (arrivalToStoreInDb != null) {
            // If the arrival time is a problem then adjust both the arrival
            // time and the departure time so that they are as accurate as
            // possible and that the arrival time comes before the departure
            // time.
            if (arrivalToStoreInDb.getTime() >= departureTime) {
                long originalTimeBetweenOldAvlAndArrival = arrivalToStoreInDb.getTime() - previousAvlReport.getTime();
                // Note: don't want to subtract out departure time because
                // it could be based on the old AVL report. Since trying to
                // determine expected time for departure using the old
                // AVL report for the arrival and the new AVL report for the
                // the departure need to use departureTimeBasedOnNewMatch.
                long originalTimeBetweenDepartureAndAvl = avlReport.getTime() - departureTimeBasedOnNewMatch;
                long timeBetweenAvlReports = avlReport.getTime() - previousAvlReport.getTime();
                double ratio = (double) timeBetweenAvlReports
                        / (originalTimeBetweenOldAvlAndArrival + originalTimeBetweenDepartureAndAvl);
                long newArrivalTime =
                        previousAvlReport.getTime() + Math.round(ratio * originalTimeBetweenOldAvlAndArrival);
                long newDepartureTime = newArrivalTime + 1;

                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "vehicleId={} determined departure time was "
                                    + "{} which is less than or equal to the previous "
                                    + "arrival time of {}. Therefore the arrival time "
                                    + "adjusted to {} and departure adjusted to {}.",
                            vehicleId,
                            Time.dateTimeStrMsec(departureTime),
                            Time.dateTimeStrMsec(arrivalToStoreInDb.getTime()),
                            Time.dateTimeStrMsec(newArrivalTime),
                            Time.dateTimeStrMsec(newDepartureTime));
                }
                departureTime = newDepartureTime;
                arrivalToStoreInDb = arrivalToStoreInDb.withUpdatedTime(new Date(newArrivalTime), dbConfig);
            }

            // Now that have the corrected arrival time store it in db
            // and reset vehicleState to indicate that have dealt with it.
            storeInDbAndLog(arrivalToStoreInDb);
            vehicleStatus.setArrivalToStoreToDb(null);
        } else {
            // Even though the last arrival time wasn't for sometime in
            // the future of the AVL time could still have an issue.
            // Make sure that departure time is greater than the previous
            // arrival time no matter how it was created. This could happen
            // if travel times indicate that the vehicle departed a long time
            // ago.
            long lastArrivalTime = vehicleStatus.getLastArrivalTime();
            if (departureTime <= lastArrivalTime) {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "vehicleId={} the determined departure was "
                                    + "{} which is before the previous arrival time {}. "
                                    + "Therefore adjusting the departure time to {}",
                            vehicleId,
                            Time.dateTimeStrMsec(departureTime),
                            Time.dateTimeStrMsec(lastArrivalTime),
                            Time.dateTimeStrMsec(lastArrivalTime + 1));
                }
                departureTime = lastArrivalTime + 1;
            }
        }

        // If adjusting the departure time makes it after the AVL report
        // then we have a problem. Can't do anything about it so just log
        // the problem.
        if (departureTime >= avlReport.getTime()) {
            if (logger.isDebugEnabled()) {
                logger.error(
                        "For vehicleId={} after adjusting the departure "
                                + "time to be after the arrival time got a departure "
                                + "time of {} which is after the AVL time of {}. This "
                                + "is a problem because the match won't be between the "
                                + "departure and arrival time even though it should be.",
                        vehicleId,
                        Time.dateTimeStrMsec(departureTime),
                        Time.dateTimeStrMsec(avlReport.getTime()));
            }
        }

        // Return the possibly adjusted departure time
        return departureTime;
    }

    /**
     * Handles the case where the old match indicates that vehicle has departed a stop. Determines
     * the appropriate departure time.
     *
     * @param vehicleStatus For obtaining match and AVL info
     * @return The time to be used as the beginTime for determining arrivals/departures for
     *     intermediate stops. Will be the time of the new AVL report if vehicle is not at a stop.
     *     If it is at a stop then it will be the expected departure time at that stop.
     */
    private long handleVehicleDepartingStop(VehicleStatus vehicleStatus) {
        String vehicleId = vehicleStatus.getVehicleId();

        // If vehicle wasn't departing a stop then simply return the
        // previous AVL time as the beginTime.
        SpatialMatch oldMatch = vehicleStatus.getPreviousMatch();
        VehicleAtStopInfo oldVehicleAtStopInfo = oldMatch.getAtStop();
        AvlReport previousAvlReport = vehicleStatus.getPreviousAvlReportFromSuccessfulMatch();
        if (oldVehicleAtStopInfo == null) return previousAvlReport.getTime();

        // Vehicle departed previous stop...
        logger.debug(
                "vehicleId={} was at stop {}  previous AVL report " + "and departed so determining departure time",
                vehicleId,
                oldVehicleAtStopInfo);

        // Use match right at the departed stop. This way we are including the
        // time it takes to get from the actual stop to the new match.
        SpatialMatch matchJustAfterStop = oldMatch.getMatchAdjustedToBeginningOfPath(dbConfig);

        // Determine departure info for the old stop by using the current
        // AVL report and subtracting the expected travel time to get from
        // there to the new match.
        SpatialMatch newMatch = vehicleStatus.getMatch();
        int travelTimeToNewMatchMsec = travelTimes
                .expectedTravelTimeBetweenMatches(vehicleId, previousAvlReport.getDate(), matchJustAfterStop, newMatch);
        AvlReport avlReport = vehicleStatus.getAvlReport();
        long departureTimeBasedOnNewMatch = avlReport.getTime() - travelTimeToNewMatchMsec;

        // Need to also look at departure time for the old stop by using the
        // previous AVL report and subtracting the expected travel time to get
        // from there. This will prevent us from using
        // departureTimeBasedOnNewMatch if that time is too early due to
        // expected travel times being too long.
        long departureTimeBasedOnOldMatch;
        if (matchJustAfterStop.lessThanOrEqualTo(oldMatch)) {
            // The stop is before the oldMatch so need to subtract travel time
            // from the stop to the oldMatch from the previous AVL report time.
            int travelTimeFromStopToOldMatchMsec = travelTimes
                    .expectedTravelTimeBetweenMatches(
                            vehicleId, previousAvlReport.getDate(), matchJustAfterStop, oldMatch);
            departureTimeBasedOnOldMatch = previousAvlReport.getTime() - travelTimeFromStopToOldMatchMsec;
        } else {
            // The oldMatch is before the stop so add the travel time from the
            // oldMatch to the stop to the previous AVL report time.
            SpatialMatch matchJustBeforeStop = oldMatch.getMatchAdjustedToEndOfPath();
            int travelTimeFromOldMatchToStopMsec = travelTimes
                    .expectedTravelTimeBetweenMatches(
                            vehicleId, previousAvlReport.getDate(), oldMatch, matchJustBeforeStop);
            departureTimeBasedOnOldMatch = previousAvlReport.getTime() + travelTimeFromOldMatchToStopMsec;
        }

        // Determine actual departure time to use. If the old match departure
        // time is greater than the new match time then we know that the
        // vehicle was still at the stop at the old match departure time.
        // Using the new match departure time would be too early in this
        // case. So for this case use the departure time based on the old
        // match.
        long departureTime = departureTimeBasedOnNewMatch;
        if (departureTimeBasedOnOldMatch > departureTimeBasedOnNewMatch) {
            // Use departure time based on old match since we definitely
            // know that the vehicle was still at the stop at that time.
            departureTime = departureTimeBasedOnOldMatch;

            // Log what is going on
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "For vehicleId={} using departure time {} based "
                                + "on old match because it is greater than the "
                                + "earlier value "
                                + "based on the new match of {}",
                        vehicleId,
                        Time.dateTimeStrMsec(departureTime),
                        Time.dateTimeStrMsec(departureTimeBasedOnNewMatch));
            }
        }

        // Make sure the determined departure time is less than new AVL time.
        // This is important because the vehicle has gone beyond the stop and
        // will be generating a match. Since the vehicle was determined to
        // have left the stop the departure time must be before the AVL time.
        // This check makes sure that matches and arrivals/departures are in
        // the proper order for when determining historic travel times.
        if (departureTime >= avlReport.getTime()) {
            logger.debug(
                    "For vehicleId={} departure time determined to be "
                            + "{} but that is greater or equal to the AVL time "
                            + "of {}. Therefore setting departure time to {}.",
                    vehicleId,
                    Time.dateTimeStrMsec(departureTime),
                    Time.dateTimeStrMsec(avlReport.getTime()),
                    Time.dateTimeStrMsec(avlReport.getTime() - 1));
            departureTime = avlReport.getTime() - 1;
        }

        // Make sure departure time is after arrival
        departureTime = adjustDepartureSoAfterArrival(departureTime, departureTimeBasedOnNewMatch, vehicleStatus);

        // Create and write out the departure time to db
        Departure departure = createDepartureTime(
            vehicleStatus,
                departureTime,
                oldVehicleAtStopInfo.getBlock(),
                oldVehicleAtStopInfo.getTripIndex(),
                oldVehicleAtStopInfo.getStopPathIndex());
        storeInDbAndLog(departure);

        // Log event if vehicle left a terminal too early or too late
        logEventIfVehicleDepartedEarlyOrLate(vehicleStatus, departure);

        // The new beginTime to be used to determine arrival/departure
        // times at intermediate stops
        return departureTime;
    }

    /**
     * Handles the case where the new match indicates that vehicle has arrived at a stop. Determines
     * the appropriate arrival time.
     *
     * @param vehicleStatus For obtaining match and AVL info
     * @param beginTime The time of the previous AVL report or the departure time if vehicle
     *     previously was at a stop.
     * @return The time to be used as the endTime for determining arrivals/departures for
     *     intermediate stops. Will be the time of the new AVL report if vehicle is not at a stop.
     *     If it is at a stop then it will be the expected arrival time at that stop.
     */
    private long handleVehicleArrivingAtStop(VehicleStatus vehicleStatus, long beginTime) {
        String vehicleId = vehicleStatus.getVehicleId();

        // If vehicle hasn't arrived at a stop then simply return the
        // AVL time as the endTime.
        SpatialMatch newMatch = vehicleStatus.getMatch();
        VehicleAtStopInfo newVehicleAtStopInfo = newMatch.getAtStop();
        AvlReport avlReport = vehicleStatus.getAvlReport();
        if (newVehicleAtStopInfo == null) return avlReport.getTime();

        // Vehicle has arrived at a stop...
        logger.debug(
                "vehicleId={} arrived at stop {} with new AVL " + "report so determining arrival time",
                vehicleId,
                newVehicleAtStopInfo);

        // Use match right at the stop. This way we are including the
        // time it takes to get from the new match to the actual
        // stop and not just to some distance before the stop.
        SpatialMatch matchJustBeforeStop = newMatch.getMatchAdjustedToEndOfPath();

        // Determine arrival info for the new stop based on the
        // old AVL report. This will give us the proper time if
        // the vehicle already arrived before the current AVL
        // report
        SpatialMatch oldMatch = vehicleStatus.getPreviousMatch();
        int travelTimeFromOldMatchMsec = travelTimes
                .expectedTravelTimeBetweenMatches(vehicleId, avlReport.getDate(), oldMatch, matchJustBeforeStop);
        // At first it appears that should use the time of the previous AVL
        // report plus the travel time. But since vehicle might have just
        // departed the previous stop should use that departure time instead.
        // By using beginTime we are using the correct value.
        long arrivalTimeBasedOnOldMatch = beginTime + travelTimeFromOldMatchMsec;

        // Need to also look at arrival time based on the new match. This
        // will prevent us from using arrivalTimeBasedOnOldMatch if that
        // time is in the future due to the expected travel times incorrectly
        // being too long.
        long arrivalTimeBasedOnNewMatch;
        if (newMatch.lessThanOrEqualTo(matchJustBeforeStop)) {
            // The new match is before the stop so add the travel time
            // from the match to the stop to the AVL time to get the
            // arrivalTimeBasedOnNewMatch.
            int travelTimeFromNewMatchToStopMsec = travelTimes
                    .expectedTravelTimeBetweenMatches(vehicleId, avlReport.getDate(), newMatch, matchJustBeforeStop);
            arrivalTimeBasedOnNewMatch = avlReport.getTime() + travelTimeFromNewMatchToStopMsec;
        } else {
            // The new match is after the stop so subtract the travel time
            // from the stop to the match from the AVL time to get the
            // arrivalTimeBasedOnNewMatch.
            SpatialMatch matchJustAfterStop = newMatch.getMatchAdjustedToBeginningOfPath(dbConfig);

            int travelTimeFromStoptoNewMatchMsec = travelTimes
                    .expectedTravelTimeBetweenMatches(vehicleId, avlReport.getDate(), matchJustAfterStop, newMatch);
            arrivalTimeBasedOnNewMatch = avlReport.getTime() - travelTimeFromStoptoNewMatchMsec;
        }

        // Determine which arrival time to use. If the one based on the old
        // match is greater than the one based on the new match it means that
        // the vehicle traveled faster than expected. This is pretty common
        // since the travel times can be based on the schedule, which is often
        // not very accurate. For this case need to use the arrival time
        // based on the new match since we know that the vehicle has arrived
        // at the stop by that time.
        long arrivalTime = arrivalTimeBasedOnOldMatch;
        if (arrivalTimeBasedOnNewMatch < arrivalTimeBasedOnOldMatch) {
            // Use arrival time based on new match since we definitely know
            // the vehicle has arrived at this time.
            arrivalTime = arrivalTimeBasedOnNewMatch;

            // Log what is going on
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "For vehicleId={} using arrival time {} based "
                                + "on new match because it is less than the later value "
                                + "based on the old match of {}",
                        vehicleId,
                        Time.dateTimeStrMsec(arrivalTime),
                        Time.dateTimeStrMsec(arrivalTimeBasedOnOldMatch));
            }
        }

        // Make sure the determined arrival time is greater than old AVL time.
        // This check makes sure that matches and arrivals/departures are in
        // the proper order for when determining historic travel times.
        AvlReport previousAvlReport = vehicleStatus.getPreviousAvlReportFromSuccessfulMatch();
        if (arrivalTime <= previousAvlReport.getTime()) {
            logger.debug(
                    "For vehicleId={} arrival time determined to be "
                            + "{} but that is less than or equal to the previous AVL "
                            + "time of {}. Therefore setting arrival time to {}.",
                    vehicleId,
                    Time.dateTimeStrMsec(arrivalTime),
                    Time.dateTimeStrMsec(previousAvlReport.getTime()),
                    Time.dateTimeStrMsec(previousAvlReport.getTime() + 1));
            arrivalTime = previousAvlReport.getTime() + 1;
        }

        // Create the arrival time
        Arrival arrival = createArrivalTime(
            vehicleStatus,
                arrivalTime,
                newVehicleAtStopInfo.getBlock(),
                newVehicleAtStopInfo.getTripIndex(),
                newVehicleAtStopInfo.getStopPathIndex());

        // If the arrival time is into the future then we don't want to store
        // it right now because it might be so in the future that it could be
        // after the next AVL report, which of course hasn't happened yet.
        // This would be a problem because then the store arrivals/departures
        // and matches could be out of sequence which would screw up all
        // the systems that use that data. But if it is the last stop of the
        // trip then should store it now because might not get another match
        // for this trip and don't want to never store the arrival.
        if (arrival.getTime() > avlReport.getTime()
                && newVehicleAtStopInfo.getStopPathIndex() != newMatch.getTrip().getNumberStopPaths() - 1) {
            // Record the arrival to store into the db next time get a
            // departure so that can make sure that arrival time is
            // appropriately before the departure time.
            vehicleStatus.setArrivalToStoreToDb(arrival);
        } else {
            // Not the complicated situation so store the arrival into db
            vehicleStatus.setArrivalToStoreToDb(null);
            storeInDbAndLog(arrival);
        }

        // The new endTime to be used to determine arrival/departure
        // times at intermediate stops
        return arrivalTime;
    }

    /**
     * Determines number of travel times and wait times between oldMatch and newMatch that have zero
     * travel or stop time, meaning that the arrival/departure times will need to be adjusted by
     * 1msec to make sure that each time is unique for a vehicle.
     *
     * @param oldMatch
     * @param newMatch
     * @return Number of zero travel or stop times
     */
    private int numberOfZeroTravelOrStopTimes(SpatialMatch oldMatch, SpatialMatch newMatch) {
        int counter = 0;
        Indices indices = oldMatch.getIndices();
        Indices newIndices = newMatch.getIndices();
        while (indices.isEarlierStopPathThan(newIndices)) {
            if (indices.getTravelTimeForPath() == 0) ++counter;
            if (indices.getStopTimeForPath() == 0) ++counter;
            indices.incrementStopPath(dbConfig);
        }

        return counter;
    }

    /**
     * Determine arrival/departure info for in the between stops between the previous and the
     * current match.
     *
     * @param vehicleStatus For obtaining match and AVL info
     * @param beginTime Time of previous AVL report or the departure time if vehicle was at a stop
     * @param endTime Time of the new AVL report or the arrival time if vehicle arrived at a stop
     */
    private void handleIntermediateStops(VehicleStatus vehicleStatus, long beginTime, long endTime) {
        // Need to make sure that the arrival/departure times created for
        // intermediate stops do not have the same exact time as the
        // departure of the previous stop or the arrival of the new
        // stop. Otherwise this could happen if have travel and/or wait
        // times of zero. The reason this is important is so that each
        // arrival/departure for a vehicle have a different time and
        // ordered correctly time wise so that when one looks at the
        // arrival/departure times for a vehicle the order is correct.
        // Otherwise the times being listed out of order could cause one
        // to lose trust in the values.
        ++beginTime;
        --endTime;

        // Convenience variables
        String vehicleId = vehicleStatus.getVehicleId();
        SpatialMatch oldMatch = vehicleStatus.getPreviousMatch();
        SpatialMatch newMatch = vehicleStatus.getMatch();
        Date previousAvlDate =
                vehicleStatus.getPreviousAvlReportFromSuccessfulMatch().getDate();
        Date avlDate = vehicleStatus.getAvlReport().getDate();

        int numZeroTimes = numberOfZeroTravelOrStopTimes(oldMatch, newMatch);

        // Determine how fast vehicle was traveling compared to what is
        // expected. Then can use proportional travel and stop times to
        // determine the arrival and departure times. Note that this part of
        // the code doesn't need to be very efficient because usually will get
        // frequent enough AVL reports such that there will be at most only
        // a single stop that is crossed. Therefore it is OK to determine
        // travel times for same segments over and over again.
        int totalExpectedTravelTimeMsec = travelTimes
                .expectedTravelTimeBetweenMatches(vehicleId, previousAvlDate, oldMatch, newMatch);
        long elapsedAvlTime = endTime - beginTime - numZeroTimes;

        // speedRatio is how much time vehicle took to travel compared to the
        // expected travel time. A value greater than 1.0 means that vehicle
        // is taking longer than expected and the expected travel times should
        // therefore be increased accordingly. There are situations where
        // totalExpectedTravelTimeMsec can be zero or really small, such as
        // when using schedule based travel times and the schedule doesn't
        // provide enough time to even account for the 10 or seconds expected
        // for wait time stops. Need to make sure that don't divide by zero
        // for this situation, where expected travel time is 5 msec or less,
        // use a speedRatio of 1.0.
        double speedRatio;
        if (totalExpectedTravelTimeMsec > 5) speedRatio = (double) elapsedAvlTime / totalExpectedTravelTimeMsec;
        else speedRatio = 1.0;

        // To determine which path use the stopInfo if available since that
        // way won't use the wrong path index if the vehicle is matching to
        // just beyond the stop.
        VehicleAtStopInfo oldVehicleAtStopInfo = oldMatch.getAtStop();
        Indices indices = oldVehicleAtStopInfo != null
                ? oldVehicleAtStopInfo.clone().incrementStopPath(endTime, dbConfig)
                : oldMatch.getIndices();

        VehicleAtStopInfo newVehicleAtStopInfo = newMatch.getAtStop();
        Indices endIndices = newVehicleAtStopInfo != null ? newVehicleAtStopInfo.clone() : newMatch.getIndices();

        // Determine time to first stop
        SpatialMatch matchAtNextStop = oldMatch.getMatchAtJustBeforeNextStop(dbConfig, coreProperties);
        long travelTimeToFirstStop = travelTimes
                .expectedTravelTimeBetweenMatches(vehicleId, avlDate, oldMatch, matchAtNextStop);
        double timeWithoutSpeedRatio = travelTimeToFirstStop;
        long arrivalTime = beginTime + Math.round(timeWithoutSpeedRatio * speedRatio);

        // Go through each stop between the old match and the new match and
        // determine the arrival and departure times...
        logger.debug(
                "For vehicleId={} determining if it traversed stops " + "in between the new and the old AVL report...",
                vehicleId);
        Block block = indices.getBlock();
        while (indices.isEarlierStopPathThan(endIndices)) {
            // Determine arrival time for current stop
            ArrivalDeparture arrival = createArrivalTime(
                vehicleStatus, arrivalTime, newMatch.getBlock(), indices.getTripIndex(), indices.getStopPathIndex());
            storeInDbAndLog(arrival);

            // Determine departure time for current stop
            double stopTime = block.getPathStopTime(indices.getTripIndex(), indices.getStopPathIndex());
            // Make sure that the departure time is different by at least
            // 1 msec so that times will be ordered properly when querying
            // the db.
            if (stopTime * speedRatio < 1.0) stopTime = 1.0 / speedRatio;
            timeWithoutSpeedRatio += stopTime;
            long departureTime = beginTime + Math.round(timeWithoutSpeedRatio * speedRatio);
            ArrivalDeparture departure = createDepartureTime(
                vehicleStatus,
                    departureTime,
                    newMatch.getBlock(),
                    indices.getTripIndex(),
                    indices.getStopPathIndex());
            storeInDbAndLog(departure);

            // Determine travel time to next time for next time through
            // the while loop
            indices.incrementStopPath(dbConfig);
            double pathTravelTime = block.getStopPathTravelTime(indices.getTripIndex(), indices.getStopPathIndex());
            if (pathTravelTime * speedRatio < 1.0) {
                pathTravelTime = 1.0 / speedRatio;
            }
            timeWithoutSpeedRatio += pathTravelTime;
            arrivalTime = beginTime + Math.round(timeWithoutSpeedRatio * speedRatio);
        }

        logger.debug(
                "For vehicleId={} done determining if it traversed "
                        + "stops in between the new and the old AVL report.",
                vehicleId);
    }

    /**
     * Processes updated vehicleState to generate associated arrival and departure times. Looks at
     * both the previous match and the current match to determine which stops need to generate times
     * for. Stores the resulting arrival/departure times into the database.
     *
     * @param vehicleStatus
     * @return List of generated ArrivalDeparture times
     */
    @Override
    public void generate(VehicleStatus vehicleStatus) {
        // Make sure vehicle state is OK
        if (!vehicleStatus.isPredictable()) {
            logger.error(
                    "Vehicle was not predictable when trying to process " + "arrival/departure times. {}",
                vehicleStatus);
            // Return empty arrivalDepartures list
            return;
        }
        SpatialMatch newMatch = vehicleStatus.getMatch();
        if (newMatch == null) {
            logger.error(
                    "Vehicle was not matched when trying to process " + "arrival/departure times. {}", vehicleStatus);
            // Return empty arrivalDepartures list
            return;
        }

        // If no old match then can determine the stops traversed between the
        // old match and the new one. But this will frequently happen because
        // sometimes won't get matches until vehicle has gone past the initial
        // stop of the block due to not getting assignment right away or some
        // kind of AVL issue. For this situation still want to estimate the
        // arrival/departure times for the previous stops.
        SpatialMatch oldMatch = vehicleStatus.getPreviousMatch();
        if (oldMatch == null) {
            logger.debug(
                    "For vehicleId={} there was no previous match "
                            + "so seeing if can generate arrivals/departures for "
                            + "beginning of block",
                    vehicleStatus.getVehicleId());
            // Don't have an oldMatch, but see if can estimate times anyways
            estimateArrivalsDeparturesWithoutPreviousMatch(vehicleStatus);
            return;
        }

        // If either the old or the new match were for layovers but where
        // the distance to the match is large, then shouldn't determine
        // arrival/departure times because the vehicles isn't or wasn't
        // actually at the layover. This can happen because sometimes
        // can jump the match ahead to the layover even though the
        // vehicle isn't actually there. Can't just use
        // CoreConfig.getMaxDistanceFromSegment() since often for agencies
        // like mbta the stops are not on the path which means that a layover
        // match is likely to be greater than getMaxDistanceFromSegment() but
        // still want to record the departure time.
        boolean oldMatchIsProblematic =
                oldMatch.isLayover() && (oldMatch.getDistanceToSegment() > coreProperties.getLayoverDistance());
        boolean newMatchIsProblematic =
                newMatch.isLayover() && (newMatch.getDistanceToSegment() > coreProperties.getLayoverDistance());

        if (oldMatchIsProblematic || newMatchIsProblematic) {
            logger.warn(
                    "For vehicleId={} the old or the new match had a match distance greater than allowed. " +
                    "Therefore not generating arrival/departure times. " +
                    "Max allowed layoverDistance={}. oldMatch={} newMatch={}",
                    vehicleStatus.getVehicleId(),
                    coreProperties.getLayoverDistance(),
                    oldMatch,
                    newMatch);
            return;
        }

        // If too many stops were traversed given the AVL time then there must
        // be something wrong so return
        AvlReport previousAvlReport = vehicleStatus.getPreviousAvlReportFromSuccessfulMatch();
        AvlReport avlReport = vehicleStatus.getAvlReport();

        if (tooManyStopsTraversed(oldMatch, newMatch, previousAvlReport, avlReport))
            return;

        // If no stops were traversed simply return
        if (!shouldProcessArrivedOrDepartedStops(oldMatch, newMatch))
            return;

        // Process the arrival/departure times since traversed at least one stop
        logger.debug(
                "vehicleId={} traversed at least one stop so "
                        + "determining arrival/departure times. oldMatch={} newMatch={}",
                vehicleStatus.getVehicleId(),
                oldMatch,
                newMatch);

        // If vehicle was at a stop with the old match and has now departed
        // then determine the departure time. Update the beginTime
        // accordingly since it should be the departure time instead of
        // the time of previous AVL report.
        long beginTime = handleVehicleDepartingStop(vehicleStatus);

        // If vehicle arrived at a stop then determine the arrival
        // time. Update the endTime accordingly since should use the time
        // that vehicle actually arrived instead of the AVL time.
        long endTime = handleVehicleArrivingAtStop(vehicleStatus, beginTime);

        // Determine arrival/departure info for in between stops. This needs to
        // be called after handleVehicleArrivingAtStop() because need endTime
        // from that method.
        handleIntermediateStops(vehicleStatus, beginTime, endTime);
    }
}
