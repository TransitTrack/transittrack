/* (C)2023 */
package org.transitclock.api.data.siri;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.transitclock.service.dto.IpcPrediction;
import org.transitclock.service.dto.IpcPredictionsForRouteStopDest;
import org.transitclock.service.dto.IpcVehicleComplete;
import org.transitclock.utils.Time;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author SkiBu Smith
 */
@Data
public class SiriStopMonitoring {
    @JsonProperty
    private String version = "1.3";

    @JsonProperty
    private String xmlns = "http://www.siri.org.uk/siri";

    @JsonProperty("ServiceDelivery")
    private SiriServiceDelivery delivery;

    @JsonIgnore
    // Defines how times should be output in Siri
    private final DateFormat siriDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    // Defines how dates should be output in Siri
    @JsonIgnore
    private final DateFormat siriDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Simple sub-element so using internal class.
     */
    private static class SiriServiceDelivery {
        @JsonProperty("ResponseTimestamp")
        private String responseTimestamp;

        @JsonProperty("StopMonitoringDelivery ")
        private SiriStopMonitoringDelivery stopMonitoringDelivery;


        public SiriServiceDelivery(
                List<IpcPredictionsForRouteStopDest> preds,
                Collection<IpcVehicleComplete> vehicles,
                String agencyId,
                DateFormat timeFormatter,
                DateFormat dateFormatter) {
            responseTimestamp = timeFormatter.format(new Date(System.currentTimeMillis()));
            stopMonitoringDelivery =
                    new SiriStopMonitoringDelivery(preds, vehicles, agencyId, timeFormatter, dateFormatter);
        }
    }

    /**
     * Simple sub-element so using internal class.
     */
    private static class SiriStopMonitoringDelivery {
        // Required by SIRI spec
        @JsonProperty
        private String version = "1.3";

        // Required by SIRI spec
        @JsonProperty("ResponseTimestamp")
        private String responseTimestamp;

        // Required by SIRI spec
        @JsonProperty("ValidUntil")
        private String validUntil;

        // Contains prediction and vehicle info. One per prediction
        @JsonProperty("MonitoredStopVisit")
        private List<SiriMonitoredStopVisit> monitoredStopVisitList;


        public SiriStopMonitoringDelivery(
                List<IpcPredictionsForRouteStopDest> preds,
                Collection<IpcVehicleComplete> vehicles,
                String agencyId,
                DateFormat timeFormatter,
                DateFormat dateFormatter) {
            long currentTime = System.currentTimeMillis();
            responseTimestamp = timeFormatter.format(new Date(currentTime));
            validUntil = timeFormatter.format(new Date(currentTime + 2 * Time.MS_PER_MIN));

            // For each prediction create a MonitoredStopVisit
            monitoredStopVisitList = new ArrayList<SiriMonitoredStopVisit>();
            for (IpcPredictionsForRouteStopDest predForRouteStopDest : preds) {
                for (IpcPrediction pred : predForRouteStopDest.getPredictionsForRouteStop()) {
                    // Determine vehicle info associated with prediction
                    IpcVehicleComplete vehicle = getVehicle(pred, vehicles);

                    // Created the MonitoredStopVisit for the prediction
                    monitoredStopVisitList.add(
                            new SiriMonitoredStopVisit(vehicle, pred, agencyId, timeFormatter, dateFormatter));
                }
            }
        }

        /**
         * Determines IpcExtVehicle object for the specified prediction
         *
         * @param pred
         * @param vehicles
         *
         * @return
         */
        private IpcVehicleComplete getVehicle(IpcPrediction pred, Collection<IpcVehicleComplete> vehicles) {
            String vehicleId = pred.getVehicleId();
            for (IpcVehicleComplete vehicle : vehicles) {
                if (vehicle.getId().equals(vehicleId)) {
                    return vehicle;
                }
            }
            // Didn't find the vehicle so return null
            return null;
        }
    }

    /**
     * Simple sub-element so using internal class.
     */
    private static class SiriMonitoredStopVisit {
        // GPS time for vehicle
        @JsonProperty("RecordedAtTime")
        private String recordedAtTime;

        @JsonProperty("MonitoredVehicleJourney")
        SiriMonitoredVehicleJourney monitoredVehicleJourney;


        public SiriMonitoredStopVisit(
                IpcVehicleComplete ipcCompleteVehicle,
                IpcPrediction prediction,
                String agencyId,
                DateFormat timeFormatter,
                DateFormat dateFormatter) {
            recordedAtTime = timeFormatter.format(new Date(ipcCompleteVehicle.getGpsTime()));
            monitoredVehicleJourney = new SiriMonitoredVehicleJourney(
                    ipcCompleteVehicle, prediction, agencyId, timeFormatter, dateFormatter);
        }
    }


    public SiriStopMonitoring(
            List<IpcPredictionsForRouteStopDest> preds, Collection<IpcVehicleComplete> vehicles, String agencyId) {
        // Set the time zones for the date formatters
//        siriDateTimeFormat.setTimeZone(AgencyTimezoneCache.get(agencyId));
//        siriDateFormat.setTimeZone(AgencyTimezoneCache.get(agencyId));

        delivery = new SiriServiceDelivery(preds, vehicles, agencyId, siriDateTimeFormat, siriDateFormat);
    }
}
