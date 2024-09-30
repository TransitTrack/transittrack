/* (C)2023 */
package org.transitclock.api.data.siri;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.transitclock.service.dto.IpcVehicleComplete;
import org.transitclock.utils.Time;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author SkiBu Smith
 */
@Data
public class SiriVehiclesMonitoring {
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

        @JsonProperty("VehicleMonitoringDelivery ")
        private SiriVehicleMonitoringDelivery vehicleMonitoringDelivery;


        public SiriServiceDelivery(
                Collection<IpcVehicleComplete> vehicles,
                String agencyId,
                DateFormat timeFormatter,
                DateFormat dateFormatter) {
            responseTimestamp = timeFormatter.format(new Date(System.currentTimeMillis()));
            vehicleMonitoringDelivery =
                    new SiriVehicleMonitoringDelivery(vehicles, agencyId, timeFormatter, dateFormatter);
        }
    }

    /**
     * Simple sub-element so using internal class.
     */
    private static class SiriVehicleMonitoringDelivery {
        // Required by SIRI spec
        @JsonProperty
        private String version = "1.3";

        // Required by SIRI spec
        @JsonProperty("ResponseTimestamp")
        private String responseTimestamp;

        // Required by SIRI spec
        @JsonProperty("ValidUntil")
        private String validUntil;

        @JsonProperty("VehicleActivity")
        private List<SiriVehicleActivity> vehicleActivityList;


        public SiriVehicleMonitoringDelivery(
                Collection<IpcVehicleComplete> vehicles,
                String agencyId,
                DateFormat timeFormatter,
                DateFormat dateFormatter) {
            long currentTime = System.currentTimeMillis();
            responseTimestamp = timeFormatter.format(new Date(currentTime));
            validUntil = timeFormatter.format(new Date(currentTime + 2 * Time.MS_PER_MIN));

            vehicleActivityList = new ArrayList<SiriVehicleActivity>();
            for (IpcVehicleComplete vehicle : vehicles) {
                vehicleActivityList.add(new SiriVehicleActivity(vehicle, agencyId, timeFormatter, dateFormatter));
            }
        }
    }

    /**
     * Simple sub-element so using internal class.
     */
    private static class SiriVehicleActivity {
        // GPS time for vehicle
        @JsonProperty("RecordedAtTime")
        private String recordedAtTime;

        // Addition SIRI MonitoredVehicleJourney element
        @JsonProperty("MonitoredVehicleJourney")
        private SiriMonitoredVehicleJourney monitoredVehicleJourney;


        public SiriVehicleActivity(
                IpcVehicleComplete ipcCompleteVehicle,
                String agencyId,
                DateFormat timeFormatter,
                DateFormat dateFormatter) {
            recordedAtTime = timeFormatter.format(new Date(ipcCompleteVehicle.getGpsTime()));
            monitoredVehicleJourney =
                    new SiriMonitoredVehicleJourney(ipcCompleteVehicle, null, agencyId, timeFormatter, dateFormatter);
        }
    }

    public SiriVehiclesMonitoring(Collection<IpcVehicleComplete> vehicles, String agencyId) {
        // Set the time zones for the date formatters
//        siriDateTimeFormat.setTimeZone(AgencyTimezoneCache.get(agencyId));
//        siriDateFormat.setTimeZone(AgencyTimezoneCache.get(agencyId));

        delivery = new SiriServiceDelivery(vehicles, agencyId, siriDateTimeFormat, siriDateFormat);
    }
}
