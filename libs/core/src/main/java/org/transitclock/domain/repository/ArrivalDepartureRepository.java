package org.transitclock.domain.repository;

import com.querydsl.jpa.impl.JPAQuery;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.ArrivalDeparture;
import org.transitclock.domain.structs.ArrivalDeparture.ArrivalsOrDepartures;
import org.transitclock.domain.structs.QArrivalDeparture;
import org.transitclock.utils.IntervalTimer;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Slf4j
public class ArrivalDepartureRepository extends BaseRepository<ArrivalDeparture> {
    /**
     * For querying large amount of data. With a Hibernate Iterator not all the data is read in at
     * once. This means that can iterate over a large dataset without running out of memory. But
     * this can be slow because when using iterate() an initial query is done to get all of Id
     * column data and then a separate query is done when iterating over each row. Doing an
     * individual query per row is of course quite time consuming. Better to use
     * getArrivalsDeparturesFromDb() with a fairly large batch size of ~50000.
     *
     * <p>Note that the session needs to be closed externally once done with the Iterator.
     */
    public static Iterator<ArrivalDeparture> getArrivalsDeparturesDbIterator(
            Session session, Date beginTime, Date endTime) throws HibernateException {
        // Create the query. Table name is case-sensitive and needs to be the
        // class name instead of the name of the db table.
        String hql = "FROM ArrivalDeparture WHERE time >= :beginDate AND time < :endDate";
        var query = session.createQuery(hql, ArrivalDeparture.class);

        // Set the parameters
        query.setParameter("beginDate", beginTime);
        query.setParameter("endDate", endTime);

        @SuppressWarnings("unchecked")
        Iterator<ArrivalDeparture> iterator = query.stream().iterator();
        return iterator;
    }

    /**
     * Read in arrivals and departures for a vehicle, over a time range.
     */
    public static List<ArrivalDeparture> getArrivalsDeparturesFromDb(Date beginTime, Date endTime, String vehicleId) {
        // Call in standard getArrivalsDeparturesFromDb() but pass in
        // sql clause
        return getArrivalsDeparturesFromDb(
                beginTime,
                endTime,
                "AND vehicleId='" + vehicleId + "'",
                0,
                0, // Don't use batching
                null); // Read both arrivals and departures
    }

    /**
     * Reads in arrivals and departures for a particular trip and service. Create session and uses
     * it
     */
    public static List<ArrivalDeparture> getArrivalsDeparturesFromDb(
            Date beginTime, Date endTime, String tripId, String serviceId) {
        Session session = HibernateUtils.getSession();

        return getArrivalsDeparturesFromDb(session, beginTime, endTime, tripId, serviceId);
    }

    /**
     * Reads in arrivals and departures for a particular trip and service. Uses session provided
     */
    public static List<ArrivalDeparture> getArrivalsDeparturesFromDb(
            Session session, Date beginTime, Date endTime, String tripId, String serviceId) {
        JPAQuery<ArrivalDeparture> query = new JPAQuery<>(session);
        var qentity = QArrivalDeparture.arrivalDeparture;

        query = query.from(qentity)
            .where(qentity.tripId.eq(tripId))
            .where(qentity.time.gt(beginTime))
            .where(qentity.time.lt(endTime));

        if (serviceId != null) {
            query.where(qentity.serviceId.eq(serviceId));
        }

        return query.fetch();
    }

    /**
     * Reads in arrivals and departures for a particular stopPathIndex of a trip between two dates.
     * Uses session provided
     */
    public static List<ArrivalDeparture> getArrivalsDeparturesFromDb(
            Session session, Date beginTime, Date endTime, String tripId, Integer stopPathIndex) {
        JPAQuery<ArrivalDeparture> query = new JPAQuery<>(session);
        var qentity = QArrivalDeparture.arrivalDeparture;

        query.select(qentity);
        if (tripId != null) {
            query.where(qentity.tripId.eq(tripId));

            if (stopPathIndex != null) {
                query.where(qentity.stopPathIndex.eq(stopPathIndex));
            }
        }

        query.where(qentity.time.gt(beginTime))
                .where(qentity.time.lt(endTime));

        return query.fetch();
    }

