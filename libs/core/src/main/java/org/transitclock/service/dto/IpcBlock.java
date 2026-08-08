/* (C)2023 */
package org.transitclock.service.dto;

import java.io.Serializable;
import java.util.List;

import org.transitclock.domain.structs.Block;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.utils.Time;

/**
 * Configuration information for a Block for IPC.
 *
 * @author SkiBu Smith
 */
public class IpcBlock implements Serializable {

    private final int configRev;
    private final String id;
    private final String serviceId;
    private final int startTime; // In seconds from midnight
    private final int endTime; // In seconds from midnight

    private final List<IpcTrip> trips;
    private final List<IpcRouteSummary> routeSummaries;

    public IpcBlock(Block dbBlock, DbConfig dbConfig) {
        configRev = dbBlock.getConfigRev();
        id = dbBlock.getId();
        serviceId = dbBlock.getServiceId();
        startTime = dbBlock.getStartTime();
        endTime = dbBlock.getEndTime();

        trips = dbConfig.getTrips(dbBlock).stream()
                .map(dbTrip -> new IpcTrip(dbTrip, dbConfig)).toList();

        routeSummaries = dbBlock.getRouteIds().stream()
                .map(dbConfig::getRouteById)
                .map(IpcRouteSummary::new)
                .toList();
    }

    @Override
    public String toString() {
        return "IpcBlock ["
                + "configRev="
                + configRev
                + ", id="
                + id
                + ", serviceId="
                + serviceId
                + ", startTime="
                + Time.timeOfDayStr(startTime)
                + ", endTime="
                + Time.timeOfDayStr(endTime)
                + ", trips="
                + trips
                + ", routeSummaries="
                + routeSummaries
                + "]";
    }

    public int getConfigRev() {
        return configRev;
    }

    public String getId() {
        return id;
    }

    public String getServiceId() {
        return serviceId;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public List<IpcTrip> getTrips() {
        return trips;
    }

    public List<IpcRouteSummary> getRouteSummaries() {
        return routeSummaries;
    }
}
