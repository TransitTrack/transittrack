package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.Transfer;

import java.util.List;

public class TransferRepository extends BaseRepository<Transfer> {
    /**
     * Deletes rev 0 from the Transfers table
     *
     * @param session
     * @param configRev
     * @return Number of rows deleted
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        // Note that hql uses class name, not the table name
        return session
                .createMutationQuery("DELETE Transfer WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
    }

    /**
     * Returns List of Transfer objects for the specified database revision.
     *
     * @param session
     * @param configRev
     * @return
     * @throws HibernateException
     */
    public static List<Transfer> getTransfers(Session session, int configRev) throws HibernateException {
        return session
                .createQuery("FROM Transfer WHERE configRev = :configRev", Transfer.class)
                .setParameter("configRev", configRev)
                .list();
    }
}
