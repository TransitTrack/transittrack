/* (C)2023 */
package org.transitclock.domain.structs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;
import org.transitclock.gtfs.model.GtfsFrequency;

/**
 * Contains data from the frequencies.txt GTFS file. This class is for reading/writing that data to
 * the db.
 *
 * @author SkiBu Smith
 */
@Entity
@DynamicUpdate
@Getter @Setter @ToString
@Table(name = "frequencies")
public class Frequency implements Serializable {

    @Id
    @Column(name = "config_rev")
    private final int configRev;

    @Id
    @Column(name = "trip_id", length = 60)
    private final String tripId;

    @Id
    @Column(name = "start_time")
    private final int startTime;

    @Column(name = "end_time")
    private final int endTime;

    @Column(name = "headway_secs")
    private final int headwaySecs;

    /**
     * The exact_times field determines if frequency-based trips should be exactly scheduled based
     * on the specified headway information. Valid values for this field are:
     *
     * <ul>
     *   <li>0 or (empty) - Frequency-based trips are not exactly scheduled. This is the default
     *       behavior. 1 - Frequency-based trips are exactly scheduled. For a frequencies.txt row,
     *       trips are scheduled starting with trip_start_time = start_time + x * headway_secs for
     *       all x in (0, 1, 2, ...) where trip_start_time < end_time.
     *   <li>The value of exact_times must be the same for all frequencies.txt rows with the same
     *       trip_id. If exact_times is 1 and a frequencies.txt row has a start_time equal to
     *       end_time, no trip must be scheduled. When exact_times is 1, care must be taken to
     *       choose an end_time value that is greater than the last desired trip start time but less
     *       than the last desired trip start time + headway_secs.
     * </ul>
     */
    @Column(name = "exact_times")
    private final boolean exactTimes;

    /**
     * Constructor
     *
     * @param configRev
     * @param gtfsFrequency
     */
    public Frequency(int configRev, GtfsFrequency gtfsFrequency) {
        this.configRev = configRev;
        this.tripId = gtfsFrequency.getTripId();
        this.startTime = gtfsFrequency.getStartTime();
        this.endTime = gtfsFrequency.getEndTime();
        this.headwaySecs = gtfsFrequency.getHeadwaySecs();
        this.exactTimes = (gtfsFrequency.getExactTimes() != null ? gtfsFrequency.getExactTimes() : false);
    }

    /** Needed because Hibernate requires no-arg constructor */
    @SuppressWarnings("unused")
    protected Frequency() {
        configRev = -1;
        tripId = null;
        startTime = -1;
        endTime = -1;
        headwaySecs = -1;
        exactTimes = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Frequency frequency)) return false;
        return configRev == frequency.configRev && startTime == frequency.startTime && endTime == frequency.endTime && headwaySecs == frequency.headwaySecs && exactTimes == frequency.exactTimes && Objects.equals(tripId, frequency.tripId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configRev, tripId, startTime, endTime, headwaySecs, exactTimes);
    }
}
