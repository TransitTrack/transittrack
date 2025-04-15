package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.ActiveRevision;
import org.transitclock.domain.structs.Agency;

import java.util.List;
import java.util.TimeZone;

@Slf4j
public class AgencyRepository extends BaseRepository<Agency> {
    /**
     * Deletes rev from the Agencies table
     *
     * @param session
     * @param configRev
     * @return Number of rows deleted
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        // Note that hql uses class name, not the table name
        return session.createMutationQuery("DELETE Agency WHERE configRev=" + configRev)
                .executeUpdate();
    }

    /**
     * Returns List of Agency objects for the specified database revision.
     *
     * @param session
     * @param configRev
     * @return
     * @throws HibernateException
     */
    public static List<Agency> getAgencies(Session session, int configRev) throws HibernateException {
        var query = session
                .createQuery("FROM Agency WHERE configRev = :configRev", Agency.class)
                .setParameter("configRev", configRev);
        return query.list();
    }

    /**
     * Returns the list of agencies for the specified project ID.
     *
     * @param agencyId Specifies name of database
     * @param configRev
     * @return
     */
    public static List<Agency> getAgencies(String agencyId, int configRev) {
        // Get the database session. This is supposed to be pretty light weight
        try (Session session = HibernateUtils.getSession(agencyId)) {
            return getAgencies(session, configRev);
        }
    }

    /**
     * Reads the current timezone for the agency from the agencies database
     *
     * @param agencyId
     * @return The TimeZone, or null if not successful
     */
    public static TimeZone getTimeZoneFromDb(String agencyId) {
        int configRev = ActiveRevision.get(agencyId).getConfigRev();

        List<Agency> agencies = getAgencies(agencyId, configRev);
        if (!agencies.isEmpty()) return agencies.get(0).getTimeZone();
        else return null;
    }
}
