/* (C)2023 */
package org.transitclock.core.prediction.lastvehicle;

import java.util.ArrayList;
import java.util.List;

import org.transitclock.core.Indices;
import org.transitclock.core.TravelTimeDetails;
import org.transitclock.core.TravelTimes;
import org.transitclock.core.VehicleStatus;
import org.transitclock.core.avl.RealTimeSchedAdhProcessor;
import org.transitclock.core.dataCache.HoldingTimeCache;
import org.transitclock.core.dataCache.StopArrivalDepartureCacheInterface;
import org.transitclock.core.dataCache.StopPathPredictionCache;
import org.transitclock.core.dataCache.TripDataHistoryCacheInterface;
import org.transitclock.core.dataCache.VehicleDataCache;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.core.holdingmethod.HoldingTimeGenerator;
import org.transitclock.core.prediction.PredictionGeneratorDefaultImpl;
import org.transitclock.core.prediction.bias.BiasAdjuster;
import org.transitclock.core.prediction.datafilter.TravelTimeDataFilter;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.domain.structs.PredictionForStopPath;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.CoreProperties;
import org.transitclock.properties.PredictionProperties;
import org.transitclock.service.dto.IpcPrediction;
import org.transitclock.service.dto.IpcVehicleComplete;
import org.transitclock.utils.SystemTime;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Sean Óg Crudden This provides a prediction based on the time it took the previous vehicle
 *     on the same route to cover the same ground. This is another step to get to Kalman
 *     implementation.
 *     <p>TODO Debug as this has yet to be tried and tested. Could do a combination with historical
 *     average so that it improves quickly rather than just waiting on having enough data to support
 *     average or Kalman. So do a progression from LastVehicle --> Historical Average --> Kalman.
 *     Might be interesting to look at the rate of improvement of prediction as well as the end
 *     result.
 *     <p>Does this by changing which class each extends. How can we make configurable?
 *     <p>This works for both schedules based and frequency based services out of the box. Not so
 *     for historical average or Kalman filter.
 */
@Slf4j
public class LastVehiclePredictionGeneratorImpl extends PredictionGeneratorDefaultImpl {
    protected final VehicleDataCache vehicleCache;
    protected final CoreProperties coreProperties;


    public LastVehiclePredictionGeneratorImpl(StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface,
                                              TripDataHistoryCacheInterface tripDataHistoryCacheInterface,
                                              DbConfig dbConfig,
                                              DataDbLogger dataDbLogger,
                                              TravelTimeDataFilter travelTimeDataFilter,
                                              PredictionProperties properties,
                                              VehicleDataCache vehicleCache,
                                              HoldingTimeCache holdingTimeCache,
                                              StopPathPredictionCache stopPathPredictionCache,
                                              TravelTimes travelTimes,
                                              HoldingTimeGenerator holdingTimeGenerator,
                                              VehicleStatusManager vehicleStatusManager,
                                              RealTimeSchedAdhProcessor realTimeSchedAdhProcessor,
                                              BiasAdjuster biasAdjuster,
                                              CoreProperties coreProperties) {
        super(stopArrivalDepartureCacheInterface, tripDataHistoryCacheInterface, dbConfig, dataDbLogger, travelTimeDataFilter, properties, holdingTimeCache, stopPathPredictionCache, travelTimes, holdingTimeGenerator, vehicleStatusManager, realTimeSchedAdhProcessor, biasAdjuster);
        this.vehicleCache = vehicleCache;
        this.coreProperties = coreProperties;
    }

    @Override
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
        // TODO Auto-generated method stub
        return super.generatePredictionForStop(
                avlReport,
                indices,
                predictionTime,
                useArrivalTimes,
                affectedByWaitStop,
                isDelayed,
                lateSoMarkAsUncertain,
                tripCounter,
                scheduleDeviation);
    }

    @Override
    public long getTravelTimeForPath(Indices indices, AvlReport avlReport, VehicleStatus vehicleStatus) {
        List<VehicleStatus> vehiclesOnRoute = new ArrayList<>();
        VehicleStatus currentVehicleStatus = vehicleStatusManager.getStatus(avlReport.getVehicleId());

        for (IpcVehicleComplete vehicle :
                emptyIfNull(vehicleCache.getVehiclesForRoute(currentVehicleStatus.getRouteId()))) {
            VehicleStatus vehicleOnRouteState = vehicleStatusManager.getStatus(vehicle.getId());
            vehiclesOnRoute.add(vehicleOnRouteState);
        }

        try {
            TravelTimeDetails travelTimeDetails = null;
            if ((travelTimeDetails = this.getLastVehicleTravelTime(currentVehicleStatus, indices)) != null) {
                logger.debug("Using last vehicle algorithm for prediction : {} for : {}", travelTimeDetails, indices);

                if (coreProperties.getStoreTravelTimeStopPathPredictions()) {
                    PredictionForStopPath predictionForStopPath = new PredictionForStopPath(
                            vehicleStatus.getVehicleId(),
                            SystemTime.getDate(),
                            (double) Long.valueOf(travelTimeDetails.getTravelTime()).intValue(),
                            indices.getTrip().getId(),
                            indices.getStopPathIndex(),
                            "LAST VEHICLE",
                            true,
                            null);

                    dataDbLogger.add(predictionForStopPath);
                    stopPathPredictionCache.putPrediction(predictionForStopPath);
                }

                return travelTimeDetails.getTravelTime();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        /* default to parent method if not enough data. This will be based on schedule if UpdateTravelTimes has not been called. */
        return super.getTravelTimeForPath(indices, avlReport, currentVehicleStatus);
    }

    @Override
    public long getStopTimeForPath(Indices indices, AvlReport avlReport, VehicleStatus vehicleStatus) {
        // Looking at last vehicle value would be a bad idea for dwell time, so no implementation
        // here.
        return super.getStopTimeForPath(indices, avlReport, vehicleStatus);
    }
}
