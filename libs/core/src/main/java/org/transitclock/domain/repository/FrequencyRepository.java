package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.Frequency;

import java.util.List;

public class FrequencyRepository extends BaseRepository<Frequency> {
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        // Note that hql uses class name, not the table name
        return session.createMutationQuery("DELETE Frequency WHERE configRev= :configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
    }

    public static List<Frequency> getFrequencies(Session session, int configRev) throws HibernateException {
        return session.createQuery("FROM Frequency WHERE configRev = :configRev", Frequency.class)
                .setParameter("configRev", configRev)
                .list();
    }
}
