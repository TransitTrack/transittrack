/* (C)2023 */
package org.transitclock.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.transitclock.domain.structs.Route;
import org.transitclock.domain.structs.Stop;
import org.transitclock.domain.structs.TripPattern;
import org.transitclock.gtfs.DbConfig;

/**
 * @author SkiBu Smith
 */
public class IpcDirection implements Serializable {

    private final String directionId;
    private final String directionTitle;
    private final Collection<IpcStop> stops;

    /**
     * Constructor. All IpcStops are marked as being a normal UiMode stop.
     *
     * @param dbRoute
     * @param directionId
     */
    public IpcDirection(Route dbRoute, DbConfig dbConfig, String directionId) {
        this.directionId = directionId;

        // Use the headsign name for the longest trip pattern for the
        // specified direction. Note: this isn't necessarily the best thing
        // to use but there is no human readable direction name specified in
        // GTFS.
        TripPattern longestTripPattern = dbRoute.getLongestTripPatternForDirection(dbConfig, directionId);
        this.directionTitle = "To " + longestTripPattern.getHeadsign();

        // Determine ordered list of stops
        this.stops = new ArrayList<IpcStop>();
        List<String> stopIds = dbRoute.getOrderedStopsByDirection(dbConfig).get(directionId);
        for (String stopId : stopIds) {
            Stop stop = dbConfig.getStop(stopId);
            this.stops.add(new IpcStop(stop, directionId));
        }
    }

    /**
     * Constructor for when already have list of IpcStops. Useful for when have already determined
     * whether an IpcStop is for UiMode or note.
     *
     * @param dbRoute
     * @param directionId
     * @param ipcStops
     */
    public IpcDirection(Route dbRoute, DbConfig dbConfig, String directionId, List<IpcStop> ipcStops) {
        this.directionId = directionId;

        // Use the headsign name for the longest trip pattern for the
        // specified direction. Note: this isn't necessarily the best thing
        // to use but there is no human readable direction name specified in
        // GTFS.
        TripPattern longestTripPattern = dbRoute.getLongestTripPatternForDirection(dbConfig, directionId);
        this.directionTitle = "To " + longestTripPattern.getHeadsign();
        this.stops = ipcStops;
    }

    @Override
    public String toString() {
        return "IpcDirection ["
                + "directionId="
                + directionId
                + ", directionTitle="
                + directionTitle
                + ", stops="
                + stops
                + "]";
    }

    public String getDirectionId() {
        return directionId;
    }

    public String getDirectionTitle() {
        return directionTitle;
    }

    public Collection<IpcStop> getStops() {
        return stops;
    }
}
