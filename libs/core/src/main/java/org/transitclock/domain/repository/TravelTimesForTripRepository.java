package org.transitclock.domain.repository;

import com.querydsl.jpa.impl.JPAQuery;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.QTravelTimesForTrip;
import org.transitclock.domain.structs.TravelTimesForTrip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TravelTimesForTripRepository extends BaseRepository<TravelTimesForTrip> {
    /**
     * Deletes data from the TravelTimesForTrip and the
     * TravelTimesForTrip_to_TravelTimesForPath_jointable.
     *
     * @param session
     * @param configRev
     *
     * @return
     *
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        int totalRowsUpdated = 0;

        // Delete configRev data from TravelTimesForTrip_to_TravelTimesForPath_jointable.
        // This needs to work with at least mySQL and PostgreSQL but they are different.
        // This means that cannot use an INNER JOIN as part of the delete since the
        // syntax for inner joins is different for the two databases. Therefore need to
        // use the IN statement with a SELECT clause.
        int rowsUpdated = session
                .createNativeQuery("DELETE FROM travel_times_for_trip_to_travel_times_for_path WHERE for_trip_id IN (SELECT id FROM travel_times_for_trips WHERE config_rev=" + configRev + ")")
                .executeUpdate();

        logger.info(
                "Deleted {} rows from TravelTimesForTrip_to_TravelTimesForPath_joinTable for configRev={}",
                rowsUpdated,
                configRev);
        totalRowsUpdated += rowsUpdated;

        // Delete configRev data from TravelTimesForStopPaths
        rowsUpdated = session
                .createMutationQuery("DELETE FROM TravelTimesForStopPath WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();

        logger.info("Deleted {} rows from TravelTimesForStopPaths for " + "configRev={}", rowsUpdated, configRev);
        totalRowsUpdated += rowsUpdated;

        // Delete configRev data from TravelTimesForTrips
        rowsUpdated = session
                .createMutationQuery("DELETE FROM TravelTimesForTrip WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();

        logger.info("Deleted {} rows from TravelTimesForTrips for configRev={}", rowsUpdated, configRev);
        totalRowsUpdated += rowsUpdated;

        return totalRowsUpdated;
    }

    /**
     * Returns Map keyed by tripPatternId of Lists of TravelTimesForTrip. Since there are usually
     * multiple trips per trip pattern the Map contains a List of TravelTimesForTrip instead of just
     * a single one.
     *
     * @param session
     * @param travelTimesRev
     *
     * @return Map keyed by tripPatternId of Lists of TripPatterns
     *
     * @throws HibernateException
     */
    public static Map<String, List<TravelTimesForTrip>> getTravelTimesForTrips(Session session, int travelTimesRev)
            throws HibernateException {
        logger.info("Reading TravelTimesForTrips for travelTimesRev={} ...", travelTimesRev);

        JPAQuery<TravelTimesForTrip> query = new JPAQuery<>(session);
        var qentity = QTravelTimesForTrip.travelTimesForTrip;
        List<TravelTimesForTrip> allTravelTimes = query.from(qentity)
                .where(qentity.travelTimesRev.eq(travelTimesRev))
                .distinct()
                .fetch();

        logger.info("Putting travel times into map...");

        // Now create the map and return it
        Map<String, List<TravelTimesForTrip>> map = new HashMap<>();
        for (TravelTimesForTrip travelTimes : allTravelTimes) {
            // Get the List to add the travelTimes to
            String tripPatternId = travelTimes.getTripPatternId();
            List<TravelTimesForTrip> listForTripPattern = map.computeIfAbsent(tripPatternId, k -> new ArrayList<>());

            // Add the travelTimes to the List
            listForTripPattern.add(travelTimes);
        }

        logger.info("Done putting travel times into map.");

        // Return the map containing all the travel times
        return map;
    }
}
