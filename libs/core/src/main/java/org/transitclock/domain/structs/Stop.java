/* (C)2023 */
package org.transitclock.domain.structs;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;
import org.transitclock.gtfs.TitleFormatter;
import org.transitclock.gtfs.model.GtfsStop;

/**
 * For storing in db information on a stop. Based on GTFS info from stops.txt file.
 *
 * @author SkiBu Smith
 */
@Entity
@DynamicUpdate
@Getter @Setter @ToString
@Table(name = "stops")
public class Stop implements Serializable {

    @Id
    @Column(name = "config_rev")
    private final int configRev;

    // The stop ID
    @Id
    @Column(name = "id", length = 60)
    private final String id;

    // The stop code used for SMS and phone systems
    @Column(name = "code")
    private final Integer code;

    // Name of the stop
    @Column(name = "name")
    private final String name;

    // Latitude/longitude of stop
    @Embedded
    private final Location loc;

    // If should generate special ScheduleAdherence data for this stop
    @Column(name = "time_point_stop")
    private final boolean timepointStop;

    // Indicates that vehicle can leave route path before departing this stop
    // since the driver is taking a break.
    @Column(name = "layover_stop")
    private final Boolean layoverStop;

    // Indicates that vehicle is not supposed to depart the stop until the
    // scheduled departure time.
    @Column(name = "wait_stop")
    private final Boolean waitStop;

    // Indicates if stop should be hidden from public
    @Column(name = "hidden")
    private final boolean hidden;

    /**
     * Constructor
     *
     * @param configRev
     * @param gtfsStop
     * @param stopCodeBaseValue For when stop code not specified in GTFS. If this value is set but
     *     stop code not configured then will sets the stop code to the stop ID plus the this
     *     stopCodeBaseValue.
     * @param titleFormatter
     */
    public Stop(int configRev, GtfsStop gtfsStop, Integer stopCodeBaseValue, TitleFormatter titleFormatter) {
        // Because will be writing data to sandbox rev in the db
        this.configRev = configRev;

        this.id = gtfsStop.getStopId();

        // Some agencies like SFMTA don't bother to fill in the stop_code field
        // in the GTFS data. But if they use a numeric stopId can use that.
        Integer stopCode = gtfsStop.getStopCode();
        if (stopCode == null) {
            // stop_code was not set in GTFS data so try using stop_id
            try {
                stopCode = Integer.parseInt(id);
                if (stopCodeBaseValue != null) stopCode += stopCodeBaseValue;
            } catch (NumberFormatException e) {
                // Well, we tried using the stopId but it was not numeric.
                // Therefore, the stopCode will simply be null.
            }
        }
        this.code = stopCode;

        this.name = titleFormatter.processTitle(gtfsStop.getStopName());
        this.loc = new Location(gtfsStop.getStopLat(), gtfsStop.getStopLon());
        // If adherence_stop not set then the default is false
        this.timepointStop = (gtfsStop.getTimepointStop() != null ? gtfsStop.getTimepointStop() : false);
        // If layover_stop not set then the default is false
        this.layoverStop = gtfsStop.getLayoverStop();
        // If wait_stop not set then the default is false
        this.waitStop = gtfsStop.getWaitStop();
        // If hidden not set then the default is false
        this.hidden = (gtfsStop.getHidden() != null ? gtfsStop.getHidden() : false);
    }

    /** Needed because Hibernate requires no-arg constructor */
    @SuppressWarnings("unused")
    protected Stop() {
        configRev = -1;
        id = null;
        code = null;
        name = null;
        loc = null;
        timepointStop = false;
        layoverStop = null;
        waitStop = null;
        hidden = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stop stop)) return false;
        return configRev == stop.configRev && timepointStop == stop.timepointStop && hidden == stop.hidden && Objects.equals(id, stop.id) && Objects.equals(code, stop.code) && Objects.equals(name, stop.name) && Objects.equals(loc, stop.loc) && Objects.equals(layoverStop, stop.layoverStop) && Objects.equals(waitStop, stop.waitStop);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configRev, id, code, name, loc, timepointStop, layoverStop, waitStop, hidden);
    }
}
