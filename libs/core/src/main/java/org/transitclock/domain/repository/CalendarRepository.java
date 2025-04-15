package org.transitclock.domain.repository;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.Calendar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CalendarRepository extends BaseRepository<Calendar>  {
    /**
     * Deletes rev from the Calendars table
     *
     * @param session
     * @param configRev
     * @return Number of rows deleted
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        // Note that hql uses class name, not the table name
        return session.createMutationQuery("DELETE Calendar WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
    }

    /**
     * Returns List of Calendar objects for the specified database revision.
     *
     * @param session
     * @param configRev
     * @return List of Calendar objects
     * @throws HibernateException
     */
    public static List<Calendar> getCalendars(Session session, int configRev) throws HibernateException {
        var query = session
                .createQuery("FROM Calendar WHERE configRev = :configRev ORDER BY serviceId", Calendar.class)
                .setParameter("configRev", configRev);
        return query.list();
    }

    /**
     * Opens up a new db session and returns Map of Calendar objects for the specified database
     * revision. The map is keyed on the serviceId.
     *
     * @param dbName Specified name of database
     * @param configRev
     * @return Map of Calendar objects keyed on serviceId
     * @throws HibernateException
     */
    public static Map<String, Calendar> getCalendars(String dbName, int configRev) throws HibernateException {
        // Get the database session. This is supposed to be pretty light weight
        Session session = HibernateUtils.getSession(dbName);

        // Get list of calendars
        List<Calendar> calendarList = getCalendars(session, configRev);

        // Convert list to map and return result
        Map<String, Calendar> map = new HashMap<>();
        for (Calendar calendar : calendarList)
            map.put(calendar.getServiceId(), calendar);
        return map;
    }
}
