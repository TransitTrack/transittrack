package org.transitclock.gtfs;

public class StopPathHelper {
    /**
     * For consistently naming the path Id. It is based on the current stop ID and the previous stop
     * Id. If previousStopId is null then will return "to_" + stopId. If not null will return
     * previousStopId + "_to_" + stopId.
     *
     * @param previousStopId
     * @param stopId
     * @return
     */
    public static String determinePathId(String previousStopId, String stopId) {
        if (previousStopId == null) {
            return "to_" + stopId;
        } else {
            return previousStopId + "_to_" + stopId;
        }
    }
}
