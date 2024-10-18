package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import org.transitclock.domain.structs.TravelTimesForStopPath;

import java.util.List;

@Slf4j
public class TravelTimesForStopPathRepository extends BaseRepository<TravelTimesForStopPath> {
    /**
     * Reads in all the travel times for the specified rev
     *
     * @param sessionFactory
     * @param configRev
     * @return
     */
    public static List<TravelTimesForStopPath> getTravelTimes(SessionFactory sessionFactory, int configRev) {
        // Sessions are not threadsafe so need to create a new one each time.
        // They are supposed to be lightweight so this should be OK.
        Session session = sessionFactory.openSession();

        // Create the query. Table name is case-sensitive!
        try (session) {
            return session
                    .createQuery("FROM TravelTimesForStopPath WHERE configRev=:configRev ", TravelTimesForStopPath.class)
                    .setParameter("configRev", configRev)
                    .list();
        } catch (HibernateException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }
}
