/* (C)2023 */
package org.transitclock.api.data;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;
import org.transitclock.core.reports.RouteTripsStopAdherenceReport;

public class ApiRouteTripsStopAdherenceDate {

    @XmlAttribute
    private String date;

    @XmlElement(name = "trip")
    private List<ApiRouteTripExecution> trips;

    protected ApiRouteTripsStopAdherenceDate() {}

    public ApiRouteTripsStopAdherenceDate(RouteTripsStopAdherenceReport.DateGroup dateGroup) {
        this.date = dateGroup.getDate();
        trips = new ArrayList<>();
        for (RouteTripsStopAdherenceReport.TripExecution trip : dateGroup.getTrips()) {
            trips.add(new ApiRouteTripExecution(trip));
        }
    }
}
