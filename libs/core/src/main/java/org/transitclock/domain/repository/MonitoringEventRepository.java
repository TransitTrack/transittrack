package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.MonitoringEvent;

import java.util.Date;
import java.util.List;

@Slf4j
public class MonitoringEventRepository extends BaseRepository<MonitoringEvent> {
    /**
     * Reads in all MonitoringEvents from the database that were between the beginTime and endTime.
     *
     * @param agencyId Which project getting data for
     * @param beginTime Specifies time range for query
     * @param endTime Specifies time range for query
     * @param sqlClause Optional. Can specify an SQL clause to winnow down the data, such as "AND
     *     routeId='71'".
     * @return
     */
    public static List<MonitoringEvent> getMonitoringEvents(String agencyId, Date beginTime, Date endTime, String sqlClause) {
        // Create the query. Table name is case sensitive and needs to be the
        // class name instead of the name of the db table.
        String hql = "FROM MonitoringEvent WHERE time >= :beginDate AND time < :endDate";
        if (sqlClause != null) {
            hql += " " + sqlClause;
        }

        try (Session session = HibernateUtils.getSession(agencyId)) {
            var query = session.createQuery(hql, MonitoringEvent.class)
                .setParameter("beginDate", beginTime)
                .setParameter("endDate", endTime);

            List<MonitoringEvent> monitorEvents = query.list();
            return monitorEvents;
        } catch (HibernateException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }
}
