package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.Match;

import java.util.Date;
import java.util.List;

@Slf4j
public class MatchRepository extends BaseRepository<Match>  {
    /**
     * Allows batch retrieval of Match data from database. This is likely the best way to read in
     * large amounts of data.
     *
     * @param projectId
     * @param beginTime
     * @param endTime
     * @param sqlClause The clause is added to the SQL for retrieving the arrival/departures. Useful
     *     for ordering the results. Can be null.
     * @param firstResult
     * @param maxResults
     * @return
     */
    public static List<Match> getMatchesFromDb(
            String projectId,
            Date beginTime,
            Date endTime,
            String sqlClause,
            final Integer firstResult,
            final Integer maxResults) {
        try (Session session = HibernateUtils.getSession(projectId)) {

            // Create the query. Table name is case sensitive and needs to be the
            // class name instead of the name of the db table.
            String hql = "FROM Match WHERE avlTime between :beginDate AND :endDate";
            if (sqlClause != null) {
                hql += " " + sqlClause;
            }
            var query = session.createQuery(hql, Match.class)
                    .setParameter("beginDate", beginTime)
                    .setParameter("endDate", endTime);

            if (firstResult != null) {
                query.setFirstResult(firstResult);
            }

            if (maxResults != null) {
                query.setMaxResults(maxResults);
            }

            return query.list();
        } catch (HibernateException e) {
            // Log error to the Core logger
            logger.error(e.getMessage(), e);
        }
        return List.of();
    }

    public static Long getMatchesCountFromDb(String projectId, Date beginTime, Date endTime, String sqlClause) {
        // Create the query. Table name is case sensitive and needs to be the
        // class name instead of the name of the db table.
        String hql = "select count(*) FROM Match WHERE avlTime >= :beginDate AND avlTime < :endDate";
        if (sqlClause != null)
            hql += " " + sqlClause;

        // Get the database session. This is supposed to be pretty light weight
        try (Session session = HibernateUtils.getSession(projectId)) {
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
