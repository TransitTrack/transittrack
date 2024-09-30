/* (C)2023 */
package org.transitclock.api.data.siri;

import java.text.DateFormat;
import java.util.Date;

import org.transitclock.service.dto.IpcPrediction;
import org.transitclock.service.dto.IpcVehicleComplete;
import org.transitclock.utils.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * For SIRI MonitorCall element.
 *
 * @author SkiBu Smith
 */
public class SiriMonitoredCall {

    @JsonProperty("StopPointRef")
    private String stopPointRef;

    @JsonProperty("VisitNumber")
    private int visitNumber;

    // The arrival/departure time elements were found in
    // http://user47094.vs.easily.co.uk/siri/schema/1.4/examples/exs_stopMonitoring_response.xml
    // Scheduled time not currently available via IPC so not available here.
    @JsonProperty("AimedArrivalTime")
    String aimedArrivalTime;

    // Predicted arrival time
    @JsonProperty("ExpectedArrivalTime")
    String expectedArrivalTime;

    // Scheduled time not currently available via IPC so not available here.
    @JsonProperty("AimedDepartureTime")
    String aimedDepartureTime;

    // Predicted departure time
    @JsonProperty("ExpectedDepartureTime")
    String expectedDepartureTime;

    // NYC MTA extensions
    @JsonProperty("Extensions")
    private Extensions extensions;

    /**
     * The MTA Bus Time extensions to show distance of the vehicle from the stop
     */
    public static class Extensions {
        @JsonProperty("Distances")
        private Distances distances;


        public Extensions(IpcVehicleComplete ipcCompleteVehicle) {
            distances = new Distances(ipcCompleteVehicle);
        }
    }

    /**
     * The MTA Bus Time extensions to show distance of the vehicle from the stop
     */
    public static class Distances {
        // The distance of the stop from the beginning of the trip/route
        @JsonProperty("CallDistanceAlongRoute")
        private String callDistanceAlongRoute;

        // The distance from the vehicle to the stop along the route, in meters
        @JsonProperty("DistanceFromCall")
        private String distanceFromCall;

        public Distances(IpcVehicleComplete ipcCompleteVehicle) {
            callDistanceAlongRoute =
                    StringUtils.oneDigitFormat(ipcCompleteVehicle.getDistanceOfNextStopFromTripStart());

            distanceFromCall = StringUtils.oneDigitFormat(ipcCompleteVehicle.getDistanceToNextStop());
        }
    }

    /**
     * Constructs a MonitoredCall element.
     *
     * @param ipcCompleteVehicle
     * @param prediction         The prediction for when doing stop monitoring. When doing vehicle
     *                           monitoring should be set to null.
     * @param timeFormatter      For converting epoch time into a Siri time string
     */
    public SiriMonitoredCall(
            IpcVehicleComplete ipcCompleteVehicle, IpcPrediction prediction, DateFormat timeFormatter) {
        stopPointRef = ipcCompleteVehicle.getNextStopId();
        // Always using value of 1 for now
        visitNumber = 1;

        // Deal with the predictions if StopMonitoring query.
        // Don't have schedule time available so can't provide it.
        if (prediction != null) {
            if (prediction.isArrival()) {
                expectedArrivalTime = timeFormatter.format(new Date(prediction.getPredictionTime()));
            } else {
                expectedDepartureTime = timeFormatter.format(new Date(prediction.getPredictionTime()));
            }
        }

        // Deal with NYC MTA extensions
        extensions = new Extensions(ipcCompleteVehicle);
    }
}