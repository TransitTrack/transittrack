/* (C)2023 */
package org.transitclock.api.data;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import org.transitclock.core.reports.RouteTripsStopAdherenceReport;

@XmlRootElement(name = "routeTripsStopAdherence")
public class ApiRouteTripsStopAdherence {

    @XmlElement(name = "date")
    private List<ApiRouteTripsStopAdherenceDate> dates;

    protected ApiRouteTripsStopAdherence() {}

    public ApiRouteTripsStopAdherence(RouteTripsStopAdherenceReport.Result result) {
        dates = new ArrayList<>();
        for (RouteTripsStopAdherenceReport.DateGroup dateGroup : result.getDates()) {
            dates.add(new ApiRouteTripsStopAdherenceDate(dateGroup));
        }
    }
}
