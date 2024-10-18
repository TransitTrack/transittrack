/* (C)2023 */
package org.transitclock.domain.structs;

import javax.annotation.Nullable;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.CallbackException;
import org.hibernate.Session;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.classic.Lifecycle;
import org.hibernate.collection.spi.PersistentList;
import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.gtfs.TitleFormatter;
import org.transitclock.gtfs.model.GtfsTrip;
import org.transitclock.utils.Time;

/**
 * Describes a GTFS trip but also includes travel time information.
 *
 * <p>Serializable since Hibernate requires such.
 *
 * <p>Implements Lifecycle so that can have the onLoad() callback be called when reading in data so
 * that can intern() member strings. In order to do this the String members could not be declared as
 * final since they are updated after the constructor is called.
 *
 * @author SkiBu Smith
 */
@Slf4j
@Entity
@Getter @Setter @ToString
@DynamicUpdate
@Table(name = "trips")
public class Trip implements Lifecycle, Serializable {

    @Id
    @Column(name = "config_rev")
    private final int configRev;

    @Id
    @Column(name = "trip_id", length = 60)
    private String tripId;

    // The startTime needs to be an Id column because GTFS frequencies.txt
    // file can be used to define multiple trips with the same trip ID.
    // It is in number of seconds into the day.
    // Not declared as final because only used for frequency based trips.
    @Id
    @Column(name = "start_time")
    private Integer startTime;

    // Used by some agencies to identify the trip in the AVL feed
    @Column(name = "trip_short_name", length = 60)
    private String tripShortName;

    // Number of seconds into the day.
    // Not final because only used for frequency based trips.
    @Column(name = "end_time")
    private Integer endTime;

    @Column(name = "direction_id", length = 60)
    private String directionId;

    @Column(name = "route_id", length = 60)
    private String routeId;

    // Route short name is also needed because some agencies such as SFMTA
    // change the route IDs when making schedule changes. But we need a
    // consistent route identifier for certain things, such as bookmarking
    // prediction pages or for running schedule adherence reports over
    // time. For where need a route identifier that is consistent over time
    // it can be best to use the routeShortName.
    @Column(name = "route_short_name", length = 60)
    private String routeShortName;

