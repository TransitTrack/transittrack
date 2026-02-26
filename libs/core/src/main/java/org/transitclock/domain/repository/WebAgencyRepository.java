package org.transitclock.domain.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.webstructs.WebAgency;
import org.transitclock.utils.IntervalTimer;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;

@Slf4j
public class WebAgencyRepository extends BaseRepository<WebAgency> {

    /**
     * Reads in WebAgency objects from the database and returns them as a map keyed on agencyId.
     *
     * @return
     *
     * @throws HibernateException
     */
    public static Map<String, WebAgency> getMapFromDb() throws HibernateException {
        IntervalTimer timer = new IntervalTimer();

        try (Session session = HibernateUtils.getSession()) {
            List<WebAgency> list = session
                    .createQuery("FROM WebAgency", WebAgency.class)
                    .list();
            Map<String, WebAgency> map = new HashMap<>();
            for (WebAgency webAgency : list) {
                map.put(webAgency.getAgencyId(), webAgency);
            }

            logger.info(
                    "Done reading WebAgencies from database. Took {} msec. They are {}",
                    timer.elapsedMsec(),
                    map.values());
            return map;
        }
        // Make sure that the session always gets closed, even if
        // exception occurs
    }

    /**
     * Stores this WebAgency object in the specified db.
     *
     * @param agency WebAgency object is stored in db.
     */
    public static void store(WebAgency agency) throws HibernateException {
        Transaction transaction = null;
        try (Session session = HibernateUtils.getSession()) {
            transaction = session.beginTransaction();
            session.merge(agency);
            transaction.commit();
        } catch (Exception e) {
            Objects.requireNonNull(transaction).rollback();
            logger.warn("There is already a configuration for provided web-agency.");
            throw new IllegalArgumentException("There is already a configuration for provided web-agency.");
        }
    }
}
