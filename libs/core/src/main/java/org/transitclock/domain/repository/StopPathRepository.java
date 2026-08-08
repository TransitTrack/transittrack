package org.transitclock.domain.repository;

import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.StopPath;

public class StopPathRepository extends BaseRepository<StopPath> {
    public static List<StopPath> getPaths(Session session, int configRev) throws HibernateException {
        return session.createQuery("FROM StopPath WHERE configRev = :configRev", StopPath.class)
                .setParameter("configRev", configRev)
                .list();
    }
}
