package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.Trip;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TripRepository extends BaseRepository<Trip> {
    /**
     * Returns map of Trip objects for the specified configRev. The map is keyed on the trip IDs.
     *
     * @param session
     * @param configRev
     * @return
     * @throws HibernateException
     */
    public static Map<String, Trip> getTrips(Session session, int configRev) throws HibernateException {
        List<Trip> tripsList = session.createQuery("FROM Trip WHERE configRev = :configRev", Trip.class)
                .setParameter("configRev", configRev)
                .list();

        Map<String, Trip> tripsMap = new HashMap<>();
        for (Trip trip : tripsList) {
            tripsMap.put(trip.getId(), trip);
        }
        return tripsMap;
    }

    /**
     * Returns specified Trip object for the specified configRev and tripId.
     *
     * @param session
     * @param configRev
     * @param tripId
     * @return
     * @throws HibernateException
     */
    public static Trip getTrip(Session session, int configRev, String tripId) throws HibernateException {
        return session
                .createQuery("FROM Trip t LEFT JOIN fetch t.scheduledTimesList LEFT JOIN FETCH t.travelTimes WHERE t.configRev = :configRev AND t.tripId = :tripId", Trip.class)
                .setParameter("configRev", configRev)
                .setParameter("tripId", tripId)
                .uniqueResult();
    }

    /**
     * Returns list of Trip objects for the specified configRev and tripShortName. There can be
     * multiple trips for a tripShortName since can have multiple service IDs configured. Therefore
     * a list must be returned.
     *
     * @param session
     * @param configRev
     * @param tripShortName
     * @return list of trips for specified configRev and tripShortName
     * @throws HibernateException
     */
    public static List<Trip> getTripByShortName(Session session, int configRev, String tripShortName) throws HibernateException {
        return session
                .createQuery("FROM Trip t left join fetch t.scheduledTimesList left join fetch t.travelTimes WHERE t.configRev = :configRev AND t.tripShortName = :tripShortName", Trip.class)
                .setParameter("configRev", configRev)
                .setParameter("tripShortName", tripShortName)
                .list();
    }

    /**
     * Deletes rev from the Trips table
     *
     * @param session
     * @param configRev
     * @return Number of rows deleted
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        return session.createMutationQuery("DELETE FROM Trip WHERE configRev= :configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
    }

    /**
     * Query how many travel times for trips entries exist for a given travelTimesRev. Used for
     * metrics.
     *
     * @param session
     * @param travelTimesRev
     * @return
     */
    public static Long countTravelTimesForTrips(Session session, int travelTimesRev) {
        var result = session
                .createQuery("select count(*) from TravelTimesForTrip where travelTimesRev=:rev", Object.class)
                .setParameter("rev", travelTimesRev)
                .uniqueResult();
        Long count = null;
        try {
            Integer bcount;
            if (result instanceof BigInteger) {
                bcount = ((BigInteger) result).intValue();
            } else {
                bcount = (Integer) result;
            }

            if (bcount != null) {
                count = bcount.longValue();
            }
        } catch (HibernateException e) {
            logger.error("exception querying for metrics", e);
        }
        return count;
    }
}
