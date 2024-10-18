package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.FareRule;

import java.util.List;

public class FareRuleRepository extends BaseRepository<FareRule> {
    /**
     * Deletes rev from the FareRules table
     *
     * @param session
     * @param configRev
     * @return Number of rows deleted
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        // Note that hql uses class name, not the table name
        return session
                .createMutationQuery("DELETE FareRule WHERE configRev = :configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
    }

    /**
     * Returns List of FareRule objects for the specified database revision.
     *
     * @param session
     * @param configRev
     * @return
     * @throws HibernateException
     */
    @SuppressWarnings("unchecked")
    public static List<FareRule> getFareRules(Session session, int configRev) throws HibernateException {
        return session.createQuery("FROM FareRule WHERE configRev = :configRev", FareRule.class)
                .setParameter("configRev", configRev)
                .list();
    }
}
