/* (C)2023 */
package org.transitclock.api.data.gtfs;

import com.google.transit.realtime.GtfsRealtime.*;
import com.google.transit.realtime.GtfsRealtime.FeedHeader.Incrementality;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition.VehicleStopStatus;
import lombok.extern.slf4j.Slf4j;

import org.springframework.util.StringUtils;

import org.transitclock.api.utils.AgencyTimezoneCache;
import org.transitclock.properties.AvlProperties;
import org.transitclock.service.contract.VehiclesService;
import org.transitclock.service.dto.IpcVehicleConfig;
import org.transitclock.service.dto.IpcVehicleGtfsRealtime;
import org.transitclock.utils.Time;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

/**
 * For creating GTFS-realtime Vehicle feed. The data is obtained via RMI.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class GtfsRtVehicleFeed {

    private final VehiclesService vehiclesService;
    private final AvlProperties avlProperties;
    // For outputting date in GTFS-realtime format
    private SimpleDateFormat gtfsRealtimeDateFormatter = new SimpleDateFormat("yyyyMMdd");
    private SimpleDateFormat gtfsRealtimeTimeFormatter = new SimpleDateFormat("HH:mm:ss");

    public GtfsRtVehicleFeed(String agencyId, VehiclesService vehiclesService, AgencyTimezoneCache agencyTimezoneCache, AvlProperties avlProperties) {
        this.vehiclesService = vehiclesService;
        this.avlProperties = avlProperties;
        this.gtfsRealtimeDateFormatter.setTimeZone(agencyTimezoneCache.get(agencyId));
    }

    /**
     * Takes in IpcGtfsRealtimeVehicle and puts it into a GTFS-realtime VehiclePosition object.
     *
     * @param vehicleData
     *
     * @return the resulting VehiclePosition
     *
     * @throws ParseException
     */
    private VehiclePosition createVehiclePosition(IpcVehicleGtfsRealtime vehicleData) throws ParseException {
        // Create the parent VehiclePosition object that is returned.
        VehiclePosition.Builder vehiclePosition = VehiclePosition.newBuilder();

        // If there is route information then add it via the TripDescriptor
        if (vehicleData.getRouteId() != null && !vehicleData.getRouteId().isEmpty()) {
            String tripStartDateStr = gtfsRealtimeDateFormatter.format(new Date(vehicleData.getTripStartEpochTime()));
            TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder()
                    .setRouteId(vehicleData.getRouteId())
                    .setTripId(vehicleData.getTripId())
                    .setStartDate(tripStartDateStr);
            if (vehicleData.isCanceled()) {
                tripDescriptor.setScheduleRelationship(ScheduleRelationship.CANCELED);
                vehiclePosition.setOccupancyStatus(VehiclePosition.OccupancyStatus.NOT_BOARDABLE);
            }
            if (vehicleData.getFreqStartTime() > 0) {
                String tripStartTimeStr = gtfsRealtimeTimeFormatter.format(new Date(vehicleData.getFreqStartTime()));
                tripDescriptor.setStartTime(tripStartTimeStr);
            }

            // Set the relation between this trip and the static schedule. ADDED and CANCELED not
            // supported.
            if (vehicleData.isTripUnscheduled()) {
                // A trip that is running with no schedule associated to it -
                // this value is used to identify trips defined in GTFS frequencies.txt with
                // exact_times = 0
                tripDescriptor.setScheduleRelationship(TripDescriptor.ScheduleRelationship.UNSCHEDULED);
            } else {
                // Trip that is running in accordance with its GTFS schedule,
                // or is close enough to the scheduled trip to be associated with it.
                tripDescriptor.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED);
            }

            vehiclePosition.setTrip(tripDescriptor);
        }

        //  Set status if available
        if (!StringUtils.isEmpty(avlProperties.getOccupancyBaseUrl())) {
            if (vehicleData.isPredictable()) {
                final float FULLNESS = vehicleData.getAvl().getPassengerFullness();
                if (vehicleData.getAvl().getPassengerCount() < 0) {
                    vehiclePosition.setOccupancyStatus(VehiclePosition.OccupancyStatus.NO_DATA_AVAILABLE);
                } else if (FULLNESS == 0) {
                    vehiclePosition.setOccupancyStatus(VehiclePosition.OccupancyStatus.EMPTY);
                } else if (FULLNESS <= 49 && FULLNESS >= 0.01) {
                    vehiclePosition.setOccupancyStatus(VehiclePosition.OccupancyStatus.MANY_SEATS_AVAILABLE);
                } else if (FULLNESS <= 75 && FULLNESS >= 49.01) {
                    vehiclePosition.setOccupancyStatus(VehiclePosition.OccupancyStatus.FEW_SEATS_AVAILABLE);
                } else if (FULLNESS <= 99.99 && FULLNESS >= 75.01) {
                    vehiclePosition.setOccupancyStatus(VehiclePosition.OccupancyStatus.STANDING_ROOM_ONLY);
                } else if (FULLNESS >= 100) {
                    vehiclePosition.setOccupancyStatus(VehiclePosition.OccupancyStatus.FULL);
                }
            } else {
                vehiclePosition.setOccupancyStatus(VehiclePosition.OccupancyStatus.NO_DATA_AVAILABLE);
            }
        }

        // Add the VehicleDescriptor information
        VehicleDescriptor.Builder vehicleDescriptor =
                VehicleDescriptor.newBuilder().setId(vehicleData.getId());
        // License plate information is optional so only add it if not null
        if (vehicleData.getLicensePlate() != null) {
            vehicleDescriptor.setLicensePlate(vehicleData.getLicensePlate());
        }
        vehiclePosition.setVehicle(vehicleDescriptor);

        // Add the Position information
        Position.Builder position =
                Position.newBuilder().setLatitude(vehicleData.getLatitude()).setLongitude(vehicleData.getLongitude());
        // Heading and speed are optional so only add them if actually a
        // valid number.
        if (!Float.isNaN(vehicleData.getHeading())) {
            position.setBearing(vehicleData.getHeading());
        }
        if (!Float.isNaN(vehicleData.getSpeed())) {
            position.setSpeed(vehicleData.getSpeed());
        }
        vehiclePosition.setPosition(position);

        // Convert the GPS timestamp information to an epoch time as
        // number of milliseconds since 1970.
        long gpsTime = vehicleData.getGpsTime();
        vehiclePosition.setTimestamp(gpsTime / Time.MS_PER_SEC);

        // Set the stop_id if at a stop or going to a stop
        String stopId = vehicleData.getAtOrNextStopId();
        if (stopId != null) {
            vehiclePosition.setStopId(stopId);
        }

        // Set current_status part of vehiclePosition if vehicle is actually
        // predictable. If not predictable then the vehicle stop status will
        // not be included in feed since it is not stopped nor in transit to.
        if (vehicleData.isPredictable()) {
            VehicleStopStatus currentStatus =
                    vehicleData.isAtStop() ? VehicleStopStatus.STOPPED_AT : VehicleStopStatus.IN_TRANSIT_TO;
            vehiclePosition.setCurrentStatus(currentStatus);

            if (vehicleData.getAtOrNextGtfsStopSeq() != null) {
                vehiclePosition.setCurrentStopSequence(vehicleData.getAtOrNextGtfsStopSeq());
            }
        }

        // Return the results
        return vehiclePosition.build();
    }

    /**
     * Creates a GTFS-realtime message for the list of ApiVehicle passed in.
     *
     * @param vehicles the data to be put into the GTFS-realtime message
     *
     * @return the GTFS-realtime FeedMessage
     */
    private FeedMessage createMessage(Collection<IpcVehicleGtfsRealtime> vehicles) {
        FeedMessage.Builder message = FeedMessage.newBuilder();

        FeedHeader.Builder feedheader = FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("1.0")
                .setIncrementality(Incrementality.FULL_DATASET)
                .setTimestamp(System.currentTimeMillis() / Time.MS_PER_SEC);
        message.setHeader(feedheader);

        for (IpcVehicleGtfsRealtime vehicle : vehicles) {

            IpcVehicleGtfsRealtime newVehicle = new IpcVehicleGtfsRealtime(
                    vehicle.getBlockId(),
                    vehicle.getBlockAssignmentMethod(),
                    vehicle.getAvl(),
                    vehicle.getHeading(),
                    vehicle.getRouteId(),
                    vehicle.getRouteShortName(),
                    vehicle.getRouteName(),
                    vehicle.getTripId(),
                    vehicle.getTripPatternId(),
                    vehicle.isTripUnscheduled(),
                    vehicle.getDirectionId(),
                    vehicle.getHeadsign(),
                    vehicle.isPredictable(),
                    vehicle.isForSchedBasedPred(),
                    vehicle.getRealTimeSchedAdh(),
                    vehicle.isDelayed(),
                    vehicle.isLayover(),
                    vehicle.getLayoverDepartureTime(),
                    vehicle.getNextStopId(),
                    vehicle.getNextStopName(),
                    vehicle.getVehicleType(),
                    vehicle.getTripStartEpochTime(),
                    vehicle.isAtStop(),
                    vehicle.getAtOrNextStopId(),
                    vehicle.getAtOrNextGtfsStopSeq(),
                    vehicle.getFreqStartTime(),
                    vehicle.getHoldingTime(),
                    vehicle.getPredictedLatitude(),
                    vehicle.getPredictedLongitude(),
                    vehicle.isCanceled());
            FeedEntity.Builder vehiclePositionEntity = FeedEntity.newBuilder()
                    .setId(vehicle.getVehicleName() == null ? vehicle.getId() : vehicle.getVehicleName());

            try {
                // vehicle
                VehiclePosition vehiclePosition = createVehiclePosition(newVehicle);
                vehiclePositionEntity.setVehicle(vehiclePosition);
                message.addEntity(vehiclePositionEntity);
            } catch (Exception e) {
                logger.error("Error parsing vehicle data for vehicle={}", vehicle, e);
            }
        }

        return message.build();
    }

    /**
     * Returns collection of all vehicles for the project obtained via RMI. Returns null if there
     * was a problem getting the data via RMI
     *
     * @return Collection of Vehicle objects, or null if not available.
     */
    private Collection<IpcVehicleGtfsRealtime> getVehicles() {
        Collection<IpcVehicleGtfsRealtime> vehicles = vehiclesService.getGtfsRealtime();

        for (IpcVehicleGtfsRealtime ipc : vehicles) {
            Collection<IpcVehicleConfig> vehConfigs = vehiclesService.getVehicleConfigs();
            for (IpcVehicleConfig ipcVehicleConfig : vehConfigs) {
                if (ipcVehicleConfig.getId().equals(ipc.getId())) {
                    ipc.setVehicleName(ipcVehicleConfig.getName());
                    break;
                }
            }
        }
        return vehicles;
    }

    /**
     * Gets the Vehicle data from RMI and creates corresponding GTFS-RT vehicle feed.
     *
     * @return GTFS-RT FeedMessage for vehicle positions
     */
    public FeedMessage createMessage() {
        Collection<IpcVehicleGtfsRealtime> vehicles = getVehicles();
        return createMessage(vehicles);
    }
}
