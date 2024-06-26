/* (C)2023 */
package org.transitclock.core.prediction;

import org.apache.commons.lang3.time.DateUtils;

import org.transitclock.core.Indices;
import org.transitclock.core.TravelTimeDetails;
import org.transitclock.core.VehicleStatus;
import org.transitclock.core.dataCache.*;
import org.transitclock.core.prediction.datafilter.TravelTimeDataFilter;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.domain.structs.ArrivalDeparture;
import org.transitclock.domain.structs.Block;
import org.transitclock.domain.structs.PredictionEvent;
import org.transitclock.domain.structs.Route;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.PredictionProperties;
import org.transitclock.service.dto.IpcArrivalDeparture;
import org.transitclock.service.dto.IpcPrediction;

import java.util.*;

/**
 * Defines the interface for generating predictions. To create predictions using an alternate method
 * simply implement this interface and configure PredictionGeneratorFactory to instantiate the new
 * class when a PredictionGenerator is needed.
 *
 * @author SkiBu Smith
 */
public abstract class AbstractPredictionGenerator implements PredictionGenerator {
    protected final StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface;
    protected final TripDataHistoryCacheInterface tripDataHistoryCacheInterface;
    protected final DbConfig dbConfig;
    protected final DataDbLogger dataDbLogger;
    protected final TravelTimeDataFilter travelTimeDataFilter;
    protected final PredictionProperties predictionProperties;

    protected AbstractPredictionGenerator(StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface,
                                          TripDataHistoryCacheInterface tripDataHistoryCacheInterface,
                                          DbConfig dbConfig,
                                          DataDbLogger dataDbLogger,
                                          TravelTimeDataFilter travelTimeDataFilter,
                                          PredictionProperties predictionProperties) {
        this.stopArrivalDepartureCacheInterface = stopArrivalDepartureCacheInterface;
        this.tripDataHistoryCacheInterface = tripDataHistoryCacheInterface;
        this.dbConfig = dbConfig;
        this.dataDbLogger = dataDbLogger;
        this.travelTimeDataFilter = travelTimeDataFilter;
        this.predictionProperties = predictionProperties;
    }

    /**
     * Generates and returns the predictions for the vehicle.
     *
     * @param vehicleStatus Contains the new match for the vehicle that the predictions are to be
     *                     based on.
     */
    public abstract List<IpcPrediction> generate(VehicleStatus vehicleStatus);


    protected TravelTimeDetails getLastVehicleTravelTime(VehicleStatus currentVehicleStatus, Indices indices) {

        StopArrivalDepartureCacheKey nextStopKey = new StopArrivalDepartureCacheKey(
            indices.getStopPath().getStopId(),
            new Date(currentVehicleStatus.getMatch().getAvlTime()));

        /* TODO how do we handle the the first stop path. Where do we get the first stop id. */
        if (!indices.atBeginningOfTrip()) {
            String currentStopId = indices.getPreviousStopPath().getStopId();

            StopArrivalDepartureCacheKey currentStopKey = new StopArrivalDepartureCacheKey(
                currentStopId, new Date(currentVehicleStatus.getMatch().getAvlTime()));

            List<IpcArrivalDeparture> currentStopList =
                stopArrivalDepartureCacheInterface.getStopHistory(currentStopKey);

            List<IpcArrivalDeparture> nextStopList =
                stopArrivalDepartureCacheInterface.getStopHistory(nextStopKey);

            if (currentStopList != null && nextStopList != null) {
                // lists are already sorted when put into cache.
                for (IpcArrivalDeparture currentArrivalDeparture : currentStopList) {

                    if (currentArrivalDeparture.isDeparture()
                        && !currentArrivalDeparture.getVehicleId().equals(currentVehicleStatus.getVehicleId())
                        && (currentVehicleStatus.getTrip().getDirectionId() == null
                        || currentVehicleStatus
                        .getTrip()
                        .getDirectionId()
                        .equals(currentArrivalDeparture.getDirectionId()))) {
                        IpcArrivalDeparture found;

                        if ((found = findMatchInList(nextStopList, currentArrivalDeparture)) != null) {
                            TravelTimeDetails travelTimeDetails = new TravelTimeDetails(currentArrivalDeparture, found, travelTimeDataFilter);
                            if (travelTimeDetails.getTravelTime() > 0) {
                                return travelTimeDetails;

                            } else {
                                String description = found + " : " + currentArrivalDeparture;
                                PredictionEvent predictionEvent = new PredictionEvent(
                                    currentVehicleStatus.getAvlReport(),
                                    currentVehicleStatus.getMatch(),
                                    PredictionEvent.TRAVELTIME_EXCEPTION,
                                    description,
                                    travelTimeDetails.getArrival().getStopId(),
                                    travelTimeDetails.getDeparture().getStopId(),
                                    travelTimeDetails.getArrival().getVehicleId(),
                                    travelTimeDetails.getArrival().getTime(),
                                    travelTimeDetails.getDeparture().getTime());
                                dataDbLogger.add(predictionEvent);
                                return null;
                            }
                        } else {
                            return null;
                        }
                    }
                }
            }
        }
        return null;
    }

