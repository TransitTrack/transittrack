/* (C)2023 */
package org.transitclock.core.headwaygenerator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.transitclock.core.VehicleStatus;
import org.transitclock.core.dataCache.StopArrivalDepartureCacheInterface;
import org.transitclock.core.dataCache.StopArrivalDepartureCacheKey;
import org.transitclock.core.dataCache.VehicleDataCache;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.domain.structs.Headway;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.CoreProperties;
import org.transitclock.service.dto.IpcArrivalDeparture;
import org.transitclock.service.dto.IpcVehicleComplete;

/**
 * @author Sean Óg Crudden
 *     <p>This is a first pass at generating a Headway value. It will find the last departure time
 *     at the last stop for the vehicle and then get the vehicle ahead of it and check when it
 *     departed the same stop. The difference will be used as the headway.
 *     <p>This is a WIP
 *     <p>Maybe should be a list and have a predicted headway at each stop along the route. So key
 *     for headway could be (stop, vehicle, trip, start_time).
 */
@Slf4j
class LastDepartureHeadwayGenerator implements HeadwayGenerator {
    private final VehicleDataCache vehicleDataCache;
    private final VehicleStatusManager vehicleStatusManager;
    private final StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface;
    private final DbConfig dbConfig;
    private final CoreProperties coreProperties;

    public LastDepartureHeadwayGenerator(VehicleDataCache vehicleDataCache, VehicleStatusManager vehicleStatusManager, StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface, DbConfig dbConfig, CoreProperties coreProperties) {
        this.vehicleDataCache = vehicleDataCache;
        this.vehicleStatusManager = vehicleStatusManager;
        this.stopArrivalDepartureCacheInterface = stopArrivalDepartureCacheInterface;
        this.dbConfig = dbConfig;
        this.coreProperties = coreProperties;
    }

    @Override
    public Headway generate(VehicleStatus vehicleStatus) {

        try {
            String stopId = vehicleStatus.getMatch()
                            .getMatchAtPreviousStop(coreProperties).getAtStop().getStopId();
            long date = vehicleStatus.getMatch().getAvlTime();
            String vehicleId = vehicleStatus.getVehicleId();
            StopArrivalDepartureCacheKey key = new StopArrivalDepartureCacheKey(stopId, new Date(date));

            List<IpcArrivalDeparture> stopList = stopArrivalDepartureCacheInterface.getStopHistory(key);
            int lastStopArrivalIndex = -1;
            int previousVehicleArrivalIndex = -1;

            if (stopList != null) {
                for (int i = 0; i < stopList.size() && previousVehicleArrivalIndex == -1; i++) {
                    IpcArrivalDeparture arrivalDepature = stopList.get(i);
                    if (arrivalDepature.isDeparture()
                            && arrivalDepature.getStopId().equals(stopId)
                            && arrivalDepature.getVehicleId().equals(vehicleId)
                            && (vehicleStatus.getTrip().getDirectionId() == null
                                    || vehicleStatus
                                            .getTrip()
                                            .getDirectionId()
                                            .equals(arrivalDepature.getDirectionId()))) {
                        // This the arrival of this vehicle now the next arrival in the list will be
                        // the previous vehicle (The arrival of the vehicle ahead).
                        lastStopArrivalIndex = i;
                    }
                    if (lastStopArrivalIndex > -1
                            && arrivalDepature.isDeparture()
                            && arrivalDepature.getStopId().equals(stopId)
                            && !arrivalDepature.getVehicleId().equals(vehicleId)
                            && (vehicleStatus.getTrip().getDirectionId() == null
                                    || vehicleStatus
                                            .getTrip()
                                            .getDirectionId()
                                            .equals(arrivalDepature.getDirectionId()))) {
                        previousVehicleArrivalIndex = i;
                    }
                }
                if (previousVehicleArrivalIndex != -1 && lastStopArrivalIndex != -1) {
                    long headwayTime = Math.abs(
                            stopList.get(lastStopArrivalIndex).getTime().getTime()
                                    - stopList.get(previousVehicleArrivalIndex).getTime()
                                    .getTime());

                    long expected = Math.abs(
                            dbConfig.getTrip(stopList.get(lastStopArrivalIndex).getTripId()).getStartTime() -
                                    dbConfig.getTrip(stopList.get(previousVehicleArrivalIndex).getTripId()).getStartTime()) *
                            1000L;
                    if (expected <= 0) return null;

                    Headway headway = new Headway(
                            dbConfig.getConfigRev(),
                            headwayTime, expected, (headwayTime - expected),
                            new Date(date),
                            vehicleId,
                            stopList.get(previousVehicleArrivalIndex).getVehicleId(),
                            stopId,
                            vehicleStatus.getTrip().getId(),
                            vehicleStatus.getTrip().getRouteId(),
                            new Date(stopList.get(lastStopArrivalIndex).getTime().getTime()),
                            new Date(stopList.get(previousVehicleArrivalIndex).getTime().getTime()));

                    // remove rubish data from departure sfrom t
                    if (Math.abs(headway.getCreationTime().getTime()
                                         - headway.getFirstDeparture().getTime())
                            > 1200000
                            || lastStopArrivalIndex > 5) {
                        headway = null;
                        vehicleStatus.setHeadway(null);
                        return null;
                    }
                    if (headway != null) {
                        if (vehicleStatus.getHeadway() == null
                                || !vehicleStatus.getHeadway().equals(headway)) {
                            vehicleStatus.setHeadway(headway);
                            setSystemVariance(headway);
                            return headway;
                        } else {
                            return null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Something went wrong when try to generate headway: {}", e.getMessage());
        }
        return null;
    }

    private void setSystemVariance(Headway headway) {
        List<Headway> headways = new ArrayList<>();

        int totalWithHeadway = 0;
        int totalVehicles = 0;
        boolean error = false;

        for (IpcVehicleComplete currentVehicle : vehicleDataCache.getVehicles()) {
            VehicleStatus vehicleStatus = vehicleStatusManager.getStatus(currentVehicle.getId());
            if (vehicleStatus.getHeadway() != null) {
                headways.add(vehicleStatus.getHeadway());
                totalWithHeadway++;
            }
            totalVehicles++;
        }
        // ONLY SET IF HAVE VALES FOR ALL VEHICLES ON ROUTE.
        if (vehicleDataCache.getVehicles().size() == headways.size()
                && totalVehicles == totalWithHeadway) {
            headway.setAverage(average(headways));
            headway.setVariance(variance(headways));
            headway.setCoefficientOfVariation(coefficientOfVariance(headways));
            headway.setNumVehicles(totalWithHeadway);
        } else {
            headway.setAverage(-1);
            headway.setVariance(-1);
            headway.setCoefficientOfVariation(-1);
            headway.setNumVehicles(totalWithHeadway);
        }
    }

    private double average(List<Headway> headways) {
        double total = 0;
        for (Headway headway : headways) {
            total += headway.getHeadway();
        }
        return total / headways.size();
    }

    private double variance(List<Headway> headways) {
        double topline = 0;
        double average = average(headways);
        for (Headway headway : headways) {
            topline += ((headway.getHeadway() - average) * (headway.getHeadway() - average));
        }
        return topline / headways.size();
    }

    private double coefficientOfVariance(List<Headway> headways) {
        double variance = variance(headways);
        double average = average(headways);

        return variance / (average * average);
    }
}
