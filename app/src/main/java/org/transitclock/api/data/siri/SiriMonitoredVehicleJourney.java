/* (C)2023 */
package org.transitclock.api.data.siri;

import java.text.DateFormat;
import java.util.Date;

import org.transitclock.service.dto.IpcPrediction;
import org.transitclock.service.dto.IpcVehicleComplete;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * For SIRI MonitoredVehicleJourney element
 *
 * @author SkiBu Smith
 */
public class SiriMonitoredVehicleJourney {
    // Vehicle Id
    @JsonProperty("VehicleRef")
    private String vehicleRef;

    // Location of vehicle
    @JsonProperty("VehicleLocation")
    private SiriLocation vehicleLocation;

    // Vehicle bearing: 0 is East, increments counter-clockwise.
    // This of course is different from heading, where 0 is north
    // and it goes clockwise.
    @JsonProperty("Bearing")
    private String bearingStr;

    // Block ID
    @JsonProperty("BlockRef")
    private String blockRef;

    // The route name
    @JsonProperty("LineRef")
    private String lineRef;

    // The GTFS direction
    @JsonProperty("DirectionRef")
    private String directionRef;

    // Describes the trip
    @JsonProperty("FramedVehicleJourneyRef")
    private SiriFramedVehicleJourneyRef framedVehicleJourneyRef;

    // Name of route. Using short name since that is available and is
    // more relevant.
    @JsonProperty("PublishedLineName")
    private String publishedLineName;

    // Name of agency
    @JsonProperty("OperatorRef")
    private String operatorRef;

    @JsonProperty("OriginRef")
    private String originRef;

    @JsonProperty("DestinationRef")
    private String destinationRef;

    @JsonProperty("DestinationName")
    private String destinationName;

    @JsonProperty("OriginAimedDepartureTime")
    private String originAimedDepartureTime;

    // Whether vehicle tracked
    @JsonProperty("Monitored")
    private String monitored;

    // Indicator of whether the bus is making progress (i.e. moving, generally)
    // or not (with value noProgress).
    @JsonProperty("ProgressRate")
    private String progressRate;

    @JsonProperty("ProgressStatus")
    private String progressStatus;

    @JsonProperty("MonitoredCall")
    private SiriMonitoredCall monitoredCall;

    @JsonProperty("OnwardCalls")
    private String onwardCalls;


    /**
     * Constructs that massive MonitoredVehicleJourney element.
     *
     * @param ipcCompleteVehicle
     * @param prediction         For when doing stop monitoring. If doing vehicle monitoring then should be
     *                           set to null.
     * @param agencyId
     * @param timeFormatter      For converting epoch time into a Siri time string
     * @param dateFormatter      For converting epoch time into a Siri date string
     */
    public SiriMonitoredVehicleJourney(
            IpcVehicleComplete ipcCompleteVehicle,
            IpcPrediction prediction,
            String agencyId,
            DateFormat timeFormatter,
            DateFormat dateFormatter) {
        vehicleRef = ipcCompleteVehicle.getId();
        vehicleLocation = new SiriLocation(ipcCompleteVehicle.getLatitude(), ipcCompleteVehicle.getLongitude());
        double bearing = 90 - ipcCompleteVehicle.getHeading();
        if (bearing < 0) {
            bearing += 360.0;
        }
        bearingStr = Double.toString(bearing);
        blockRef = ipcCompleteVehicle.getBlockId();
        lineRef = ipcCompleteVehicle.getRouteShortName();
        directionRef = ipcCompleteVehicle.getDirectionId();
        framedVehicleJourneyRef = new SiriFramedVehicleJourneyRef(ipcCompleteVehicle, dateFormatter);
        publishedLineName = ipcCompleteVehicle.getRouteName();
        operatorRef = agencyId;
        originRef = ipcCompleteVehicle.getOriginStopId();
        destinationRef = ipcCompleteVehicle.getDestinationId();
        destinationName = ipcCompleteVehicle.getHeadsign();
        originAimedDepartureTime = timeFormatter.format(new Date(ipcCompleteVehicle.getTripStartEpochTime()));
        monitored = "true";
        progressRate = "normalProgress";
        progressStatus = ipcCompleteVehicle.isLayover() ? "true" : null;

        monitoredCall = new SiriMonitoredCall(ipcCompleteVehicle, prediction, timeFormatter);

        // Not currently implemented but outputting it for completeness
        onwardCalls = "";
    }
}
