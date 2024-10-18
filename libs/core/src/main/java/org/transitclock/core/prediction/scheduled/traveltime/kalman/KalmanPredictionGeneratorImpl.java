/* (C)2023 */
package org.transitclock.core.prediction.scheduled.traveltime.kalman;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.transitclock.core.Indices;
import org.transitclock.core.TravelTimeDetails;
import org.transitclock.core.TravelTimes;
import org.transitclock.core.VehicleStatus;
import org.transitclock.core.avl.RealTimeSchedAdhProcessor;
import org.transitclock.core.avl.space.SpatialMatch;
import org.transitclock.core.dataCache.ErrorCache;
import org.transitclock.core.dataCache.HoldingTimeCache;
import org.transitclock.core.dataCache.KalmanError;
import org.transitclock.core.dataCache.KalmanErrorCacheKey;
import org.transitclock.core.dataCache.StopArrivalDepartureCacheInterface;
import org.transitclock.core.dataCache.StopPathPredictionCache;
import org.transitclock.core.dataCache.TripDataHistoryCacheInterface;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.core.holdingmethod.HoldingTimeGenerator;
import org.transitclock.core.prediction.PredictionGeneratorDefaultImpl;
import org.transitclock.core.prediction.bias.BiasAdjuster;
import org.transitclock.core.prediction.datafilter.TravelTimeDataFilter;
import org.transitclock.core.prediction.kalman.KalmanPrediction;
import org.transitclock.core.prediction.kalman.KalmanPredictionResult;
import org.transitclock.core.prediction.kalman.TripSegment;
import org.transitclock.core.prediction.kalman.Vehicle;
import org.transitclock.core.prediction.kalman.VehicleStopDetail;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.domain.structs.PredictionEvent;
import org.transitclock.domain.structs.PredictionForStopPath;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.PredictionProperties;
import org.transitclock.utils.SystemTime;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;

/**
 * @author Sean Óg Crudden This is a prediction generator that uses a Kalman filter to provide
 *     predictions. It uses historical average while waiting on enough data to support a Kalman
 *     filter.
 */
@Slf4j
public class KalmanPredictionGeneratorImpl extends PredictionGeneratorDefaultImpl {

    private final String alternative = "PredictionGeneratorDefaultImpl";

    private final ErrorCache kalmanErrorCache;

