/* (C)2023 */
package org.transitclock.api.data;

import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;
import org.transitclock.core.reports.RouteTripsStopAdherenceReport;

public class ApiRouteTripExecution {

    @XmlElement
    private String tripId;

    @XmlElement
    private String vehicleId;

    @XmlElement
    private String vehicleName;

    @XmlElement
    private String blockId;

    @XmlElement
    private String directionId;

    @XmlElement(name = "stop")
    private List<ApiRouteTripStop> stops;

    protected ApiRouteTripExecution() {}

    public ApiRouteTripExecution(RouteTripsStopAdherenceReport.TripExecution trip) {
        this.tripId = trip.getTripId();
        this.vehicleId = trip.getVehicleId();
        this.vehicleName = trip.getVehicleName();
        this.blockId = trip.getBlockId();
        this.directionId = trip.getDirectionId();
        stops = new ArrayList<>();
        for (RouteTripsStopAdherenceReport.Stop stop : trip.getStops()) {
            stops.add(new ApiRouteTripStop(stop));
        }
    }
}