    /**
     * Reads the arrivals/departures for the timespan specified. All of the data is read in at once
     * so could present memory issue if reading in a very large amount of data. For that case
     * probably best to instead use getArrivalsDeparturesDb() where one specifies the firstResult
     * and maxResult parameters.
     */
    public static List<ArrivalDeparture> getArrivalsDeparturesFromDb(String projectId, Date beginTime, Date endTime) {
        IntervalTimer timer = new IntervalTimer();

        // Get the database session. This is supposed to be pretty lightweight
        Session session = HibernateUtils.getSession(projectId);

        // Create the query. Table name is case-sensitive and needs to be the
        // class name instead of the name of the db table.
        try (session) {
            var query = session
                    .createQuery("FROM ArrivalDeparture WHERE time >= :beginDate AND time < :endDate", ArrivalDeparture.class)
                    .setParameter("beginDate", beginTime)
                    .setParameter("endDate", endTime);
            List<ArrivalDeparture> arrivalsDeparatures = query.list();
            logger.debug("Getting arrival/departures from database took {} msec", timer.elapsedMsec());
            return arrivalsDeparatures;
        } catch (HibernateException e) {
            logger.error(e.getMessage(), e);
        }

        return null;
    }

    /**
     * Allows batch retrieval of data. This is likely the best way to read in large amounts of data.
     * Using getArrivalsDeparturesDbIterator() reads in only data as needed so good with respect to
     * memory usage but it does a separate query for each row. Reading in list of all data is quick
     * but can cause memory problems if reading in a very large amount of data. This method is a
     * good compromise because it only reads in a batch of data at a time so is not as memory
     * intensive yet it is quite fast. With a batch size of 50k found it to run in under 1/4 the
     * time as with the iterator method.
     *
     * @param dbName Name of the database to retrieve data from. If set to null then will use db
     *     name configured by Java property transitclock.db.dbName
     * @param beginTime
     * @param endTime
     * @param sqlClause The clause is added to the SQL for retrieving the arrival/departures. Useful
     *     for ordering the results. Can be null.
     * @param firstResult For when reading in batch of data at a time.
     * @param maxResults For when reading in batch of data at a time. If set to 0 then will read in
     *     all data at once.
     * @param arrivalOrDeparture Enumeration specifying whether to read in just arrivals or just
     *     departures. Set to null to read in both.
     * @return List<ArrivalDeparture> or null if there is an exception
     */
    public static List<ArrivalDeparture> getArrivalsDeparturesFromDb(
            Date beginTime,
            Date endTime,
            String sqlClause,
            final Integer firstResult,
            final Integer maxResults,
            ArrivalsOrDepartures arrivalOrDeparture) {
        // Get the database session. This is supposed to be pretty light weight
        try (Session session = HibernateUtils.getSession()) {
            String hql = "FROM ArrivalDeparture WHERE time between :beginDate AND :endDate";
            if (arrivalOrDeparture != null) {
                if (arrivalOrDeparture == ArrivalsOrDepartures.ARRIVALS) {
                    hql += " AND isArrival = true";
                } else {
                    hql += " AND isArrival = false";
                }
            }
            if (sqlClause != null) {
                hql += " " + sqlClause;
            }
            var query = session.createQuery(hql, ArrivalDeparture.class)
                    .setParameter("beginDate", beginTime)
                    .setParameter("endDate", endTime);

            // Only get a batch of data at a time if maxResults specified
            if (firstResult != null) {
                query.setFirstResult(firstResult);
            }
            if (maxResults != null && maxResults > 0) {
                query.setMaxResults(maxResults);
            }

            return query.list();
        } catch (HibernateException e) {
            logger.error(e.getMessage(), e);
        }

        return new ArrayList<>();
    }

    public static long getArrivalsDeparturesCountFromDb(
            String dbName, Date beginTime, Date endTime, ArrivalsOrDepartures arrivalOrDeparture) {

        // Get the database session. This is supposed to be pretty lightweight
        try (Session session = HibernateUtils.getSession(dbName)) {
            String hql = "select count(*) FROM ArrivalDeparture WHERE time >= :beginDate AND time < :endDate";
            if (arrivalOrDeparture != null) {
                if (arrivalOrDeparture == ArrivalsOrDepartures.ARRIVALS) {
                    hql += " AND isArrival = true";
                } else {
                    hql += " AND isArrival = false";
                }
            }

            var query = session.createQuery(hql, Long.class)
                    .setParameter("beginDate", beginTime)
                    .setParameter("endDate", endTime);

            return query.uniqueResult();
        } catch (HibernateException e) {
            logger.error(e.getMessage(), e);
        }
        return 0L;
    }
}