    // So can determine all the stops and stopPaths associated with trip
    // Note that needs to be FetchType.EAGER because otherwise get a Javassist HibernateException.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name = "trippattern_id", referencedColumnName = "id"),
        @JoinColumn(name = "trippattern_config_rev", referencedColumnName = "config_rev")
    })
    @Cascade({CascadeType.SAVE_UPDATE})
    private TripPattern tripPattern;

    // Use FetchType.EAGER so that all travel times are efficiently read in
    // when system is started up.
    // Note that needs to be FetchType.EAGER because otherwise get a Javassist
    // HibernateException.
    //
    // We are sharing travel times so need a ManyToOne mapping
    @ManyToOne(fetch = FetchType.EAGER)
    @Cascade({CascadeType.SAVE_UPDATE})
    @JoinColumns({
        @JoinColumn(name = "traveltimes_id", referencedColumnName = "id")
    })
    private TravelTimesForTrip travelTimes;

    // Contains schedule time for each stop as obtained from GTFS
    // stop_times.txt file. Useful for determining schedule adherence.
    @OrderColumn(name = "list_index")
    @ElementCollection
    @CollectionTable(name = "trip_scheduled_times_list", joinColumns = {
        @JoinColumn(name = "trip_config_rev", referencedColumnName = "config_rev"),
        @JoinColumn(name = "trip_trip_id", referencedColumnName = "trip_id"),
        @JoinColumn(name = "trip_start_time", referencedColumnName = "start_time")
    })
    private final List<ScheduleTime> scheduledTimesList = new ArrayList<>();

    // For non-scheduled blocks where vehicle runs a trip as a continuous loop
    @Column(name = "no_schedule")
    private final boolean noSchedule;

    // For when times are determined via the GTFS frequency.txt file and
    // exact_times for the trip is set to true. Indicates that the schedule
    // times were determined using the trip frequency and start_time.
    @Column(name = "exact_times_headway")
    private final boolean exactTimesHeadway;

    // Service ID for the trip
    @Column(name = "service_id", length = 60)
    private String serviceId;

    // The GTFS trips.txt trip_headsign if set. Otherwise will get from the
    // stop_headsign, if set, from the first stop of the trip. Otherwise null.
    @Column(name = "headsign")
    private String headsign;

    // From GTFS trips.txt block_id if set. Otherwise the trip_id.
    @Column(name = "block_id", length = 60)
    private String blockId;

    // The GTFS trips.txt shape_id
    @Column(name = "shape_id", length = 60)
    private String shapeId;

    @Transient
    private Route route;


    /**
     * Constructs Trip object from GTFS data.
     *
     * <p>Does not set startTime nor endTime. Those are set separately using addScheduleTimes().
     * Also doesn't set travel times. Those are set separately using setTravelTimes().
     *
     * @param configRev
     * @param gtfsTrip The GTFS data describing the trip
     * @param properRouteId The routeId, but can actually be the ID of the parent route.
     * @param routeShortName Needed to provide a route identifier that is consistent over schedule
     *     changes.
     * @param unprocessedHeadsign the headsign from the GTFS trips.txt file, or if that is not
     *     available then the stop_headsign from the GTFS stop_times.txt file.
     * @param titleFormatter So can fix titles associated with trip
     */
    public Trip(
            int configRev,
            GtfsTrip gtfsTrip,
            String properRouteId,
            String routeShortName,
            String unprocessedHeadsign,
            TitleFormatter titleFormatter) {
        this.configRev = configRev;
        this.tripId = gtfsTrip.getTripId();
        this.tripShortName = gtfsTrip.getTripShortName();
        this.directionId = gtfsTrip.getDirectionId();
        this.routeId = properRouteId != null ? properRouteId : gtfsTrip.getRouteId();
        this.routeShortName = routeShortName;
        this.serviceId = gtfsTrip.getServiceId();
        this.headsign = processedHeadsign(unprocessedHeadsign, routeId, titleFormatter);
        // Make sure headsign not too long for db
        if (this.headsign.length() > TripPattern.HEADSIGN_LENGTH) {
            this.headsign = this.headsign.substring(0, TripPattern.HEADSIGN_LENGTH);
        }

        // block column is optional in GTFS trips.txt file. Best can do when
        // block ID is not set is to use the trip short name or the trip id
        // as the block. MBTA uses trip short name in the feed so start with
        // that.
        String theBlockId = gtfsTrip.getBlockId();
        if (theBlockId == null) {
            theBlockId = gtfsTrip.getTripShortName();
            if (theBlockId == null) theBlockId = gtfsTrip.getTripId();
        }
        this.blockId = theBlockId;
        this.shapeId = gtfsTrip.getShapeId();
        this.noSchedule = false;

        // Not a frequency based trip with an exact time so remember such
        this.exactTimesHeadway = false;
    }

    /**
     * Creates a copy of the Trip object but adjusts the startTime, endTime, and scheduledTimesMap
     * according to the frequenciesBasedStartTime. This is used when the frequencies.txt file
     * specifies exact_times for a trip.
     *
     * @param tripFromStopTimes
     * @param frequenciesBasedStartTime
     */
    public Trip(Trip tripFromStopTimes, int frequenciesBasedStartTime) {
        this.configRev = tripFromStopTimes.configRev;
        this.tripId = tripFromStopTimes.tripId;
        this.tripShortName = tripFromStopTimes.tripShortName;
        this.directionId = tripFromStopTimes.directionId;
        this.routeId = tripFromStopTimes.routeId;
        this.routeShortName = tripFromStopTimes.routeShortName;
        this.serviceId = tripFromStopTimes.serviceId;
        this.headsign = tripFromStopTimes.headsign;
        this.shapeId = tripFromStopTimes.shapeId;
        this.tripPattern = tripFromStopTimes.tripPattern;
        this.travelTimes = tripFromStopTimes.travelTimes;

        // Set the updated start and end times by using the frequencies
        // based start time.
        this.startTime = tripFromStopTimes.startTime + frequenciesBasedStartTime;
        this.endTime = tripFromStopTimes.endTime + frequenciesBasedStartTime;

        // Since frequencies being used for configuration we will have multiple
        // trips with the same ID. But need a different block ID for each one.
        // Therefore use the original trip's block ID but then append the
        // start time as a string to make it unique.
        this.blockId = tripFromStopTimes.blockId + "_" + Time.timeOfDayStr(this.startTime);

        // Set the scheduledTimesMap by using the frequencies based start time
        for (ScheduleTime schedTimeFromStopTimes : tripFromStopTimes.scheduledTimesList) {
            Integer arrivalTime = null;
            if (schedTimeFromStopTimes.getArrivalTime() != null)
                arrivalTime = schedTimeFromStopTimes.getArrivalTime() + frequenciesBasedStartTime;
            Integer departureTime = null;
            if (schedTimeFromStopTimes.getDepartureTime() != null)
                departureTime = schedTimeFromStopTimes.getDepartureTime() + frequenciesBasedStartTime;

            ScheduleTime schedTimeFromFrequency = new ScheduleTime(arrivalTime, departureTime);
            this.scheduledTimesList.add(schedTimeFromFrequency);
        }

        // Since this constructor is only for frequency based trips where
        // exact_times is true set the corresponding members to indicate such
        this.noSchedule = false;
        this.exactTimesHeadway = true;
    }

    /**
     * Creates a copy of the Trip object but adjusts the startTime, endTime, and scheduledTimesMap
     * according to the frequenciesBasedStartTime. This is used when the frequencies.txt specifies a
     * time range for a trip but where exact_times is false. This is for noSchedule routes where
     * vehicle is expected to continuously run on a route without a schedule.
     *
     * @param tripFromStopTimes
     * @param frequenciesBasedStartTime
     * @param frequenciesBasedEndTime
     */
    public Trip(Trip tripFromStopTimes, int frequenciesBasedStartTime, int frequenciesBasedEndTime) {
        this.configRev = tripFromStopTimes.configRev;
        this.tripId = tripFromStopTimes.tripId;
        this.tripShortName = tripFromStopTimes.tripShortName;
        this.directionId = tripFromStopTimes.directionId;
        this.routeId = tripFromStopTimes.routeId;
        this.routeShortName = tripFromStopTimes.routeShortName;
        this.serviceId = tripFromStopTimes.serviceId;
        this.headsign = tripFromStopTimes.headsign;
        this.shapeId = tripFromStopTimes.shapeId;
        this.tripPattern = tripFromStopTimes.tripPattern;
        this.travelTimes = tripFromStopTimes.travelTimes;
        this.blockId = tripFromStopTimes.blockId;

        // Set the updated start and end times by using the times from the
        // frequency.txt GTFS file
        this.startTime = frequenciesBasedStartTime;
        this.endTime = frequenciesBasedEndTime;

        // Set the scheduledTimesMap by using the frequencies based start time
        this.scheduledTimesList.addAll(tripFromStopTimes.scheduledTimesList);

        // Since this constructor is only for frequency based trips where
        // exact_times is false set the corresponding members to indicate such
        this.noSchedule = true;
        this.exactTimesHeadway = false;
    }

    /** Hibernate requires no-arg constructor */
    @SuppressWarnings("unused")
    protected Trip() {
        configRev = -1;
        tripId = null;
        tripShortName = null;
        directionId = null;
        routeId = null;
        routeShortName = null;
        serviceId = null;
        headsign = null;
        blockId = null;
        shapeId = null;
        noSchedule = false;
        exactTimesHeadway = false;
    }

    /**
     * For refining the headsign. For some agencies like VTA & AC Transit the headsign includes the
     * route number at the beginning. This is indeed the headsign but not really appropriate as a
     * destination indicator. Ideally it might make sense to have an unprocessed headsign and a
     * separate destination indicator but for now lets just use headsign member. Also uses
     * TitleFormatter to deal with capitalization.
     *
     * @param gtfsHeadsign
     * @param routeId
     * @param titleFormatter
     * @return Processed headsign with proper formatting, or null if gtfsHeadsign passed in is null
     */
    private String processedHeadsign(String gtfsHeadsign, String routeId, TitleFormatter titleFormatter) {
        // Prevent NPE since gtfsHeadsign can be null
        if (gtfsHeadsign == null) return null;

        String headsignWithoutRouteInfo;
        if (gtfsHeadsign.startsWith(routeId)) {
            // Headsign starts with route ID so trim that off
            headsignWithoutRouteInfo = gtfsHeadsign.substring(routeId.length()).trim();

            // Handle possibility of having a separator between the route ID
            // and the rest of the headsign.
            if (headsignWithoutRouteInfo.startsWith(":") || headsignWithoutRouteInfo.startsWith("-"))
                headsignWithoutRouteInfo = headsignWithoutRouteInfo.substring(1).trim();
        } else
            // Headsign doesn't start with route ID so use entire string
            headsignWithoutRouteInfo = gtfsHeadsign;

        // Handle capitalization and any other title formatting necessary
        return titleFormatter.processTitle(headsignWithoutRouteInfo);
    }

    /**
     * For adding ScheduleTimes for stops to a Trip. Updates scheduledTimesMap, startTime, and
     * endTime.
     *
     * @param newScheduledTimesList
     * @throws ArrayIndexOutOfBoundsException If not enough space allocated for serialized schedule
     *     times in scheduledTimesMap column
     */
    public void addScheduleTimes(List<ScheduleTime> newScheduledTimesList) {
        // For each schedule time (one per stop path)
        for (ScheduleTime scheduleTime : newScheduledTimesList) {
            // Add the schedule time to the map
            scheduledTimesList.add(scheduleTime);

            // Determine the begin and end time. Assumes that times are added in order
            if (startTime == null || (scheduleTime.getDepartureTime() != null && scheduleTime.getDepartureTime() < startTime))
                startTime = scheduleTime.getDepartureTime();
            if (endTime == null || (scheduleTime.getArrivalTime() != null && scheduleTime.getArrivalTime() > endTime))
                endTime = scheduleTime.getArrivalTime();
        }

        // If resulting map takes up too much memory throw an exception.
        // Only bother checking if have at least a few schedule times.
        /*if (scheduledTimesList.size() > 5) {
        	int serializedSize = HibernateUtils.sizeof(scheduledTimesList);
        	if (serializedSize > scheduleTimesMaxBytes) {
        		String msg = "Too many elements in "
        				+ "scheduledTimesMap when constructing a "
        				+ "Trip. Have " + scheduledTimesList.size()
        				+ " schedule times taking up " + serializedSize
        				+ " bytes but only have " + scheduleTimesMaxBytes
        				+ " bytes allocated for the data. Trip="
        				+ this.toShortString();
        		logger.error(msg);

        		// Since this could be a really problematic issue, throw an error
        		throw new ArrayIndexOutOfBoundsException(msg);
        	}
        }*/
    }

    /**
     * TripPattern is created after the Trip. Therefore it cannot be set in constructor and instead
     * needs this set method.
     *
     * @param tripPattern
     */
    public void setTripPattern(TripPattern tripPattern) {
        this.tripPattern = tripPattern;
    }

    /**
     * TravelTimesForTrip created after the Trip. Therefore it cannot be set in constructor and
     * instead needs this set method.
     *
     * @param travelTimes
     */
    public void setTravelTimes(TravelTimesForTrip travelTimes) {
        this.travelTimes = travelTimes;
    }

    //    /* (non-Javadoc)
