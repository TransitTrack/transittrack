/* (C)2023 */
package org.transitclock.core.travelTimes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.transitclock.domain.structs.HowSet;
import org.transitclock.domain.structs.Trip;
import org.transitclock.utils.Time;

/**
 * For keeping track of the historic data such that if no data is available for a trip then can find
 * closest trip. Keyed by stop path index.
 *
 * @author SkiBu Smith
 */
public class TravelTimeInfoMap {

    /*
     * TravelTimesByStop is to be used as the value in travelTimesByTripPattern.
     * By declaring it a class can make the code more readable.
     */
    @SuppressWarnings("serial")
    private static class TravelTimesByStopMap extends HashMap<Integer, List<TravelTimeInfo>> {}

    // For keeping track of the historic data such that if no data is
    // available for a trip then can find closest trip. Keyed by trip pattern
    // ID.
    private final Map<String, TravelTimesByStopMap> travelTimesByTripPatternMap = new HashMap<String, TravelTimesByStopMap>();


    /**
     * Adds the new TravelTimeInfo object to the static travelTimesByTripPattern map so that can
     * find best match for trip for when there is no data for the actual trip.
     *
     * @param travelTimeInfo
     */
    public void add(TravelTimeInfo travelTimeInfo) {
        String tripPatternId = travelTimeInfo.getTrip().getTripPattern().getId();
        int stopPathIndex = travelTimeInfo.getStopPathIndex();

        // Get the map of travel times by stop map. If haven't
        // created the map for the trip pattern ID yet then do so now.
        travelTimesByTripPatternMap
                .computeIfAbsent(tripPatternId, k -> new TravelTimesByStopMap())
                .computeIfAbsent(stopPathIndex, k -> new ArrayList<>())
                .add(travelTimeInfo);
    }

    /**
     * Returns true if there is at least some historic data for the specified trip pattern.
     *
     * @param tripPatternId Trip pattern to see if there is data for
     * @return True if there is historic data
     */
    public boolean dataExists(String tripPatternId) {
        TravelTimesByStopMap travelTimesByStopMap = travelTimesByTripPatternMap.get(tripPatternId);
        return travelTimesByStopMap != null;
    }

    /**
     * Returns the list, containing one TravelTimeInfo for each vehicle that traveled on a trip.
     *
     * @param tripPatternId
     * @param stopPathIndex
     * @return List of TravelTimeInfo data obtained from historic info. Returns null if no data
     *     available for the trip pattern/stop.
     */
    private List<TravelTimeInfo> getTravelTimeInfos(String tripPatternId, int stopPathIndex) {
        TravelTimesByStopMap travelTimesByStopMap = travelTimesByTripPatternMap.get(tripPatternId);
        if (travelTimesByStopMap == null) return null;

        return travelTimesByStopMap.get(stopPathIndex);
    }

    /**
     * For when don't have historic data for a trip. This method first determines the trip with
     * historic data for the same service ID that has the nearest time. If no historic data for a
     * trip with the same service ID then will look at trips with other service IDs.
     *
     * @param trip The Trip that needs travel time info
     * @param stopPathIndex The stop in the trip that needs travel time info
     * @return The best match for the trip or null if there was no historic info for the
     *     tripPatternId/stopPathIndex.
     */
    public TravelTimeInfoWithHowSet getBestMatch(Trip trip, int stopPathIndex) {
        // Get the list of historic travel times for the specified
        // trip pattern.
        String tripPatternId = trip.getTripPattern().getId();
        List<TravelTimeInfo> timesForTripPatternAndStop = getTravelTimeInfos(tripPatternId, stopPathIndex);

        // If no historic data at all for this tripPatternId/stopPathIndex
        // then return null.
        if (timesForTripPatternAndStop == null) return null;

        // Go through times and find best one, if there are any. First go
        // for same service Id
        int bestDifference = Integer.MAX_VALUE;
        TravelTimeInfo bestMatch = null;
        HowSet howSet = null;
        for (TravelTimeInfo travelTimeInfo : timesForTripPatternAndStop) {
            Trip thisTrip = travelTimeInfo.getTrip();
            // If for desired service ID...
            if (thisTrip.getServiceId().equals(trip.getServiceId())) {
                int timeDiffOfTripSchedTime = Time.getTimeDifference(thisTrip.getStartTime(), trip.getStartTime());
                if (timeDiffOfTripSchedTime < bestDifference) {
                    bestDifference = timeDiffOfTripSchedTime;
                    bestMatch = travelTimeInfo;
                    if (timeDiffOfTripSchedTime == 0) {
                        howSet = HowSet.AVL;

                        // Since found perfect match (same exact trip) don't
                        // need to look at the other travel times for the
                        // trip pattern, so continue.
                        continue;
                    } else {
                        // Found a reasonable match, but for another trip.
                        // Remember this by setting howSet to TRIP.
                        howSet = HowSet.TRIP;
                    }
                }
            }
        }

        // If didn't find match for the same service class then go for other
        // service classes
        if (bestMatch == null) {
            for (TravelTimeInfo travelTimeInfo : timesForTripPatternAndStop) {
                Trip thisTrip = travelTimeInfo.getTrip();
                int timeDiffOfTripSchedTime = Time.getTimeDifference(thisTrip.getStartTime(), trip.getStartTime());
                if (timeDiffOfTripSchedTime < bestDifference) {
                    bestDifference = timeDiffOfTripSchedTime;
                    bestMatch = travelTimeInfo;
                    howSet = HowSet.SERVC;
                }
            }
        }

        // Return the best match. Can be null.
        if (bestMatch != null) {
            return new TravelTimeInfoWithHowSet(bestMatch, howSet);
        }

        return null;
    }
}
