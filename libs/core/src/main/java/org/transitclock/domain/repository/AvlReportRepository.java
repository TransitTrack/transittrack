package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.AvlReport;

import java.util.Date;
import java.util.List;

@Slf4j
public class AvlReportRepository extends BaseRepository<AvlReport> {

    /**
     * Gets list of AvlReports from database for the time span specified.
     *
     * @param beginTime
     * @param endTime
     * @param vehicleId Optional. If not null then will only return results for that vehicle
     * @param clause Optional. If not null then the clause, such as "ORDER BY time" will be added to
     *     the hql statement.
     * @return List of AvlReports or null if an exception is thrown
     */
    public static List<AvlReport> getAvlReportsFromDb(Date beginTime, Date endTime, String vehicleId, String clause) {
        String hql = "FROM AvlReport WHERE time >= :beginDate AND time < :endDate";
        if (vehicleId != null && !vehicleId.isEmpty())
            hql += " AND vehicleId=:vehicleId";
        if (clause != null)
            hql += " " + clause;

        try(Session session = HibernateUtils.getSession()) {
            var query = session.createQuery(hql, AvlReport.class)
                    .setParameter("beginDate", beginTime)
                    .setParameter("endDate", endTime);

            // Set the parameters
            if (vehicleId != null && !vehicleId.isEmpty()) {
                query.setParameter("vehicleId", vehicleId);
            }

            return query.list();
        } catch (HibernateException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