//     * @see java.lang.Object#toString()
//     */
//    @Override
//    public String toString() {
//        // Note: the '\n' at beginning is so that when output list of trips
//        // each will be on new line
//        return "\n    Trip ["
//                + "configRev="
//                + configRev
//                + ", tripId="
//                + tripId
//                + ", tripShortName="
//                + tripShortName
//                + ", tripPatternId="
//                + (tripPattern != null ? tripPattern.getId() : "null")
//                + ", tripIndexInBlock="
//                + getIndexInBlock()
//                + ", startTime="
//                + Time.timeOfDayStr(startTime)
//                + ", endTime="
//                + Time.timeOfDayStr(endTime)
//                + (headsign != null ? ", headsign=\"" + headsign + "\"" : "")
//                + ", directionId="
//                + directionId
//                + ", routeId="
//                + routeId
//                + ", routeShortName="
//                + routeShortName
//                + (noSchedule ? ", noSchedule=" + noSchedule : "")
//                + (exactTimesHeadway ? ", exactTimesHeadway=" + exactTimesHeadway : "")
//                + ", serviceId="
//                + serviceId
//                + ", blockId="
//                + blockId
//                + ", shapeId="
//                + shapeId
//                //				+ ", scheduledTimesList=" + scheduledTimesList
//                + "]";
//    }

