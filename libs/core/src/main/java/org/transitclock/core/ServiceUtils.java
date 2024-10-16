/* (C)2023 */
package org.transitclock.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.transitclock.domain.structs.Agency;
import org.transitclock.domain.structs.Calendar;
import org.transitclock.domain.structs.CalendarDate;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.ServiceProperties;
import org.transitclock.utils.Time;

import lombok.extern.slf4j.Slf4j;

/**
 * For working with service types, such as determining serviceId or appropriate block to use for a
 * given epoch time.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class ServiceUtils {
    private final ServiceProperties properties;
    private final GregorianCalendar calendar;
    private final DbConfig dbConfig;
    /**
     * ServiceUtils constructor. Creates reusable GregorianCalendar and sets the timezone so that
     * the calendar can be reused.
     */
    public ServiceUtils(ServiceProperties properties, DbConfig dbConfig) {
        this.properties = properties;
        Agency agency = dbConfig.getFirstAgency();
        this.calendar = agency != null ? new GregorianCalendar(agency.getTimeZone()) : new GregorianCalendar();
        this.dbConfig = dbConfig;
    }

    /**
     * Returns day of the week. Value returned will be a constant from jvaa.util.Calendar such as
     * Calendar.TUESDAY.
     *
     * @param epochTime
     * @return Day of the week
     */
    public int getDayOfWeek(Date epochTime) {
        synchronized (calendar) {
            calendar.setTime(epochTime);
            return calendar.get(java.util.Calendar.DAY_OF_WEEK);
        }
    }

    /**
     * Gets list of currently active calendars. If all Calendars have expired then will still use
     * the ones that end at the latest date. This way if someone forgets to update the GTFS
     * Calendars or if someone forgets to process the latest GTFS data in time, the system will
     * still run using the old Calendars. This is very important because it is unfortunately
     * somewhat common for the Calendars to expire.
     *
     * <p>Uses already read in calendars, but does a good number of calculations so still a bit
     * expensive.
     *
     * @param epochTime For determining which Calendars are currently active
     * @return List of active Calendars
     */
    private List<Calendar> getActiveCalendars(Date epochTime) {
        List<Calendar> originalCalendarList = dbConfig.getCalendars();
        List<Calendar> activeCalendarList = new ArrayList<Calendar>();
        long maxEndTime = 0;

        // Go through calendar and find currently active ones
        for (Calendar calendar : originalCalendarList) {
            // If calendar is currently active then add it to list of active ones
            if (epochTime.getTime() >= calendar.getStartDate().getTime()
                    && epochTime.getTime() <= calendar.getEndDate().getTime()) {
                activeCalendarList.add(calendar);
            }

            // Update the maxEndTime in case all calendars have expired
            maxEndTime = Math.max(calendar.getEndDate().getTime(), maxEndTime);
        }

        // If there are no currently active calendars then there most
        // likely someone forgot to update the dates or perhaps the
        // latest GTFS data was never processed. To handle this kind
        // of situation use the most recent Calendars if none are
        // configured to be active.
        if (activeCalendarList.isEmpty()) {
            // Use most recent calendar to keep system running
            long earliestStartTime = Long.MAX_VALUE;
            for (Calendar calendar : originalCalendarList) {
                if (calendar.getEndDate().getTime() == maxEndTime) {
                    activeCalendarList.add(calendar);

                    // Determine earliest start time for calendars so can
                    // determine if should output error message.
                    if (calendar.getStartDate().getTime() < earliestStartTime)
                        earliestStartTime = calendar.getStartDate().getTime();
                }
            }

            // This is a rather serious issue so log it as an error if the start
            // time is OK, which indicates that the end time was not. The reason
            // a start time violation is not worth noting is because sometimes
            // the system looks at service class for previous day so can handle
            // assignments that span midnight. If the calendar just started
            // today and looking at yesterday then that is not a notable
            // problem.
            boolean startTimeAProblem = earliestStartTime > epochTime.getTime();
            if (!startTimeAProblem) {
                logger.error("All Calendars were expired. Update them!!!");

                // Output calendar list but only for debugging since it is
                // so verbose
                logger.debug(
                        "So that the system will continue to run the " + "old Calendars will be used: {}",
                        activeCalendarList);
            }
        }

        // Return the results
        return activeCalendarList;
    }

    Map<Date, List<String>> serviceIdsForDate = new HashMap<Date, List<String>>();

    /**
     * Caching version fo getServiceIdsForDay. Assumes epochTime can be distilled to a serviceDate.
     * Note that boundary conditions may exist where serviceDate guess is wrong.
     *
     * <p>TODO as is this cache will grow without bounds, but the data should be small
     */
    public List<String> getServiceIdsForDay(Date epochTime) {
        Date serviceDate = getStartOfDay(epochTime);
        if (serviceIdsForDate.containsKey(serviceDate)) {
            return serviceIdsForDate.get(serviceDate);
        }
        List<String> serviceIds = getServiceIdsForDayNoCache(serviceDate);
        serviceIdsForDate.put(serviceDate, serviceIds);
        return serviceIds;
    }

    private Date getStartOfDay(Date epochTime) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(epochTime);
        c.set(java.util.Calendar.HOUR, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /**
     * Determines list of current service IDs for the specified time. These service IDs designate
     * which block assignments are currently active.
     *
     * <p>Uses already read in calendars, but does a good number of calculations so still a bit
     * expensive.
     *
     * @param epochTime The current time that determining service IDs for
     * @return List of service IDs that are active for the specified time.
     */
    public List<String> getServiceIdsForDayNoCache(Date epochTime) {
        List<String> serviceIds = new ArrayList<String>();

        // Make sure haven't accidentally let all calendars expire
        List<Calendar> activeCalendars = getActiveCalendars(epochTime);

        // Go through calendars and determine which ones match. For those that
        // match, add them to the list of service IDs.
        int dateOfWeek = getDayOfWeek(epochTime);
        for (Calendar calendar : activeCalendars) {
            // If calendar for the current day of the week then add the
            // serviceId
            if ((dateOfWeek == java.util.Calendar.MONDAY && calendar.getMonday())
                    || (dateOfWeek == java.util.Calendar.TUESDAY && calendar.getTuesday())
                    || (dateOfWeek == java.util.Calendar.WEDNESDAY && calendar.getWednesday())
                    || (dateOfWeek == java.util.Calendar.THURSDAY && calendar.getThursday())
                    || (dateOfWeek == java.util.Calendar.FRIDAY && calendar.getFriday())
                    || (dateOfWeek == java.util.Calendar.SATURDAY && calendar.getSaturday())
                    || (dateOfWeek == java.util.Calendar.SUNDAY && calendar.getSunday())) {
                serviceIds.add(calendar.getServiceId());
            }
        }
        logger.debug("For {} services from calendar.txt that are active are {}", epochTime, serviceIds);

        // Go through calendar_dates to see if there is special service for
        // this date. Add or remove the special service.

        List<CalendarDate> calendarDatesForNow = dbConfig.getCalendarDates(epochTime);

        if (calendarDatesForNow != null) {
            for (CalendarDate calendarDate : calendarDatesForNow) {
                // Handle special service for this date
                if (calendarDate.addService()) {
                    // Add the service for this date
                    serviceIds.add(calendarDate.getServiceId());
                } else {
                    // Remove the service for this date
                    serviceIds.remove(calendarDate.getServiceId());
                }

                logger.debug(
                        "{} is special service date in " + "calendar_dates.txt file. Services now are {}",
                        epochTime,
                        serviceIds);
            }
        }
        logger.info("Finished adding calendar dates");

        // Return the results
        return serviceIds;
    }

    /**
     * Determines list of current service IDs for the specified time. These service IDs designate
     * which block assignments are currently active.
     *
     * <p>Uses already read in calendars, but does a good number of calculations so still a bit
     * expensive.
     *
     * @param epochTime The current time that determining service IDs for
     * @return List of service IDs that are active for the specified time.
     */
    public List<String> getServiceIdsForDay(long epochTime) {
        return getServiceIdsForDay(new Date(epochTime));
    }

    /**
     * Determines list of current service IDs for the specified time. If it is before the java
     * property minutesIntoMorningToIncludePreviousServiceIds then the service IDs for the previous
     * day will also be included. This way will get the proper service IDs even for blocks that
     * started the previous day. Important for late night service. These service IDs designate which
     * block assignments are currently active.
     *
     * <p>Uses already read in calendars, but does a good number of calculations so still a bit
     * expensive.
     *
     * @param epochTime The current time that determining service IDs for
     * @return List of service IDs that are active for the specified time, includes ones for
     *     previous day if epochTime specifies it is early in the morning.
     */
    public Collection<String> getServiceIds(Date epochTime) {
        List<String> serviceIdsForDay = getServiceIdsForDay(epochTime);
        Time time = dbConfig.getTime();
        if (time.getSecondsIntoDay(epochTime)
                > properties.getMinutesIntoMorningToIncludePreviousServiceIds() * Time.MIN_IN_SECS) return serviceIdsForDay;

        List<String> serviceIdsForPreviousDay = getServiceIdsForDay(epochTime.getTime() - Time.DAY_IN_MSECS);

        Set<String> set = new HashSet<String>(serviceIdsForDay);
        set.addAll(serviceIdsForPreviousDay);
        return set;
    }

    /**
     * Determines list of current service IDs for the specified time. If it is before the java
     * property minutesIntoMorningToIncludePreviousServiceIds then the service IDs for the previous
     * day will also be included. This way will get the proper service IDs even for blocks that
     * started the previous day. Important for late night service. These service IDs designate which
     * block assignments are currently active.
     *
     * <p>Uses already read in calendars, but does a good number of calculations so still a bit
     * expensive.
     *
     * @param epochTime The current time that determining service IDs for
     * @return List of service IDs that are active for the specified time, includes ones for
     *     previous day if epochTime specifies it is early in the morning.
     */
    public Collection<String> getServiceIds(long epochTime) {
        return getServiceIds(new Date(epochTime));
    }

    /**
     * Finds the calendars that are currently active.
     *
     * @param epochTime
     * @return List of calendars that are currently active.
     */
    public List<Calendar> getCurrentCalendars(long epochTime) {
        // Result to be returned
        List<Calendar> currentCalendars = new ArrayList<>();

        // Get list of all calendars that are configured
        List<Calendar> allCalendars = dbConfig.getCalendars();

        // For each service ID that is currently active...
        Collection<String> currentServiceIds = getServiceIds(epochTime);
        for (String serviceId : currentServiceIds) {
            // Find corresponding calendar
            for (Calendar calendar : allCalendars) {
                if (calendar.getServiceId().equals(serviceId)) {
                    // Found the calendar that corresponds to the service ID
                    // so add it to the list
                    currentCalendars.add(calendar);
                    break;
                }
            }
        }

        // Return the results
        return currentCalendars;
    }
}
