package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.Stop;

import java.util.List;

public class StopRepository extends BaseRepository<Stop>  {
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        return session.createMutationQuery("DELETE Stop WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
    }

    public static List<Stop> getStops(Session session, int configRev) throws HibernateException {
        return session.createQuery("FROM Stop WHERE configRev = :configRev", Stop.class)
                .setParameter("configRev", configRev)
                .list();
    }
}