//    /**
//     * Similar to toString() but also includes full TripPattern and travelTimes
//     *
//     * @return
//     */
//    public String toLongString() {
//        return "Trip ["
//                + "configRev="
//                + configRev
//                + ", tripId="
//                + tripId
//                + ", tripShortName="
//                + tripShortName
//                + ", tripPatternId="
//                + (tripPattern != null ? tripPattern.getId() : "null")
//                + ", tripPattern="
//                + tripPattern
//                + ", tripIndexInBlock="
//                + getIndexInBlock()
//                + ", startTime="
//                + Time.timeOfDayStr(startTime)
//                + ", endTime="
//                + Time.timeOfDayStr(endTime)
//                + (headsign != null ? ", headsign=\"" + headsign + "\"" : "")
//                + ", directionId="
//                + directionId
//                + ", routeId="
//                + routeId
//                + ", routeShortName="
//                + routeShortName
//                + (noSchedule ? ", noSchedule=" + noSchedule : "")
//                + (exactTimesHeadway ? ", exactTimesHeadway=" + exactTimesHeadway : "")
//                + ", serviceId="
//                + serviceId
//                + ", blockId="
//                + blockId
//                + ", shapeId="
//                + shapeId
//                + ", scheduledTimesList="
//                + scheduledTimesList
//                + ", travelTimes="
//                + travelTimes
//                + "]";
//    }

