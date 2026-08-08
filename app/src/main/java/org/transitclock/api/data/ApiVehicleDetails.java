/* (C)2023 */
package org.transitclock.api.data;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.transitclock.api.resources.TransitimeApi.UiMode;
import org.transitclock.core.TemporalDifference;
import org.transitclock.core.avl.assigner.BlockAssignmentMethod;
import org.transitclock.properties.HoldingProperties;
import org.transitclock.service.dto.IpcVehicle;
import org.transitclock.service.dto.IpcVehicleComplete;
import org.transitclock.utils.Time;

/**
 * Contains data for a single vehicle with additional info that is meant more for management than
 * for passengers.
 *
 * @author SkiBu Smith
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
public class ApiVehicleDetails extends ApiVehicleAbstract {

    @JsonProperty
    private String routeName;

    @JsonProperty
    private String vehicleName;

    // Note: needs to be Integer instead of an int because it can be null
    // (for vehicles that are not predictable)
    @JsonProperty("schAdh")
    private Integer scheduleAdherence;

    @JsonProperty("schAdhStr")
    private String scheduleAdherenceStr;

    @JsonProperty("block")
    private String blockId;

    @JsonProperty("blockMthd")
    private BlockAssignmentMethod blockAssignmentMethod;

    @JsonProperty("trip")
    private String tripId;

    @JsonProperty("tripPattern")
    private String tripPatternId;

    @JsonProperty("delayed")
    private Boolean isDelayed;

    @JsonProperty("layover")
    private Boolean isLayover;

    @JsonProperty
    private Long layoverDepTime;

    @JsonProperty
    private String layoverDepTimeStr;

    @JsonProperty
    private String nextStopId;

    @JsonProperty
    private String nextStopName;

    @JsonProperty("driver")
    private String driverId;

    @JsonProperty
    private Boolean isScheduledService;

    @JsonProperty
    private Long freqStartTime;

    @JsonProperty
    private Boolean isAtStop;

    @JsonProperty
    private ApiHoldingTime holdingTime;

    @JsonProperty
    private double distanceAlongTrip;

    @JsonProperty
    private String licensePlate;

    @JsonProperty
    private String fullness;

    @JsonProperty
    private boolean isCanceled;

    @JsonProperty
    private double headway;

    @JsonProperty
    private double expectedHeadway;

    @JsonProperty
    private double headwayDeviation;

    @JsonProperty
    private String headwayDeviationCategory;
    /**
     * Takes a Vehicle object for client/server communication and constructs a ApiVehicle object for
     * the API.
     *
     * @param vehicle
     * @param timeForAgency So can output times in proper timezone
     * @param uiType Optional parameter. If should be labeled as "minor" in output for UI. Default
     *     is UiMode.NORMAL.
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    public ApiVehicleDetails(IpcVehicle vehicle, Time timeForAgency, UiMode... uiType) {
        super(vehicle, uiType.length > 0 ? uiType[0] : UiMode.NORMAL);

        routeName = vehicle.getRouteName();
        vehicleName = vehicle.getVehicleName();
        scheduleAdherence = Optional.ofNullable(vehicle.getRealTimeSchedAdh())
                .map(TemporalDifference::getTemporalDifference)
                .orElse(null);
        scheduleAdherenceStr = Optional.ofNullable(vehicle.getRealTimeSchedAdh())
                .map(Objects::toString)
                .orElse(null);
        blockId = vehicle.getBlockId();
        blockAssignmentMethod = vehicle.getBlockAssignmentMethod();
        tripId = vehicle.getTripId();
        tripPatternId = vehicle.getTripPatternId();
        isDelayed = vehicle.isDelayed();
        isLayover = vehicle.isLayover();
        layoverDepTime = vehicle.isLayover() ? vehicle.getLayoverDepartureTime() / Time.MS_PER_SEC : null;

        layoverDepTimeStr =
                vehicle.isLayover() ? timeForAgency.timeStrForTimezone(vehicle.getLayoverDepartureTime()) : null;

        nextStopId = vehicle.getNextStopId() != null ? vehicle.getNextStopId() : null;
        nextStopName = vehicle.getNextStopName() != null ? vehicle.getNextStopName() : null;
        driverId = vehicle.getAvl().getDriverId();
        licensePlate = vehicle.getLicensePlate();
        isCanceled = false;
        headway = -1;
        headwayDeviationCategory = "UNAVAILABLE";
        if (vehicle instanceof IpcVehicleComplete && tripId != null) {
            distanceAlongTrip = ((IpcVehicleComplete) vehicle).getDistanceAlongTrip();
            isCanceled = ((IpcVehicleComplete) vehicle).isCanceled();
            headway = vehicle.isLayover() ? -1 : ((IpcVehicleComplete) vehicle).getHeadway().getHeadway();
            expectedHeadway = vehicle.isLayover() ? -1 : ((IpcVehicleComplete) vehicle).getHeadway().getExpected();
            headwayDeviation = vehicle.isLayover() ? -1 : ((IpcVehicleComplete) vehicle).getHeadway().getDeviation();
            headwayDeviationCategory = getHeadwayDeviationCategory(((IpcVehicleComplete) vehicle).getHeadway().getDeviation());
        }
        isScheduledService = vehicle.getFreqStartTime() <= 0;
        if (!isScheduledService) {
            freqStartTime = vehicle.getFreqStartTime();
        } else {
            freqStartTime = null;
        }

        this.isAtStop = vehicle.isAtStop();

        this.holdingTime = Optional.ofNullable(vehicle.getHoldingTime())
                .map(ApiHoldingTime::new)
                .orElse(null);

        this.fullness = convertFullness(vehicle);
    }

    private String convertFullness(IpcVehicle vehicle) {
            if (vehicle.isPredictable()) {
                final float FULLNESS = vehicle.getAvl().getPassengerFullness();
                if (vehicle.getAvl().getPassengerCount() < 0) return "NO_DATA_AVAILABLE";
                else if (FULLNESS == 0) return "EMPTY";
                else if (FULLNESS <= 49 && FULLNESS >= 0.01) return "MANY_SEATS_AVAILABLE";
                else if (FULLNESS <= 75 && FULLNESS >= 49.01) return "FEW_SEATS_AVAILABLE";
                else if (FULLNESS <= 99.99 && FULLNESS >= 75.01) return "STANDING_ROOM_ONLY";
                else if (FULLNESS >= 100) return "FULL";
            } else return "NO_DATA_AVAILABLE";

        return null;
    }

    private String getHeadwayDeviationCategory(double headwayDeviation) {
        HoldingProperties holdingProperties = new HoldingProperties();
        if (headway != -1 && headwayDeviation > holdingProperties.getHeadwayExpectedMaxLimitInSecs() * 1000) {
            return "GAPPED";
        } else if (headway != -1 && headwayDeviation < holdingProperties.getHeadwayExpectedMinLimitInSecs() * -1000) {
            return "BUNCHED";
        } else if (headway == -1 || expectedHeadway <= 0) {
            return "UNAVAILABLE";
        } else
            return "EXPECTED";
    }
}
