package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.FareAttribute;

import java.util.List;

public class FareAttributeRepository extends BaseRepository<FareAttribute> {
    /**
     * Deletes rev from the FareAttributes table
     *
     * @param session
     * @param configRev
     * @return Number of rows deleted
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        // Note that hql uses class name, not the table name
        return session.createMutationQuery("DELETE FareAttribute WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
    }

    /**
     * Returns List of FareAttribute objects for the specified database revision.
     *
     * @param session
     * @param configRev
     * @return
     * @throws HibernateException
     */
    public static List<FareAttribute> getFareAttributes(Session session, int configRev) throws HibernateException {
        return session.createQuery("FROM FareAttribute WHERE configRev = :configRev", FareAttribute.class)
                .setParameter("configRev", configRev)
                .list();
    }
}
