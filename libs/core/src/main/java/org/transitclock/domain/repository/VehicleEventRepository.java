package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.VehicleEvent;
import org.transitclock.utils.IntervalTimer;

import java.util.Date;
import java.util.List;

@Slf4j
public class VehicleEventRepository extends BaseRepository<VehicleEvent> {
    /**
     * Reads in all VehicleEvents from the database that were between the beginTime and endTime.
     *
     * @param agencyId  Which project getting data for
     * @param beginTime Specifies time range for query
     * @param endTime   Specifies time range for query
     * @param sqlClause Optional. Can specify an SQL clause to winnow down the data, such as "AND
     *                  routeId='71'".
     * @return
     */
    public static List<VehicleEvent> getVehicleEvents(String agencyId, Date beginTime, Date endTime, String sqlClause) {
        IntervalTimer timer = new IntervalTimer();

        // Get the database session. This is supposed to be pretty light weight
        Session session = HibernateUtils.getSession(agencyId);

        // Create the query. Table name is case sensitive and needs to be the
        // class name instead of the name of the db table.
        String hql = "FROM VehicleEvent WHERE time >= :beginDate AND time < :endDate";
        if (sqlClause != null) {
            hql += " " + sqlClause;
        }
        var query = session.createQuery(hql, VehicleEvent.class);

        // Set the parameters
        query.setParameter("beginDate", beginTime);
        query.setParameter("endDate", endTime);

        try {
            List<VehicleEvent> vehicleEvents = query.list();
            logger.debug("Getting VehicleEvents from database took {} msec", timer.elapsedMsec());
            return vehicleEvents;
        } catch (HibernateException e) {
            logger.error(e.getMessage(), e);
            return null;
        } finally {
            // Clean things up. Not sure if this absolutely needed nor if
            // it might actually be detrimental and slow things down.
            session.close();
        }
    }
}
