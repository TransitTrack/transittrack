/* (C)2023 */
package org.transitclock.api.data;

import java.util.Date;

import org.transitclock.service.dto.IpcVehicleToBlockConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ApiVehicleToBlockConfig {

    @JsonProperty
    protected long id;

    @JsonProperty
    protected String vehicleId;

    @JsonProperty
    protected Date validFrom;

    @JsonProperty
    protected Date validTo;

    @JsonProperty
    protected Date assignmentDate;

    @JsonProperty
    protected String tripId;

    @JsonProperty
    protected String blockId;


    public ApiVehicleToBlockConfig(IpcVehicleToBlockConfig vehicleToBlockConfig) {
        id = vehicleToBlockConfig.getId();
        vehicleId = vehicleToBlockConfig.getVehicleId();
        tripId = vehicleToBlockConfig.getTripId();
        blockId = vehicleToBlockConfig.getBlockId();
        validFrom = vehicleToBlockConfig.getValidFrom();
        validTo = vehicleToBlockConfig.getValidTo();
        assignmentDate = vehicleToBlockConfig.getAssignmentDate();
    }
}
