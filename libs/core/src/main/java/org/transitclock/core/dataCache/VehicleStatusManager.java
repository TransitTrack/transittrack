/* (C)2023 */
package org.transitclock.core.dataCache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transitclock.core.VehicleStatus;
import org.transitclock.properties.CoreProperties;

/**
 * For keeping track of vehicle state. This is used by the main predictor code, not for RMI clients.
 * For RMI clients the VehicleDataCache is used. This way making the system threadsafe is simpler
 * since VehicleDataCache can handle thread safety completely independently.
 *
 * @author SkiBu Smith
 */
@Component
@RequiredArgsConstructor
public class VehicleStatusManager {
    private final CoreProperties coreProperties;

    // Keyed by vehicle ID. Need to use ConcurrentHashMap instead of HashMap
    // since getVehiclesState() returns values() of the map which can be
    // accessed while the map is being modified with new data via another
    // thread. Otherwise, could get a ConcurrentModificationException.
    private final Map<String, VehicleStatus> vehicleMap = new ConcurrentHashMap<>();


    /**
     * Returns vehicle state for the specified vehicle. Vehicle state is kept in a map. If
     * {@link VehicleStatus} not yet created for the vehicle then this method will create it. If there was no
     * {@link VehicleStatus} already created for the vehicle then it is created. This way this method never
     * returns null.
     *
     * <p>VehicleState is a large object with multiple collections as members. Since it might be
     * getting modified when there is a new AVL report when this method is called need to
     * synchronize on the returned {@link VehicleStatus} object if accessing any information that is not
     * atomic, such as the avlReportHistory.
     *
     * @param vehicleId
     * @return the {@link VehicleStatus} for the vehicle
     */
    public VehicleStatus getStatus(@NonNull String vehicleId) {
        return vehicleMap.computeIfAbsent(vehicleId, id -> new VehicleStatus(id, coreProperties));
    }

    /**
     * Returns VehicleState for all vehicles.
     *
     * @return Collection of {@link VehicleStatus} objects for all vehicles.
     */
    public Collection<VehicleStatus> getStatuses() {
        return vehicleMap.values();
    }
}