    protected Indices getLastVehicleIndices(VehicleStatus currentVehicleStatus, Indices indices) {

        StopArrivalDepartureCacheKey nextStopKey = new StopArrivalDepartureCacheKey(
            indices.getStopPath().getStopId(),
            new Date(currentVehicleStatus.getMatch().getAvlTime()));

        /* TODO how do we handle the the first stop path. Where do we get the first stop id. */
        if (!indices.atBeginningOfTrip()) {
            String currentStopId = indices.getPreviousStopPath().getStopId();

            StopArrivalDepartureCacheKey currentStopKey = new StopArrivalDepartureCacheKey(
                currentStopId, new Date(currentVehicleStatus.getMatch().getAvlTime()));

            List<IpcArrivalDeparture> currentStopList =
                stopArrivalDepartureCacheInterface.getStopHistory(currentStopKey);

            List<IpcArrivalDeparture> nextStopList =
                stopArrivalDepartureCacheInterface.getStopHistory(nextStopKey);

            if (currentStopList != null && nextStopList != null) {
                // lists are already sorted when put into cache.
                for (IpcArrivalDeparture currentArrivalDeparture : currentStopList) {

                    if (currentArrivalDeparture.isDeparture()
                        && !currentArrivalDeparture.getVehicleId().equals(currentVehicleStatus.getVehicleId())
                        && (currentVehicleStatus.getTrip().getDirectionId() == null
                        || currentVehicleStatus
                        .getTrip()
                        .getDirectionId()
                        .equals(currentArrivalDeparture.getDirectionId()))) {
                        IpcArrivalDeparture found;

                        if ((found = findMatchInList(nextStopList, currentArrivalDeparture)) != null) {
                            if (found.getTime().getTime()
                                - currentArrivalDeparture.getTime().getTime()
                                > 0) {
                                Block currentBlock = null;
                                /* block is transient in arrival departure so when read from database need to get from dbconfig. */

                                currentBlock = dbConfig.getBlock(
                                    currentArrivalDeparture.getServiceId(), currentArrivalDeparture.getBlockId());

                                if (currentBlock != null)
                                    return new Indices(
                                        currentBlock,
                                        currentArrivalDeparture.getTripIndex(),
                                        found.getStopPathIndex(),
                                        0);
                            } else {
                                // must be going backwards
                                return null;
                            }
                        } else {
                            return null;
                        }
                    }
                }
            }
        }
        return null;
    }

    /* TODO could also make it a requirement that it is on the same route as the one we are generating prediction for */
    protected IpcArrivalDeparture findMatchInList(
        List<IpcArrivalDeparture> nextStopList, IpcArrivalDeparture currentArrivalDeparture) {
        for (IpcArrivalDeparture nextStopArrivalDeparture : nextStopList) {
            if (currentArrivalDeparture.getVehicleId().equals(nextStopArrivalDeparture.getVehicleId())
                && currentArrivalDeparture.getTripId().equals(nextStopArrivalDeparture.getTripId())
                && currentArrivalDeparture.isDeparture()
                && nextStopArrivalDeparture.isArrival()) {
                return nextStopArrivalDeparture;
            }
        }
        return null;
    }