//    /**
//     * Similar to toString() but doesn't include scheduledTimesMap which can be quite verbose since
//     * it often contains times for many stops.
//     *
//     * @return
//     */
//    public String toShortString() {
//        return "Trip ["
//                + "tripId="
//                + tripId
//                + ", tripShortName="
//                + tripShortName
//                + ", tripPatternId="
//                + (tripPattern != null ? tripPattern.getId() : "null")
//                + ", tripIndexInBlock="
//                + getIndexInBlock()
//                + ", startTime="
//                + Time.timeOfDayStr(startTime)
//                + ", endTime="
//                + Time.timeOfDayStr(endTime)
//                + (headsign != null ? ", headsign=\"" + headsign + "\"" : "")
//                + ", directionId="
//                + directionId
//                + ", routeId="
//                + routeId
//                + ", routeShortName="
//                + routeShortName
//                + (noSchedule ? ", noSchedule=" + noSchedule : "")
//                + (exactTimesHeadway ? ", exactTimesHeadway=" + exactTimesHeadway : "")
//                + ", serviceId="
//                + serviceId
//                + ", blockId="
//                + blockId
//                + ", shapeId="
//                + shapeId
//                + "]";
//    }

    /**
     * @return the tripId
     */
    public String getId() {
        return tripId;
    }

    /**
     * @return the tripShortName
     */
    public String getShortName() {
        return tripShortName;
    }

    /**
     * Returns the routeShortName. If it is null then returns the full route name. Causes exception
     * if Core not available, such as when processing GTFS data.
     *
     * @return the routeShortName
     */
    public String getRouteShortName(DbConfig dbConfig) {
        return routeShortName != null ? routeShortName : getRouteName(dbConfig);
    }

    /**
     * Returns the Route object for this trip. This object is determined and cached when first
     * accessed. Uses value read in from database using Core, which means that it won't be available
     * when processing GTFS data since that doesn't have core object.
     *
     * @return The route or null if no Core object available
     */
    public Route getRoute(DbConfig dbConfig) {
        if (route == null) {
            route = dbConfig.getRouteById(routeId);
        }
        return route;
    }

    /**
     * Returns route name. Gets it from the Core database configuration. If Core database
     * configuration not available such as when processing GTFS data then will return null. Will
     * cause exception if core not available and gtfs data not loaded in db yet since the active
     * revisions will not be set properly yet.
     *
     * @return The route name or null if Core object not available
     */
    public String getRouteName(DbConfig dbConfig) {
        Route route = getRoute(dbConfig);
        if (route == null) {
            return null;
        }

        return route.getName();
    }

    /**
     * For modifying the headsign. Useful for when reading in GTFS data and determine that the
     * headsign should be modified because it is for a different last stop or such.
     *
     * @param headsign
     */
    public void setHeadsign(String headsign) {
        this.headsign = headsign.length() <= TripPattern.HEADSIGN_LENGTH
                ? headsign
                : headsign.substring(0, TripPattern.HEADSIGN_LENGTH);
    }

    /**
     * Returns the Block that the Trip is associated with. Only valid when running the core
     * application where can use Core.getInstance(). Otherwise returns null.
     *
     * @return
     */
    public Block getBlock(DbConfig dbConfig) {
        // Part of core project so return the Block
        return dbConfig.getBlock(serviceId, blockId);
    }

    /**
     * Returns the index of the trip in the block. Uses DbConfig from the core project to determine
     * the Block. If not part of the core project the Block info is not available and -1 is
     * returned.
     *
     * @return The index of the trip in the block or -1 if block info not available.
     */
    public int getIndexInBlock(DbConfig dbConfig) {
        // If block info no available then simply return -1
        Block block = getBlock(dbConfig);
        if (block == null) return -1;

        // Block info available so return the trip index
        return block.getTripIndex(this);
    }

    /**
     * Returns the ScheduleTime object for the stopPathIndex. Will return null if there are no
     * schedule times associated with that stop for this trip. Useful for determining schedule
     * adherence.
     *
     * @param stopPathIndex
     * @return
     */
    @Nullable
    public ScheduleTime getScheduleTime(int stopPathIndex) {
        if (scheduledTimesList instanceof PersistentList<?> persistentListTimes) {
            // TODO this is an anti-pattern
            // instead find a way to manage sessions more consistently
            var session = persistentListTimes.getSession();
            if (session == null) {
                Session globalLazyLoadSession = HibernateUtils.getSession();
                globalLazyLoadSession.merge(this);
            }
        }
        return scheduledTimesList.get(stopPathIndex);
    }

    /**
     * @return list of schedule times for the trip
     */
    public List<ScheduleTime> getScheduleTimes() {
        return scheduledTimesList;
    }

    /**
     * Returns the travel time info for the path specified by the stopPathIndex.
     *
     * @param stopPathIndex
     * @return
     */
    public TravelTimesForStopPath getTravelTimesForStopPath(int stopPathIndex) {
        return travelTimes.getTravelTimesForStopPath(stopPathIndex);
    }

    /**
     * Returns length of the trip from the first terminal to the last.
     *
     * @return
     */
    public double getLength() {
        return getTripPattern().getLength();
    }

    /**
     * Returns the stop ID of the last stop of the trip. This is the destination for the trip.
     *
     * @return ID of last stop
     */
    public String getLastStopId() {
        return getTripPattern().getLastStopIdForTrip();
    }

    /**
     * Returns the List of the stop paths for the trip pattern
     *
     * @return
     */
    public List<StopPath> getStopPaths() {
        return tripPattern.getStopPaths();
    }

    /**
     * Returns the StopPath for the stopPathIndex specified
     *
     * @param stopPathIndex
     * @return the path specified or null if index out of range
     */
    public StopPath getStopPath(int stopPathIndex) {
        return tripPattern.getStopPath(stopPathIndex);
    }

    /**
     * Returns the StopPath specified by the stopId.
     *
     * @param stopId
     * @return The specified StopPath, or null if the stop is not part of this trip pattern.
     */
    public StopPath getStopPath(String stopId) {
        return tripPattern.getStopPath(stopId);
    }

    /**
     * Returns number of stop paths defined for this trip.
     *
     * @return Number of stop paths
     */
    public int getNumberStopPaths() {
        return getTripPattern().getStopPaths().size();
    }

    /**
     * Callback due to implementing Lifecycle interface. Used to compact string members by interning
     * them.
     */
    @Override
    public void onLoad(Session s, Object id) throws CallbackException {
        if (tripId != null) tripId = tripId.intern();
        if (tripShortName != null) tripShortName = tripShortName.intern();
        if (directionId != null) directionId = directionId.intern();
        if (routeId != null) routeId = routeId.intern();
        if (routeShortName != null) routeShortName = routeShortName.intern();
        if (serviceId != null) serviceId = serviceId.intern();
        if (headsign != null) headsign = headsign.intern();
        if (blockId != null) blockId = blockId.intern();
        if (shapeId != null) shapeId = shapeId.intern();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trip trip)) return false;
        return configRev == trip.configRev
            && noSchedule == trip.noSchedule
            && exactTimesHeadway == trip.exactTimesHeadway
            && Objects.equals(tripId, trip.tripId)
            && Objects.equals(startTime, trip.startTime)
            && Objects.equals(tripShortName, trip.tripShortName)
            && Objects.equals(endTime, trip.endTime)
            && Objects.equals(directionId, trip.directionId)
            && Objects.equals(routeId, trip.routeId)
            && Objects.equals(routeShortName, trip.routeShortName)
            && Objects.equals(tripPattern, trip.tripPattern)
            && Objects.equals(travelTimes, trip.travelTimes)
            && Objects.equals(scheduledTimesList, trip.scheduledTimesList)
            && Objects.equals(serviceId, trip.serviceId)
            && Objects.equals(headsign, trip.headsign)
            && Objects.equals(blockId, trip.blockId)
            && Objects.equals(shapeId, trip.shapeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configRev, tripId, startTime, tripShortName, endTime, directionId, routeId, routeShortName, tripPattern, travelTimes, scheduledTimesList, noSchedule, exactTimesHeadway, serviceId, headsign, blockId, shapeId);
    }
}
