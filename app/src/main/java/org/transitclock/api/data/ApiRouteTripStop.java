/* (C)2023 */
package org.transitclock.api.data;

import jakarta.xml.bind.annotation.XmlElement;
import org.transitclock.core.reports.RouteTripsStopAdherenceReport;

public class ApiRouteTripStop {

    @XmlElement
    private String stopId;

    @XmlElement
    private Integer stopCode;

    @XmlElement
    private String stopName;

    @XmlElement
    private Integer stopSequence;

    @XmlElement
    private Integer gtfsStopSeq;

    @XmlElement
    private String vehicleId;

    @XmlElement
    private String vehicleName;

    @XmlElement
    private String actualArrival;

    @XmlElement
    private String actualDeparture;

    @XmlElement
    private String scheduledArrival;

    @XmlElement
    private String scheduledDeparture;

    @XmlElement
    private String deviation;

    @XmlElement
    private String directionId;

    @XmlElement
    private boolean isTimepoint;

    protected ApiRouteTripStop() {}

    public ApiRouteTripStop(RouteTripsStopAdherenceReport.Stop stop) {
        this.stopId = stop.getStopId();
        this.stopCode = stop.getStopCode();
        this.stopName = stop.getStopName();
        this.stopSequence = stop.getStopSequence();
        this.gtfsStopSeq = stop.getGtfsStopSeq();
        this.vehicleId = stop.getVehicleId();
        this.vehicleName = stop.getVehicleName();
        this.actualArrival = formatTimestamp(stop.getActualArrival());
        this.actualDeparture = formatTimestamp(stop.getActualDeparture());
        this.scheduledArrival = formatTimestamp(stop.getScheduledArrival());
        this.scheduledDeparture = formatTimestamp(stop.getScheduledDeparture());
        this.deviation = stop.getDeviation();
        this.directionId = stop.getDirectionId();
        this.isTimepoint = stop.isTimepoint();
    }

    private static String formatTimestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toString();
    }
}