    public KalmanPredictionGeneratorImpl(StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface,
                                         TripDataHistoryCacheInterface tripDataHistoryCacheInterface,
                                         DbConfig dbConfig,
                                         DataDbLogger dataDbLogger,
                                         TravelTimeDataFilter travelTimeDataFilter,
                                         PredictionProperties properties,
                                         HoldingTimeCache holdingTimeCache, StopPathPredictionCache stopPathPredictionCache, TravelTimes travelTimes, HoldingTimeGenerator holdingTimeGenerator, VehicleStatusManager vehicleStatusManager, RealTimeSchedAdhProcessor realTimeSchedAdhProcessor, BiasAdjuster biasAdjuster, ErrorCache kalmanErrorCache) {
        super(stopArrivalDepartureCacheInterface, tripDataHistoryCacheInterface, dbConfig, dataDbLogger, travelTimeDataFilter, properties, holdingTimeCache, stopPathPredictionCache, travelTimes, holdingTimeGenerator, vehicleStatusManager, realTimeSchedAdhProcessor, biasAdjuster);
        this.kalmanErrorCache = kalmanErrorCache;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.transitclock.core.prediction.PredictionGeneratorDefaultImpl#getTravelTimeForPath
     * (org.transitclock.core.Indices, org.transitclock.db.structs.AvlReport)
     */
    @Override
    public long getTravelTimeForPath(Indices indices, AvlReport avlReport, VehicleStatus vehicleStatus) {

        logger.debug("Calling Kalman prediction algorithm for : {}", indices);

        long alternatePrediction = super.getTravelTimeForPath(indices, avlReport, vehicleStatus);
        var currentVehicleState = vehicleStatusManager.getStatus(avlReport.getVehicleId());

        try {
            TravelTimeDetails travelTimeDetails = this.getLastVehicleTravelTime(currentVehicleState, indices);

            /*
             * The first vehicle of the day should use schedule or historic data to
             * make prediction. Cannot use Kalman as yesterday's vehicle will have
             * little to say about today's.
             */
            if (travelTimeDetails != null) {
                logger.debug("Kalman has last vehicle info for : {} : {}", indices, travelTimeDetails);
                Date nearestDay = DateUtils.truncate(avlReport.getDate(), Calendar.DAY_OF_MONTH);

                List<TravelTimeDetails> lastDaysTimes = lastDaysTimes(
                        tripDataHistoryCacheInterface,
                        currentVehicleState.getTrip().getId(),
                        currentVehicleState.getTrip().getDirectionId(),
                        indices.getStopPathIndex(),
                        nearestDay,
                        currentVehicleState.getTrip().getStartTime(),
                        predictionProperties.getData().getKalman().getMaxdaystosearch(),
                        predictionProperties.getData().getKalman().getMaxdays());

                if (lastDaysTimes != null) {
                    logger.debug("Kalman has {} historical values for : {}", lastDaysTimes.size(), indices);
                }

                /*
                 * if we have enough data start using Kalman filter otherwise revert
                 * to extended class for prediction.
                 */
                if (lastDaysTimes != null && lastDaysTimes.size() >= predictionProperties.getData().getKalman().getMindays()) {

                    logger.debug("Generating Kalman prediction for : {}", indices);
                    try {
                        KalmanPrediction kalmanPrediction = new KalmanPrediction(predictionProperties.getData().getKalman());
                        KalmanPredictionResult kalmanPredictionResult;
                        Vehicle vehicle = new Vehicle(avlReport.getVehicleId());
                        VehicleStopDetail originDetail = new VehicleStopDetail(null, 0, vehicle);
                        TripSegment[] historical_segments_k = new TripSegment[lastDaysTimes.size()];

                        for (int i = 0; i < lastDaysTimes.size() && i < predictionProperties.getData().getKalman().getMaxdays(); i++) {
                            logger.debug("Kalman is using historical value : {} for : {}", lastDaysTimes.get(i), indices);

                            VehicleStopDetail destinationDetail = new VehicleStopDetail(null, lastDaysTimes.get(i).getTravelTime(), vehicle);
                            historical_segments_k[lastDaysTimes.size() - i - 1] = new TripSegment(originDetail, destinationDetail);
                        }

                        VehicleStopDetail destinationDetail_0_k_1 =
                                new VehicleStopDetail(null, travelTimeDetails.getTravelTime(), vehicle);

                        TripSegment ts_day_0_k_1 = new TripSegment(originDetail, destinationDetail_0_k_1);
                        TripSegment last_vehicle_segment = ts_day_0_k_1;
                        Indices previousVehicleIndices = new Indices(travelTimeDetails.getArrival(), dbConfig);

                        KalmanError last_prediction_error =
                                lastVehiclePredictionError(kalmanErrorCache, previousVehicleIndices);

                        logger.debug("Using error value: {} found with vehicle id {} from: {}",
                                last_prediction_error, travelTimeDetails.getArrival().getVehicleId(),
                                new KalmanErrorCacheKey(previousVehicleIndices));

                        // TODO this should also display the detail of which vehicle it choose as
                        // the last one.
                        logger.debug("Using last vehicle value: {} for : {}", travelTimeDetails, indices);

                        kalmanPredictionResult = kalmanPrediction.predict(
                                last_vehicle_segment, historical_segments_k, last_prediction_error.getError());

                        long predictionTime = (long) kalmanPredictionResult.getResult();

                        logger.debug("Setting Kalman error value: {} for : {}", kalmanPredictionResult.getFilterError(), new KalmanErrorCacheKey(indices));

                        kalmanErrorCache.putErrorValue(indices, kalmanPredictionResult.getFilterError());

                        double percentageDifferecence =
                                Math.abs(100 * ((predictionTime - alternatePrediction) / (double) alternatePrediction));

                        if (((percentageDifferecence * alternatePrediction) / 100) > predictionProperties.getData().getKalman().getThresholdForDifferenceEventLog()) {
                            if (percentageDifferecence > predictionProperties.getData().getKalman().getPercentagePredictionMethodDifferencene()) {
                                String description = "Kalman predicts : " + predictionTime + " Super predicts : " + alternatePrediction;

                                logger.warn(description);

                                PredictionEvent predictionEvent = new PredictionEvent(
                                        avlReport,
                                        vehicleStatus.getMatch(),
                                        PredictionEvent.PREDICTION_VARIATION,
                                        description,
                                        travelTimeDetails.getArrival().getStopId(),
                                        travelTimeDetails.getDeparture().getStopId(),
                                        travelTimeDetails.getArrival().getVehicleId(),
                                        travelTimeDetails.getArrival().getTime(),
                                        travelTimeDetails.getDeparture().getTime());
                                dataDbLogger.add(predictionEvent);
                            }
                        }

                        logger.debug("Using Kalman prediction: {} instead of " + alternative + " prediction: {} for : {}", predictionTime, alternatePrediction, indices);

                        if (coreProperties.getStoreTravelTimeStopPathPredictions()) {
                            PredictionForStopPath predictionForStopPath = new PredictionForStopPath(
                                    vehicleStatus.getVehicleId(),
                                    SystemTime.getDate(),
                                    (double) Long.valueOf(predictionTime).intValue(),
                                    indices.getTrip().getId(),
                                    indices.getStopPathIndex(),
                                    "KALMAN",
                                    true,
                                    null);
                            dataDbLogger.add(predictionForStopPath);
                            stopPathPredictionCache.putPrediction(predictionForStopPath);
                        }
                        return predictionTime;

                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return alternatePrediction;
    }

    @Override
    public long expectedTravelTimeFromMatchToEndOfStopPath(AvlReport avlReport, SpatialMatch match) {

        if (predictionProperties.getData().getKalman().getUsekalmanforpartialstoppaths()) {
            VehicleStatus currentVehicleStatus = vehicleStatusManager.getStatus(avlReport.getVehicleId());

            long fulltime = this.getTravelTimeForPath(match.getIndices(), avlReport, currentVehicleStatus);

            double distanceAlongStopPath = match.getDistanceAlongStopPath();

            double stopPathLength = match.getStopPath().getLength();

            long remainingtime = (long) (fulltime * ((stopPathLength - distanceAlongStopPath) / stopPathLength));

            logger.debug(
                    "Using Kalman for first stop path {} with value {} instead of {}.",
                    match.getIndices(),
                    remainingtime,
                    super.expectedTravelTimeFromMatchToEndOfStopPath(avlReport, match));

            return remainingtime;
        } else {
            return super.expectedTravelTimeFromMatchToEndOfStopPath(avlReport, match);
        }
    }

    private KalmanError lastVehiclePredictionError(ErrorCache cache, Indices indices) {

        KalmanError result;
        try {
            result = cache.getErrorValue(indices);
            if (result == null) {
                logger.debug("Kalman Error value set to default: {} for key: {}", predictionProperties.getData().getKalman().getInitialerrorvalue(), new KalmanErrorCacheKey(indices));
                result = new KalmanError(predictionProperties.getData().getKalman().getInitialerrorvalue());
            }
            return result;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public long getStopTimeForPath(Indices indices, AvlReport avlReport, VehicleStatus vehicleStatus) {

        return super.getStopTimeForPath(indices, avlReport, vehicleStatus);
    }
}