    protected VehicleStatus getClosetVehicle(
        List<VehicleStatus> vehiclesOnRoute, Indices indices, VehicleStatus currentVehicleStatus) {

        Route routeById = dbConfig.getRouteById(currentVehicleStatus.getTrip().getRouteId());
        Map<String, List<String>> stopsByDirection = routeById.getOrderedStopsByDirection(dbConfig);

        List<String> routeStops =
            stopsByDirection.get(currentVehicleStatus.getTrip().getDirectionId());

        int closest = 100;

        VehicleStatus result = null;

        for (VehicleStatus vehicle : vehiclesOnRoute) {

            Integer numAfter = numAfter(
                routeStops,
                vehicle.getMatch().getStopPath().getStopId(),
                currentVehicleStatus.getMatch().getStopPath().getStopId());

            if (numAfter != null && numAfter > predictionProperties.getClosestvehiclestopsahead() && numAfter < closest) {
                closest = numAfter;
                result = vehicle;
            }
        }
        return result;
    }

    boolean isAfter(List<String> stops, String stop1, String stop2) {
        if (stops != null && stop1 != null && stop2 != null) {
            if (stops.contains(stop1) && stops.contains(stop2)) {
                return stops.indexOf(stop1) > stops.indexOf(stop2);
            }
        }
        return false;
    }

    protected Integer numAfter(List<String> stops, String stop1, String stop2) {
        if (stops != null && stop1 != null && stop2 != null)
            if (stops.contains(stop1) && stops.contains(stop2)) return stops.indexOf(stop1) - stops.indexOf(stop2);

        return null;
    }

    protected List<TravelTimeDetails> lastDaysTimes(
        TripDataHistoryCacheInterface cache,
        String tripId,
        String direction,
        int stopPathIndex,
        Date startDate,
        Integer startTime,
        int num_days_look_back,
        int num_days) {

        List<IpcArrivalDeparture> results;
        List<TravelTimeDetails> times = new ArrayList<>();
        int num_found = 0;
        /*
         * TODO This could be smarter about the dates it looks at by looking at
         * which services use this trip and only 1ook on day service is
         * running
         */
        for (int i = 0; i < num_days_look_back && num_found < num_days; i++) {
            Date nearestDay = DateUtils.truncate(DateUtils.addDays(startDate, (i + 1) * -1), Calendar.DAY_OF_MONTH);

            TripKey tripKey = new TripKey(tripId, nearestDay, startTime);

            results = cache.getTripHistory(tripKey);

            if (results != null) {

                IpcArrivalDeparture arrival = getArrival(stopPathIndex, results);

                if (arrival != null) {
                    IpcArrivalDeparture departure = tripDataHistoryCacheInterface.findPreviousDepartureEvent(results, arrival);

                    if (departure != null) {

                        TravelTimeDetails travelTimeDetails = new TravelTimeDetails(departure, arrival, travelTimeDataFilter);

                        if (travelTimeDetails.getTravelTime() != -1) {
                            if (!travelTimeDataFilter.filter(
                                travelTimeDetails.getDeparture(), travelTimeDetails.getArrival())) {
                                times.add(travelTimeDetails);
                                num_found++;
                            }
                        }
                    }
                }
            }
        }
        return times;
    }

    protected IpcArrivalDeparture getArrival(int stopPathIndex, List<IpcArrivalDeparture> results) {
        for (IpcArrivalDeparture result : results) {
            if (result.isArrival() && result.getStopPathIndex() == stopPathIndex) {
                return result;
            }
        }
        return null;
    }

    protected long timeBetweenStops(ArrivalDeparture ad1, ArrivalDeparture ad2) {
        return Math.abs(ad2.getTime() - ad1.getTime());
    }

    protected static <T> Iterable<T> emptyIfNull(Iterable<T> iterable) {
        return iterable == null ? Collections.emptyList() : iterable;
    }
}
