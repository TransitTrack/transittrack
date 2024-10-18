/* (C)2023 */
package org.transitclock.domain.structs;

import jakarta.persistence.*;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Immutable;

import org.transitclock.gtfs.model.GtfsCalendar;
import org.transitclock.utils.Time;

/**
 * Contains data from the calendar.txt GTFS file. This class is for reading/writing that data to the
 * db.
 *
 * @author SkiBu Smith
 */
@Immutable
@Entity
@DynamicUpdate
@Getter @Setter @ToString
@Table(name = "calendars")
public class Calendar implements Serializable {

    @Id
    @Column(name = "config_rev")
    private final int configRev;

    @Id
    @Column(name = "service_id", length = 60)
    private final String serviceId;

    @Id
    @Column(name = "monday")
    private final boolean monday;

    @Id
    @Column(name = "tuesday")
    private final boolean tuesday;

    @Id
    @Column(name = "wednesday")
    private final boolean wednesday;

    @Id
    @Column(name = "thursday")
    private final boolean thursday;

    @Id
    @Column(name = "friday")
    private final boolean friday;

    @Id
    @Column(name = "saturday")
    private final boolean saturday;

    @Id
    @Column(name = "sunday")
    private final boolean sunday;

    @Id
    @Column(name = "start_date")
    @Temporal(TemporalType.DATE)
    private final Date startDate;

    // The service is to run until midnight of the end date, which is actually
    // the endDate plus 1 day.
    @Id
    @Column(name = "end_date")
    @Temporal(TemporalType.DATE)
    private final Date endDate;

    // For outputting start and end date as strings
    private static final DateFormat formatter = new SimpleDateFormat("MM-dd-yyyy");

    /** Needed because Hibernate requires no-arg constructor */
    @SuppressWarnings("unused")
    protected Calendar() {
        configRev = -1;
        serviceId = null;
        monday = false;
        tuesday = false;
        wednesday = false;
        thursday = false;
        friday = false;
        saturday = false;
        sunday = false;
        startDate = null;
        endDate = null;
    }

    /**
     * Constructor
     *
     * @param configRev
     * @param gc
     * @param dateFormat
     */
    public Calendar(int configRev, GtfsCalendar gc, DateFormat dateFormat) {
        this.configRev = configRev;
        this.serviceId = gc.getServiceId();
        this.monday = isSetToTrue(gc.getMonday());
        this.tuesday = isSetToTrue(gc.getTuesday());
        this.wednesday = isSetToTrue(gc.getWednesday());
        this.thursday = isSetToTrue(gc.getThursday());
        this.friday = isSetToTrue(gc.getFriday());
        this.saturday = isSetToTrue(gc.getSaturday());
        this.sunday = isSetToTrue(gc.getSunday());

        // Dealing with dates is complicated because must parse
        Date tempDate;
        try {
            tempDate = dateFormat.parse(gc.getStartDate());
        } catch (ParseException e) {
//            logger.error(
//                    "Could not parse calendar start_date \"{}\" from " + "line #{} from file {}",
//                    gc.getStartDate(),
//                    gc.getLineNumber(),
//                    gc.getFileName());
            tempDate = new Date();
        }
        this.startDate = tempDate;

        // For end date parse the specified date and add a day so that
        // the end date will be midnight of the date specified.
        try {
            tempDate = dateFormat.parse(gc.getEndDate());
        } catch (ParseException e) {
//            logger.error(
//                    "Could not parse calendar end_date \"{}\" from " + "line #{} from file {}",
//                    gc.getStartDate(),
//                    gc.getLineNumber(),
//                    gc.getFileName());
            tempDate = new Date();
        }
        this.endDate = tempDate;
    }

    /**
     * Returns true if the parameter zeroOrOne is set to "1". Otherwise returns false.
     *
     * @param zeroOrOne
     * @return
     */
    private boolean isSetToTrue(String zeroOrOne) {
        return zeroOrOne != null && zeroOrOne.trim().equals("1");
    }

    public boolean getMonday() {
        return monday;
    }

    public boolean getTuesday() {
        return tuesday;
    }

    public boolean getWednesday() {
        return wednesday;
    }

    public boolean getThursday() {
        return thursday;
    }

    public boolean getFriday() {
        return friday;
    }

    public boolean getSaturday() {
        return saturday;
    }

    public boolean getSunday() {
        return sunday;
    }

    /**
     * @return the start date as a formatted string
     */
    public String getStartDateStr() {
        return formatter.format(startDate);
    }

    /**
     * End of the last day of service. This means that when an end date is specified the service
     * runs for up to and including that day. Since days start at midnight, this method returns the
     * endDate plus 1 day so that it represents midnight of the configured endDate.
     *
     * @return midnight at the end of the endDate
     */
    public Date getEndDate() {
        return new Date(endDate.getTime() + Time.MS_PER_DAY);
    }

    /**
     * @return the end date as a formatted string
     */
    public String getEndDateStr() {
        return formatter.format(endDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Calendar calendar)) return false;
        return configRev == calendar.configRev && monday == calendar.monday && tuesday == calendar.tuesday && wednesday == calendar.wednesday && thursday == calendar.thursday && friday == calendar.friday && saturday == calendar.saturday && sunday == calendar.sunday && Objects.equals(serviceId, calendar.serviceId) && Objects.equals(startDate, calendar.startDate) && Objects.equals(endDate, calendar.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configRev, serviceId, monday, tuesday, wednesday, thursday, friday, saturday, sunday, startDate, endDate);
    }
}
