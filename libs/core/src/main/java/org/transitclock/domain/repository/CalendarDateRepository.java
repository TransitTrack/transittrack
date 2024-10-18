package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.CalendarDate;

import java.util.List;

@Slf4j
public class CalendarDateRepository extends BaseRepository<CalendarDate> {
    /**
     * Deletes rev from the CalendarDates table
     *
     * @param session
     * @param configRev
     * @return Number of rows deleted
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        return session.createMutationQuery("DELETE CalendarDate WHERE configRev= :configRev")
                .setParameter("configRev", configRev)
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
    public static List<CalendarDate> getCalendarDates(Session session, int configRev) throws HibernateException {
        return session.createQuery("FROM CalendarDate WHERE configRev = :configRev", CalendarDate.class)
                .setParameter("configRev", configRev)
                .list();
    }
}
