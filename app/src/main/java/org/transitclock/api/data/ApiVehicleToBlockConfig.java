/* (C)2023 */
package org.transitclock.api.data;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcVehicleToBlockConfig;

@Data
public class ApiVehicleToBlockConfig {

    @JsonProperty
    protected long id;

    @JsonProperty
    protected String vehicleId;

    @JsonProperty
    protected long validFrom;

    @JsonProperty
    protected long validTo;

    @JsonProperty
    protected long assignmentDate;

    @JsonProperty
    protected String tripId;

    @JsonProperty
    protected String blockId;


    public ApiVehicleToBlockConfig(IpcVehicleToBlockConfig vehicleToBlockConfig) {
        id = vehicleToBlockConfig.getId();
        vehicleId = vehicleToBlockConfig.getVehicleId();
        tripId = vehicleToBlockConfig.getTripId();
        blockId = vehicleToBlockConfig.getBlockId();
        validFrom = vehicleToBlockConfig.getValidFrom().getTime();
        validTo = vehicleToBlockConfig.getValidTo().getTime();
        assignmentDate = vehicleToBlockConfig.getAssignmentDate().getTime();
    }
}
