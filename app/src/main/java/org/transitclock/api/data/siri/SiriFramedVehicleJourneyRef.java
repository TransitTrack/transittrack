/* (C)2023 */
package org.transitclock.api.data.siri;

import java.text.DateFormat;
import java.util.Date;

import org.transitclock.service.dto.IpcVehicle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes the trip
 *
 * @author SkiBu Smith
 */
public class SiriFramedVehicleJourneyRef {

    // The GTFS service date for the trip the vehicle is serving
    @JsonProperty("DataFrameRef")
    private String dataFrameRef;

    // Trip ID from GTFS
    @JsonProperty("DatedVehicleJourneyRef")
    private String datedVehicleJourneyRef;


    /**
     * Constructor
     *
     * @param vehicle
     * @param dateFormatter
     */
    public SiriFramedVehicleJourneyRef(IpcVehicle vehicle, DateFormat dateFormatter) {
        // FIXME Note: dataFrameRef is not correct. It should use
        // the service date, not the GPS time. When assignment spans
        // midnight this will be wrong. But of course this isn't too
        // important because if a client would actually want such info
        // they would want service ID, not the date. Sheesh.
        dataFrameRef = dateFormatter.format(new Date(vehicle.getGpsTime()));
        datedVehicleJourneyRef = vehicle.getTripId();
    }
}
