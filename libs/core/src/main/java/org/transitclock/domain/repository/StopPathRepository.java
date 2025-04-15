package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.StopPath;

import java.util.List;

public class StopPathRepository extends BaseRepository<StopPath> {
    public static List<StopPath> getPaths(Session session, int configRev) throws HibernateException {
        return session.createQuery("FROM StopPath WHERE configRev = :configRev", StopPath.class)
                .setParameter("configRev", configRev)
                .list();
    }
}
