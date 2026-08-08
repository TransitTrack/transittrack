/* (C)2023 */
package org.transitclock.api.data;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcVehicleToBlockConfig;

@Data
public class ApiVehicleToBlockResponse {

    @JsonProperty("vehicleToBlock")
    private List<ApiVehicleToBlockConfig> data;


    /**
     * For constructing a ApiVehicleToBlockConfigs object from a Collection of Vehicle objects.
     *
     * @param vehicles
     */
    public ApiVehicleToBlockResponse(Collection<IpcVehicleToBlockConfig> vehicles) {
        data = vehicles.stream()
                .map(ApiVehicleToBlockConfig::new)
                .toList();
    }
}
