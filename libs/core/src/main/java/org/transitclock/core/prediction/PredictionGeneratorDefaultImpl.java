/* (C)2023 */
package org.transitclock.core.prediction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.transitclock.core.Indices;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.TravelTimes;
import org.transitclock.core.VehicleStatus;
import org.transitclock.core.avl.RealTimeSchedAdhProcessor;
import org.transitclock.core.avl.space.SpatialMatch;
import org.transitclock.core.avl.time.TemporalMatch;
import org.transitclock.core.dataCache.HoldingTimeCache;
import org.transitclock.core.dataCache.StopArrivalDepartureCacheInterface;
import org.transitclock.core.dataCache.StopPathPredictionCache;
import org.transitclock.core.dataCache.TripDataHistoryCacheInterface;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.core.holdingmethod.HoldingTimeGenerator;
import org.transitclock.core.prediction.bias.BiasAdjuster;
import org.transitclock.core.prediction.datafilter.TravelTimeDataFilter;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.domain.structs.HoldingTime;
import org.transitclock.domain.structs.PredictionForStopPath;
import org.transitclock.domain.structs.StopPath;
import org.transitclock.domain.structs.Trip;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.CoreProperties;
import org.transitclock.properties.PredictionProperties;
import org.transitclock.service.dto.IpcPrediction;
import org.transitclock.service.dto.IpcPrediction.ArrivalOrDeparture;
import org.transitclock.utils.Geo;
import org.transitclock.utils.SystemTime;
import org.transitclock.utils.Time;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * When a new match based on AVL data is made for a vehicle the methods in this class are used to
 * generate the corresponding predictions.
 *
 * <p>Layovers are the most complicated part. At a layover a vehicle is expected to leave at the
 * scheduled departure time. But if a vehicle is late then it will stop just for the max of the stop
 * time or the break time.
 *
 * <p>The stop time should be based on historic AVL data but I'm not sure how that can be determined
 * for a layover. Perhaps just need to use a default time and not update it via historic AVL data?
 *
 * <p>The break time is a value per stop, though it will default to a system wide setting since most
 * of the time won't have specific break time data per stop.
 *
 * <p>Also need to take into account that vehicles frequently don't leave right at the scheduled
 * departure time. The AVL data can be used to determine when they really depart. This could be
 * stored as the stop time for the stop. This overloads the meaning of stop time because then it is
 * used both for determining 1) how late relative to the schedule time a vehicle departs a layover;
 * and 2) how long a vehicle will stop for if vehicle arrives late and the stop time is greater than
 * the break time.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class PredictionGeneratorDefaultImpl extends AbstractPredictionGenerator implements PredictionComponentElementsGenerator {
    protected final HoldingTimeCache holdingTimeCache;
    protected final StopPathPredictionCache stopPathPredictionCache;
    protected final TravelTimes travelTimes;
    protected final HoldingTimeGenerator holdingTimeGenerator;
    protected final VehicleStatusManager vehicleStatusManager;
    protected final RealTimeSchedAdhProcessor realTimeSchedAdhProcessor;
    protected final BiasAdjuster biasAdjuster;

    @Autowired
    protected CoreProperties coreProperties;

    public PredictionGeneratorDefaultImpl(StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface,
                                          TripDataHistoryCacheInterface tripDataHistoryCacheInterface,
                                          DbConfig dbConfig,
                                          DataDbLogger dataDbLogger,
                                          TravelTimeDataFilter travelTimeDataFilter,
                                          PredictionProperties properties,
                                          HoldingTimeCache holdingTimeCache,
                                          StopPathPredictionCache stopPathPredictionCache,
                                          TravelTimes travelTimes,
                                          HoldingTimeGenerator holdingTimeGenerator,
                                          VehicleStatusManager vehicleStatusManager,
                                          RealTimeSchedAdhProcessor realTimeSchedAdhProcessor,
                                          BiasAdjuster biasAdjuster) {

        super(stopArrivalDepartureCacheInterface, tripDataHistoryCacheInterface, dbConfig, dataDbLogger, travelTimeDataFilter, properties);
        this.holdingTimeCache = holdingTimeCache;
        this.stopPathPredictionCache = stopPathPredictionCache;
        this.travelTimes = travelTimes;
        this.holdingTimeGenerator = holdingTimeGenerator;
        this.vehicleStatusManager = vehicleStatusManager;
        this.realTimeSchedAdhProcessor = realTimeSchedAdhProcessor;
        this.biasAdjuster = biasAdjuster;
    }

    /**
     * Generates prediction for the stop specified by the indices parameter. It will be an arrival
     * prediction if at the end of the trip or the useArrivalTimes parameter is set to true, and it
     * is not at a waitStop (since at waitStop always want a departure prediction. For departures
     * the prediction time will be the sum of the parameters predictionTime and timeStoppedMsec.
     *
     * @param avlReport So can get information such as vehicleId and AVL time
     * @param indices Describes the stop that should generate prediction for
     * @param predictionTime The expected arrival time at the stop.
     * @param useArrivalTimes For when not at end of trip or at a waitStop we have the choice of
     *     either generating arrival or departure times. For such stops this parameter whether to
     *     generate an arrival or a departure time for such a stop.
     * @param affectedByWaitStop to indicate if prediction is not as accurate because it depends on
     *     driver leaving waitStop according to schedule.
     * @param isDelayed The vehicle has not been making forward progress
     * @param lateSoMarkAsUncertain Indicates that vehicle is late and now generating predictions
     *     for a subsequent trip. Use to indicate that the predictions are less certain.
     * @return The generated Prediction
     */
    protected IpcPrediction generatePredictionForStop(
            AvlReport avlReport,
            Indices indices,
            long predictionTime,
            boolean useArrivalTimes,
            boolean affectedByWaitStop,
            boolean isDelayed,
            boolean lateSoMarkAsUncertain,
            int tripCounter,
            Integer scheduleDeviation) {

        // Determine additional parameters for the prediction to be generated
        StopPath path = indices.getStopPath();
        String stopId = path.getStopId();
        int gtfsStopSeq = path.getGtfsStopSeq();

        predictionTime = avlReport.getTime() + biasAdjuster.adjustPrediction(predictionTime - avlReport.getTime());

        Trip trip = indices.getTrip();

        long freqStartTime = -1;
        VehicleStatus vehicleStatus = vehicleStatusManager.getStatus(avlReport.getVehicleId());
        if (trip.isNoSchedule()) {
            if (vehicleStatus.getTripStartTime(tripCounter) != null) {
                freqStartTime = vehicleStatus.getTripStartTime(tripCounter);
            }
        }

        // If should generate arrival time...
        if ((indices.atEndOfTrip() || useArrivalTimes) && !indices.isWaitStop()) {
            // Create and return arrival time for this stop
            return new IpcPrediction(
                    dbConfig,
                    avlReport,
                    stopId,
                    gtfsStopSeq,
                    trip,
                    predictionTime,
                    predictionTime,
                    indices.atEndOfTrip(),
                    affectedByWaitStop,
                    isDelayed,
                    lateSoMarkAsUncertain,
                    ArrivalOrDeparture.ARRIVAL,
                    scheduleDeviation,
                    freqStartTime,
                    tripCounter,
                    vehicleStatus.isCanceled());

        } else {

            // Generate a departure time
            int expectedStopTimeMsec = (int) getStopTimeForPath(indices, avlReport, vehicleStatus);
            // If at a wait stop then need to handle specially...
            if (indices.isWaitStop()) {
                logger.debug(
                        "For vehicleId={} the original arrival time for waitStop stopId={} is {}",
                        avlReport.getVehicleId(),
                        path.getStopId(),
                        Time.dateTimeStrMsec(predictionTime));

                // At layover so need to look at if it has enough time to get
                // to the waitStop, the scheduled departure time, the expected
                // stop time since vehicles often don't depart on time, and
                // whether driver getting enough break time.
                long arrivalTime = predictionTime;
                boolean deadheadingSoNoDriverBreak = false;

                // Make sure have enough time to get to the waitStop for the
                // departure.
                double distanceToWaitStop = Geo.distance(avlReport.getLocation(), indices.getStopPath().getStopLocation());
                int crowFliesTimeToWaitStop = travelTimes.travelTimeAsTheCrowFlies(distanceToWaitStop);
                if (avlReport.getTime() + crowFliesTimeToWaitStop > arrivalTime) {
                    arrivalTime = avlReport.getTime() + crowFliesTimeToWaitStop;
                    deadheadingSoNoDriverBreak = true;
                    logger.debug(
                            "For vehicleId={} adjusted the arrival time "
                                    + "for layover stopId={} tripId={} blockId={} "
                                    + "since crowFliesTimeToLayover={} "
                                    + "but predictionTime-avlReport.getTime()={}msec. The "
                                    + "arrival time is now {}",
                            avlReport.getVehicleId(),
                            path.getStopId(),
                            trip.getId(),
                            trip.getBlockId(),
                            crowFliesTimeToWaitStop,
                            predictionTime - avlReport.getTime(),
                            Time.dateTimeStrMsec(arrivalTime));
                }

                // If it is currently before the scheduled departure time then use
                // the schedule time. But if after the scheduled time then use
                // the prediction time, which indicates when it is going to arrive
                // at the stop, but also adjust for stop wait time.
                long scheduledDepartureTime = travelTimes.scheduledDepartureTime(indices, arrivalTime);
                long expectedDepartureTime = Math.max(arrivalTime + expectedStopTimeMsec, scheduledDepartureTime);
                if (expectedDepartureTime > scheduledDepartureTime) {
                    logger.info(
                            "For vehicleId={} adjusted departure time "
                                    + "for wait stop stopId={} tripId={} blockId={} to "
                                    + "expectedDepartureTimeWithStopWaitTime={} "
                                    + "because arrivalTime={} but "
                                    + "scheduledDepartureTime={} and "
                                    + "expectedStopTimeMsec={}",
                            avlReport.getVehicleId(),
                            path.getStopId(),
                            trip.getId(),
                            trip.getBlockId(),
                            Time.dateTimeStrMsec(expectedDepartureTime),
                            Time.dateTimeStrMsec(arrivalTime),
                            Time.dateTimeStrMsec(scheduledDepartureTime),
                            expectedStopTimeMsec);
                }

                // Make sure there is enough break time for the driver to get
                // their break. But only giving drivers a break if vehicle not
                // limited by deadheading time. Thought is that if deadheading
                // then driver just starting assignment or already got break
                // elsewhere, so won't take a break at this layover.
                if (!deadheadingSoNoDriverBreak) {
                    if (expectedDepartureTime < predictionTime + (long) path.getBreakTimeSec().orElse(coreProperties.getDefaultBreakTimeSec()) * Time.MS_PER_SEC) {
                        expectedDepartureTime = predictionTime + (long) path.getBreakTimeSec().orElse(coreProperties.getDefaultBreakTimeSec()) * Time.MS_PER_SEC;
                        logger.info(
                                "For vehicleId={} adjusted departure time "
                                        + "for wait stop stopId={} tripId={} blockId={} "
                                        + "to expectedDepartureTime={} to ensure "
                                        + "driver gets break of path.getBreakTimeSec()={}",
                                avlReport.getVehicleId(),
                                path.getStopId(),
                                trip.getId(),
                                trip.getBlockId(),
                                Time.dateTimeStrMsec(expectedDepartureTime),
                                path.getBreakTimeSec());
                    }
                }

                // Create and return the departure prediction for this wait stop.
                // If supposed to use exact schedule times for the end user for
                // the wait stops then use the special IpcPrediction()
                // constructor that allows both the
                // predictionForNextStopCalculation and the predictionForUser
                // to be specified.
                if (coreProperties.isUseExactSchedTimeForWaitStops()) {
                    // Configured to use schedule times instead of prediction
                    // times for wait stops. So need to determine the prediction
                    // time without taking into account the stop wait time.
                    long expectedDepartureTimeWithoutStopWaitTime = Math.max(arrivalTime, scheduledDepartureTime);
                    if (!deadheadingSoNoDriverBreak) {
                        expectedDepartureTimeWithoutStopWaitTime = Math.max(
                                expectedDepartureTimeWithoutStopWaitTime, (long) path.getBreakTimeSec().orElse(coreProperties.getDefaultBreakTimeSec()) * Time.MS_PER_SEC);
                    }

                    long predictionForNextStopCalculation = expectedDepartureTime;
                    long predictionForUser = expectedDepartureTimeWithoutStopWaitTime;
                    return new IpcPrediction(
                            dbConfig,
                            avlReport,
                            stopId,
                            gtfsStopSeq,
                            trip,
                            predictionForUser,
                            predictionForNextStopCalculation,
                            indices.atEndOfTrip(),
                            affectedByWaitStop,
                            isDelayed,
                            lateSoMarkAsUncertain,
                            ArrivalOrDeparture.DEPARTURE,
                            scheduleDeviation,
                            freqStartTime,
                            tripCounter,
                            vehicleStatus.isCanceled());

                } else {
                    // Use the expected departure times, possibly adjusted for
                    // stop wait times
                    return new IpcPrediction(
                            dbConfig,
                            avlReport,
                            stopId,
                            gtfsStopSeq,
                            trip,
                            expectedDepartureTime,
                            expectedDepartureTime,
                            indices.atEndOfTrip(),
                            affectedByWaitStop,
                            isDelayed,
                            lateSoMarkAsUncertain,
                            ArrivalOrDeparture.DEPARTURE,
                            scheduleDeviation,
                            freqStartTime,
                            tripCounter,
                            vehicleStatus.isCanceled());
                }
            } else {
                // Create and return the departure prediction for this
                // non-wait-stop stop
                return new IpcPrediction(
                        dbConfig,
                        avlReport,
                        stopId,
                        gtfsStopSeq,
                        trip,
                        predictionTime + expectedStopTimeMsec,
                        predictionTime + expectedStopTimeMsec,
                        indices.atEndOfTrip(),
                        affectedByWaitStop,
                        isDelayed,
                        lateSoMarkAsUncertain,
                        ArrivalOrDeparture.DEPARTURE,
                        scheduleDeviation,
                        freqStartTime,
                        tripCounter,
                        vehicleStatus.isCanceled());
            }
        }
    }

    /**
     * Generates the predictions for the vehicle.
     *
     * @param vehicleStatus Contains the new match for the vehicle that the predictions are to be
     *     based on.
     * @return List of Predictions. Can be empty but will not be null.
     */
    @Override
    public List<IpcPrediction> generate(VehicleStatus vehicleStatus) {
        // For layovers always use arrival time for end of trip and
        // departure time for anything else. But for non-layover stops
        // can use either arrival or departure times, depending on what
        // the agency wants. Therefore make this configurable.
        boolean useArrivalPreds = coreProperties.isUseArrivalPredictionsForNormalStops();

        // If prediction is based on scheduled departure time for a layover then the predictions are likely not as accurate.
        // Therefore, this information needs to be part of a prediction.
        boolean affectedByWaitStop = false;

        // For storing the new predictions
        List<IpcPrediction> newPredictions = new ArrayList<>();

        // Get the new match for the vehicle that predictions are to be based on
        TemporalMatch match = vehicleStatus.getMatch();
        Indices indices = match.getIndices();

        // Get info from the AVL report.
        AvlReport avlReport = vehicleStatus.getAvlReport();
        long avlTime = avlReport.getTime();
        boolean schedBasedPreds = avlReport.isForSchedBasedPreds();

        logger.debug("Calling prediction algorithm for {} with a match {}.", avlReport, match);

        // Get time to end of first path and thereby determine prediction for
        // first stop.

        long predictionTime = avlTime + expectedTravelTimeFromMatchToEndOfStopPath(avlReport, match);

        // Determine if vehicle is so late that predictions for subsequent
        // trips should be marked as uncertain given that another vehicle
        // might substitute in for that block.
        TemporalDifference lateness = vehicleStatus.getRealTimeSchedAdh();
        boolean lateSoMarkSubsequentTripsAsUncertain =
                lateness != null && lateness.isLaterThan(coreProperties.getMaxLateCutoffPredsForNextTripsSecs());
        if (lateSoMarkSubsequentTripsAsUncertain) {
            logger.info("Vehicle late so marking predictions for subsequent trips as being uncertain. {}",
                vehicleStatus);
        }
        int currentTripIndex = indices.getTripIndex();

        // For filtering out predictions that are before now, which can
        // happen for schedule based predictions
        final long now = SystemTime.getMillis();

        // indices.incrementStopPath(predictionTime);
        Integer tripCounter = vehicleStatus.getTripCounter();

        Map<Integer, IpcPrediction> filteredPredictions = new HashMap<>();

        // Continue through block until end of block or limit on how far
        // into the future should generate predictions reached.
        while (schedBasedPreds || predictionTime < (avlTime + coreProperties.getMaxPredictionsTimeSecs() * Time.MS_PER_SEC)) {
            // Keep track of whether prediction is affected by layover
            // scheduled departure time since those predictions might not
            // be a accurate. Once a layover encountered then all subsequent
            // predictions are affected by a layover.

            // Increment indices so can generate predictions for next path
            if (indices.isWaitStop()) {
                affectedByWaitStop = true;
            }

            boolean lateSoMarkAsUncertain =
                    lateSoMarkSubsequentTripsAsUncertain && indices.getTripIndex() > currentTripIndex;

            int delay = realTimeSchedAdhProcessor
                    .generateEffectiveScheduleDifference(vehicleStatus)
                    .getTemporalDifference() / 1000;

            // Determine the new prediction
            IpcPrediction predictionForStop = generatePredictionForStop(
                    avlReport,
                    indices,
                    predictionTime,
                    useArrivalPreds,
                    affectedByWaitStop,
                    vehicleStatus.isDelayed(),
                    lateSoMarkAsUncertain,
                    tripCounter,
                    delay);

            if ((predictionForStop.getPredictionTime() - SystemTime.getMillis()) < coreProperties.getGenerateHoldingTimeWhenPredictionWithin()
                    && (predictionForStop.getPredictionTime() - SystemTime.getMillis()) > 0) {
                if (holdingTimeGenerator != null) {
                    HoldingTime holdingTime = holdingTimeGenerator
                            .generateHoldingTime(vehicleStatus, predictionForStop);
                    if (holdingTime != null) {
                        holdingTimeCache.putHoldingTime(holdingTime);
                        vehicleStatus.setHoldingTime(holdingTime);
                    }
                }
            }

            logger.debug("For vehicleId={} generated prediction {}", vehicleStatus.getVehicleId(), predictionForStop);

            // If prediction ended up being too far in the future (which can
            // happen if it is a departure prediction where the time at the
            // stop is added to the arrival time) then don't add the prediction
            // and break out of the loop.
            if (!schedBasedPreds
                    && predictionForStop.getPredictionTime()
                            > avlTime + coreProperties.getMaxPredictionsTimeSecs() * Time.MS_PER_SEC) break;

            // If no schedule assignment then don't want to generate predictions
            // for the last stop of the trip since it is a duplicate of the
            // first stop of the trip
            boolean lastStopOfNonSchedBasedTrip = indices.getBlock().isNoSchedule() && indices.atEndOfTrip();

            // This is incremented each time the prediction starts a new trip.
            // The first prediction for the start of a new trip is used as the
            // start time for a frequency based service
            if (lastStopOfNonSchedBasedTrip) {
                tripCounter++;
                vehicleStatus.putTripStartTime(tripCounter, predictionForStop.getPredictionTime());
                // break;
            }

            // The prediction is not too far into the future. Add it to the
            // list of predictions to be returned. But only do this if
            // it is not last stop of non-schedule based trip since that is a
            // a duplicate of the stop for the next trip. Also, don't add
            // prediction if it is in the past since those are not needed.
            // Can get predictions in the past for schedule based predictions.
            if (!lastStopOfNonSchedBasedTrip && predictionForStop.getPredictionTime() > now) {
                logger.debug("Generated IpcPrediction [vehicle={}, route={}, stop={}, eta={}] based on {}.",
                        predictionForStop.getVehicleId(),
                        predictionForStop.getRouteShortName() != null ? predictionForStop.getRouteShortName() : predictionForStop.getRouteId(),
                        predictionForStop.getStopId(),
                        Time.dateTimeStr(predictionForStop.getPredictionTime()),
                        avlReport);

                if (indices.atEndOfTrip() || indices.atBeginningOfTrip()) {
                    // Deals with case where a vehicle transitions from one trip to another and the lastStop then becomes the firstSTop
                    // This occasionally leads to duplicate predictions. This works around the problem by creating a hash of predictions
                    // that have the same Prediction information but different trips
                    int predictionKey = lastStopPredictionHash(predictionForStop);
                    if (filteredPredictions.containsKey(predictionKey) && filteredPredictions.get(predictionKey) != null) {
                        Integer filteredPredictionTripStartTime = filteredPredictions
                                .get(predictionKey)
                                .getTrip()
                                .getStartTime();
                        if (predictionForStop.getTrip().getStartTime() > filteredPredictionTripStartTime) {
                            logger.warn("Found multiple predictions for Prediction with routeId={}, stopId={}, and vehicleId={} ",
                                    predictionForStop.getRouteId(),
                                    predictionForStop.getStopId(),
                                    predictionForStop.getVehicleId());
                            filteredPredictions.put(predictionKey, predictionForStop);
                        }
                    } else {
                        filteredPredictions.put(predictionKey, predictionForStop);
                    }
                } else {
                    newPredictions.add(predictionForStop);
                }
            }

            // Determine prediction time for the departure. For layovers
            // the prediction time can be adjusted by deadhead time,
            // schedule time, break time, etc. For arrival predictions
            // need to add the expected stop time. Need to use
            // getActualPredictionTime() instead of getPredictionTime() to
            // handle situations where want to display to the user for wait
            // stops schedule times instead of the calculated prediction time.
            predictionTime = predictionForStop.getActualPredictionTime();

            if (predictionForStop.isArrival()) {
                predictionTime += getStopTimeForPath(indices, avlReport, vehicleStatus);
                /* TODO this is where we should take account of holding time */
                if (coreProperties.isUseHoldingTimeInPrediction() && holdingTimeGenerator != null) {
                    HoldingTime holdingTime = holdingTimeGenerator
                            .generateHoldingTime(vehicleStatus, predictionForStop);

                    if (holdingTime != null) {
                        long holdingTimeMsec = holdingTime.getHoldingTime().getTime() - holdingTime.getArrivalTime().getTime();
                        if (holdingTimeMsec > indices.getStopTimeForPath()) {
                            predictionTime += holdingTime.getHoldingTime().getTime() - holdingTime.getArrivalTime().getTime();
                        }
                    }
                }
            }
            indices.incrementStopPath(predictionTime, dbConfig);
            // If reached end of block then done
            if (indices.pastEndOfBlock(predictionTime, dbConfig)) {
                logger.debug("For vehicleId={} reached end of block when generating predictions.",
                        vehicleStatus.getVehicleId());
                break;
            }
            boolean isCircuitRoute = true;
            // Add in travel time for the next path to get to predicted
            // arrival time of this stop
            if (!lastStopOfNonSchedBasedTrip && isCircuitRoute) {
                predictionTime += getTravelTimeForPath(indices, avlReport, vehicleStatus);
            }
        }

        newPredictions.addAll(filteredPredictions.values());

        // Return the results
        return newPredictions;
    }

    public long getTravelTimeForPath(Indices indices, AvlReport avlReport, VehicleStatus vehicleStatus) {
        // logger.debug("Using transiTime default algorithm for travel time prediction : " + indices
        // + " Value: "+indices.getTravelTimeForPath());
        if (coreProperties.getStoreTravelTimeStopPathPredictions()) {
            PredictionForStopPath predictionForStopPath = new PredictionForStopPath(
                    vehicleStatus.getVehicleId(),
                    SystemTime.getDate(),
                    (double)(indices.getTravelTimeForPath()),
                    indices.getTrip().getId(),
                    indices.getStopPathIndex(),
                    "TRANSITIME DEFAULT",
                    true,
                    null);
            dataDbLogger.add(predictionForStopPath);
            stopPathPredictionCache.putPrediction(predictionForStopPath);
        }
        return indices.getTravelTimeForPath();
    }

    public long getStopTimeForPath(Indices indices, AvlReport avlReport, VehicleStatus vehicleStatus) {
        return travelTimes.expectedStopTimeForStopPath(indices);
    }

    public long expectedTravelTimeFromMatchToEndOfStopPath(AvlReport avlReport, SpatialMatch match) {
        return travelTimes.expectedTravelTimeFromMatchToEndOfStopPath(match);
    }

    private int lastStopPredictionHash(IpcPrediction prediction) {
        final int prime = 31;
        int result = 1;

        result = prime * result
                + ((prediction.getBlockId() == null)
                        ? 0
                        : prediction.getBlockId().hashCode());
        result = prime * result
                + ((prediction.getVehicleId() == null)
                        ? 0
                        : prediction.getVehicleId().hashCode());
        result = prime * result
                + ((prediction.getStopId() == null) ? 0 : prediction.getStopId().hashCode());
        result = prime * result
                + ((prediction.getRouteId() == null)
                        ? 0
                        : prediction.getRouteId().hashCode());
        result = prime * result + Long.valueOf(prediction.getPredictionTime()).hashCode();

        return result;
    }
}
