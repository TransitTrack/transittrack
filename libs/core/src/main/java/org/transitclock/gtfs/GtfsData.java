/* (C)2023 */
package org.transitclock.gtfs;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import org.transitclock.domain.repository.RouteRepository;
import org.transitclock.domain.structs.ActiveRevision;
import org.transitclock.domain.structs.Agency;
import org.transitclock.domain.structs.Block;
import org.transitclock.domain.structs.Calendar;
import org.transitclock.domain.structs.CalendarDate;
import org.transitclock.domain.structs.ConfigRevision;
import org.transitclock.domain.structs.FareAttribute;
import org.transitclock.domain.structs.FareRule;
import org.transitclock.domain.structs.Frequency;
import org.transitclock.domain.structs.Location;
import org.transitclock.domain.structs.Route;
import org.transitclock.domain.structs.ScheduleTime;
import org.transitclock.domain.structs.Stop;
import org.transitclock.domain.structs.StopPath;
import org.transitclock.domain.structs.Transfer;
import org.transitclock.domain.structs.Trip;
import org.transitclock.domain.structs.TripPattern;
import org.transitclock.domain.structs.TripPatternKey;
import org.transitclock.gtfs.model.GtfsAgency;
import org.transitclock.gtfs.model.GtfsCalendar;
import org.transitclock.gtfs.model.GtfsCalendarDate;
import org.transitclock.gtfs.model.GtfsFareAttribute;
import org.transitclock.gtfs.model.GtfsFareRule;
import org.transitclock.gtfs.model.GtfsFrequency;
import org.transitclock.gtfs.model.GtfsRoute;
import org.transitclock.gtfs.model.GtfsShape;
import org.transitclock.gtfs.model.GtfsStop;
import org.transitclock.gtfs.model.GtfsStopTime;
import org.transitclock.gtfs.model.GtfsTransfer;
import org.transitclock.gtfs.model.GtfsTrip;
import org.transitclock.gtfs.readers.GtfsAgenciesSupplementReader;
import org.transitclock.gtfs.readers.GtfsAgencyReader;
import org.transitclock.gtfs.readers.GtfsCalendarDatesReader;
import org.transitclock.gtfs.readers.GtfsCalendarReader;
import org.transitclock.gtfs.readers.GtfsFareAttributesReader;
import org.transitclock.gtfs.readers.GtfsFareRulesReader;
import org.transitclock.gtfs.readers.GtfsFrequenciesReader;
import org.transitclock.gtfs.readers.GtfsRoutesReader;
import org.transitclock.gtfs.readers.GtfsRoutesSupplementReader;
import org.transitclock.gtfs.readers.GtfsShapesReader;
import org.transitclock.gtfs.readers.GtfsShapesSupplementReader;
import org.transitclock.gtfs.readers.GtfsStopTimesReader;
import org.transitclock.gtfs.readers.GtfsStopTimesSupplementReader;
import org.transitclock.gtfs.readers.GtfsStopsReader;
import org.transitclock.gtfs.readers.GtfsStopsSupplementReader;
import org.transitclock.gtfs.readers.GtfsTransfersReader;
import org.transitclock.gtfs.readers.GtfsTripsReader;
import org.transitclock.gtfs.readers.GtfsTripsSupplementReader;
import org.transitclock.gtfs.readers.ReaderHelper;
import org.transitclock.utils.Geo;
import org.transitclock.utils.IntervalTimer;
import org.transitclock.utils.MapKey;
import org.transitclock.utils.StringUtils;
import org.transitclock.utils.Time;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;

/**
 * Contains all the GTFS data processed into Java lists and such. Also combines in info from
 * supplemental routes.txt file if there is one.
 *
 * @author SkiBu Smith
 */
@Slf4j
@Getter
public class GtfsData {
    private final GtfsProcessingConfig config;
    private final Session session;

    // Various params set by constructor
    private final ActiveRevision revs;
    // For when zip file used. Null otherwise
    private final Date zipFileLastModifiedTime;
    private final int originalTravelTimesRev;

    private final TitleFormatter titleFormatter;
    private final ReaderHelper readerHelper;
    private final GtfsFilter gtfsFilter;

    private Map<String, GtfsRoute> gtfsRoutesMap;
    private Map<String, GtfsStop> gtfsStopsMap;
    private Map<String, GtfsTrip> gtfsTripsMap;

    private Map<String, Route> routesMap;
    private Map<String, Stop> stopsMap;

    // For keeping track of which routes are just sub-routes of a parent.
    // For these the route will not be configured separately and the
    // route IDs from trips.txt and fare_rules.txt will be set to the
    // parent ID.
    private final Map<String, String> properRouteIdMap = new HashMap<>();



    // From stop_times.txt file
    private Map<String, List<GtfsStopTime>> gtfsStopTimesForTripMap; // Key is trip_id
    private Collection<Trip> tripsCollection;

    // Want to lookup trip patterns and only keep around
    // unique ones. Also, want to have each trip pattern
    // know which trips use it. This means that TripPattern
    // needs list of GTFS trips. To do all of this need to
    // use a map. The key needs to be a TripPatternKey so
    // make sure the patterns are unique. The map contains
    // TripPatterns as the values so that can update
    // the trips list when another Trip is found to
    // use that TripPattern.
    private Map<TripPatternKey, TripPattern> tripPatternMap;

    // Also need to be able to get trip patterns associated
    // with a route so can be included in Route object.
    // Key is routeId.
    private Map<String, List<TripPattern>> tripPatternsByRouteIdMap;

    // So can convert from a Trip to a TripPattern. The key
    // is tripId.
    private Map<String, TripPattern> tripPatternsByTripIdMap;

    // So can make sure that each tripPattern gets a unique ID
    // even when really screwy things are done such as use the same
    // shapeId for different trip patterns.
    private Set<String> tripPatternIdSet;

    // So can know which service IDs have trips so that can remove
    // calendars for ones that do not.
    private Set<String> serviceIdsWithTrips;

    // List of all the blocks, random order
    private List<Block> blocks;

    // Keyed on tripPatternId and pathId using getPathMapKey(tripPatternId, pathId)
    private HashMap<String, StopPath> pathsMap;

    private List<Calendar> calendars;

    private List<CalendarDate> calendarDates;

    private Set<String> validServiceIds;

    // Data for the other GTFS files
    // Key for frequencyMap is trip_id. Values are a List of Frequency objects
    // since each trip can be listed in frequencies.txt file multiple times in
    // order to define a different headway for different time ranges.
    private Map<String, List<Frequency>> frequencyMap;

    private List<Agency> agencies;

    private List<FareAttribute> fareAttributes;

    private List<FareRule> fareRules;

    private List<Transfer> transfers;

    // This is the format that dates are in for CSV. Should
    // be accessed only through getDateFormatter() to make
    // sure that it is initialized.
    private SimpleDateFormat dateFormatter;

    public GtfsData(
            GtfsProcessingConfig config,
            Session session,
            int configRev,
            Date zipFileLastModifiedTime,
            boolean shouldStoreNewRevs,
            TitleFormatter titleFormatter,
            ReaderHelper readerHelper,
            GtfsFilter filter
    ) {
        this.config = config;
        // Get the database session. Using one session for the whole process.
        this.session = session;

        this.zipFileLastModifiedTime = zipFileLastModifiedTime;
        this.titleFormatter = titleFormatter;
        this.readerHelper = readerHelper;
        this.gtfsFilter = filter;

        // Deal with the ActiveRevisions. First, store the original travel times
        // rev since need it to read in old travel time data.
        ActiveRevision originalRevs = ActiveRevision.get(session);
        this.originalTravelTimesRev = originalRevs.getTravelTimesRev();

        // If we should store the new revs in database (make them active)
        // then use the originalRevs read from db since they will be
        // written out when session is closed
        if (shouldStoreNewRevs) {
            // Use the originalRevs object which was read from the db.
            // When revs is updated then originalRevs is updated and
            // Hibernate will store the changes to originalRevs to
            // the db when the session is closed.
            revs = originalRevs;
        } else {
            // Don't need to store new revs in db so use a transient object
            revs = new ActiveRevision();
        }
        // If particular configuration rev specified then use it. This way
        // can write over existing configuration revisions.
        if (configRev >= 0) {
            revs.setConfigRev(configRev);
        } else {
            revs.setConfigRev(revs.getConfigRev() + 1);
        }

        // Increment the travel times rev
        revs.setTravelTimesRev(originalTravelTimesRev + 1);

        // Log which revisions are being used
        logger.info("Will be writing data to revisions {}", revs);
    }

    /**
     * Creates the static dateFormatter using the specified timezone. The timezone name is obtained
     * from the first agency in the agency.txt file.
     */
    private DateFormat getDateFormatter() {
        if (dateFormatter != null) {
            return dateFormatter;
        }

        // The dateFormatter not yet read in.
        // First, read in the agency.txt GTFS data from file
        GtfsAgencyReader agencyReader = new GtfsAgencyReader(config.getGtfsDirectoryName());
        List<GtfsAgency> gtfsAgencies = agencyReader.get();
        if (gtfsAgencies.isEmpty()) {
            logger.error(
                    "Could not read in {}/agency.txt file, which is " + "needed for createDateFormatter()",
                    config.getGtfsDirectoryName());
            System.exit(-1);
        }
        String timezoneName = gtfsAgencies.get(0).getAgencyTimezone();

        // Create the dateFormatter with the proper timezone
        TimeZone timezone = TimeZone.getTimeZone(timezoneName);
        dateFormatter = new SimpleDateFormat("yyyyMMdd");
        dateFormatter.setTimeZone(timezone);

        return dateFormatter;
    }

    /**
     * Reads routes.txt files from both gtfsDirectoryName and supplementDir and combines them
     * together. End result is that gtfsRoutesMap is created and filled in.
     */
    private void processRouteData() {
        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Processing routes.txt data...");

        // Read in standard route data
        GtfsRoutesReader routesReader = new GtfsRoutesReader(config.getGtfsDirectoryName(), gtfsFilter);
        List<GtfsRoute> gtfsRoutes = routesReader.get();

        // Put GtfsRoute objects in Map so easy to find the right ones.
        // HashMap is keyed on the route_id.
        gtfsRoutesMap = new HashMap<>(gtfsRoutes.size());
        for (GtfsRoute r : gtfsRoutes) {
            // If this route is just a subset of another route
            // then can ignore it. But put in the map so that
            // can adjust trips and fare_rules to use the proper
            // parent route ID.
            if (r.getParentRouteId() != null) {
                properRouteIdMap.put(r.getRouteId(), r.getParentRouteId());
            } else {
                // Use normal route ID
                properRouteIdMap.put(r.getRouteId(), r.getRouteId());
            }

            // Add the gtfs route to the map now that we can get the proper
            // route ID.
            gtfsRoutesMap.put(getProperIdOfRoute(r.getRouteId()), r);
        }

        // Read in supplemental route data
        if (config.hasSupplementDir()) {
            // Read in the supplemental route data
            GtfsRoutesSupplementReader routesSupplementReader = new GtfsRoutesSupplementReader(config.getGtfsDirectoryName());
            List<GtfsRoute> gtfsRoutesSupplement = routesSupplementReader.get();

            // Modify the main GtfsRoute objects using the supplemental data
            for (GtfsRoute supplementRoute : gtfsRoutesSupplement) {
                // Determine the original GtfsRoute that the supplemental
                // data corresponds to
                GtfsRoute originalGtfsRoute = null;
                if (supplementRoute.getRouteId() != null) {
                    // Using regular route_id in supplemental routes.txt file.
                    // Look up the GTFS route by the proper route ID.
                    String routeMapKey = getProperIdOfRoute(supplementRoute.getRouteId());
                    originalGtfsRoute = gtfsRoutesMap.get(routeMapKey);
                } else {
                    // Must be using route short name as ID. Therefore
                    // cannot use the routes hashmap directly.
                    String routeShortName = supplementRoute.getRouteShortName();
                    for (GtfsRoute gtfsRoute : gtfsRoutes) {
                        if (gtfsRoute.getRouteShortName().equals(routeShortName)) {
                            originalGtfsRoute = gtfsRoute;
                            break;
                        }
                    }
                }

                if (originalGtfsRoute != null) {
                    // Found the original GTFS route that the supplemental data
                    // is associated with so create a new GTFSRoute object that
                    // combines the original data with the supplemental data.
                    GtfsRoute combinedRoute = new GtfsRoute(originalGtfsRoute, supplementRoute);

                    // Store that combined data route in the map
                    gtfsRoutesMap.put(combinedRoute.getRouteId(), combinedRoute);
                } else {
                    // Didn't find the original route with the same ID as the
                    // supplemental route so log warning that supplemental file
                    // is not correct.
                    logger.warn(
                            "Found route data in supplemental file but "
                                    + "there is no such route with the ID in the "
                                    + "main routes.txt file. Therefore could not use "
                                    + "the supplemental data for this route. "
                                    + "supplementRoute={}",
                            supplementRoute);
                }
            }
        }

        // Let user know what is going on
        logger.info("processRouteData() finished processing routes.txt " + "data. Took {} msec.", timer.elapsedMsec());
    }

    /**
     * Takes data from gtfsRoutesMap and creates corresponding Route map. Processes all of the
     * titles to make them more readable. Creates corresponding Route objects and stores them into
     * the database. This method is separated out from processRouteData() since reading trips needs
     * the gtfs route info but reading Routes requires trips.
     *
     * <p>Also sets route order for when route_order not specified in GTFS. This is important so
     * that route order is always stored as part of the route in the db so can join the routes table
     * with other tables and then sort the results by route order, all within a SQL call.
     */
    private void processRouteMaps() {
        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Processing Routes objects data in processRouteMaps()...");

        // Make sure needed data is already read in. This method uses
        // trips and trip patterns from the stop_time.txt file. This objects
        // need to know lat & lon so can figure out bounding box. Therefore
        // stops.txt file must be read in first.
        if (gtfsTripsMap == null || gtfsTripsMap.isEmpty()) {
            logger.error("processTripsData() must be called before " + "GtfsData.processRouteMaps() is. Exiting.");
            System.exit(-1);
        }
        if (gtfsRoutesMap == null || gtfsRoutesMap.isEmpty()) {
            logger.error("processRouteData() must be called before " + "GtfsData.processRouteMaps() is. Exiting.");
            System.exit(-1);
        }

        // Now that the GtfsRoute objects have been created, create
        // the corresponding map of Route objects
        routesMap = new HashMap<>();
        Set<String> routeIds = gtfsRoutesMap.keySet();
        int numberOfRoutesWithoutTrips = 0;
        for (String routeId : routeIds) {
            GtfsRoute gtfsRoute = gtfsRoutesMap.get(routeId);

            // If route is to be ignored then continue
            if (gtfsRoute.shouldRemove()) continue;

            // If this route is just a subset of another route
            // then can ignore it.
            if (gtfsRoute.getParentRouteId() != null) {
                continue;
            }

            // Determine the trip patterns for the route so that they
            // can be included when constructing the route object.
            // If there aren't any then can't
            List<TripPattern> tripPatternsForRoute = getTripPatterns(routeId);
            if (tripPatternsForRoute == null || tripPatternsForRoute.isEmpty()) {
                logger.warn(
                        "Route \"{}\" route_id={} was defined on line #{} in "
                                + "routes.txt file but does not have any associated trips "
                                + "defined in the stop_times.txt file. Therefore that route "
                                + "has been removed from the configuration.",
                        gtfsRoute.getRouteLongName() != null
                                ? gtfsRoute.getRouteLongName()
                                : gtfsRoute.getRouteShortName(),
                        routeId,
                        gtfsRoute.getLineNumber());
                ++numberOfRoutesWithoutTrips;
                continue;
            }

            // Create the route object and add it to the container
            Route route = new Route(revs.getConfigRev(), gtfsRoute, tripPatternsForRoute, titleFormatter);
            routesMap.put(routeId, route);
        }

        // Sort the routes so that can determine the route order for each one.
        // Uses GTFS route_order when available and uses route_name when not.
        ArrayList<Route> routes = new ArrayList<>(routesMap.values());
        routes.sort(RouteRepository.routeComparator);

        // Determine and set route order for each route if it is not already set
        int routeOrderCounter = 0;
        for (Route route : routes) {
            if (route.getRouteOrder() == null) {
                // Route order not set in GTFS so use current routeOrderCounter
                route.setRouteOrder(routeOrderCounter++);
            } else {
                // Route order set in GTFS so remember the new value
                routeOrderCounter = route.getRouteOrder() + 1;
            }
        }

        // Summarize how many problem routes there are that don't have trips
        if (numberOfRoutesWithoutTrips > 0) {
            logger.warn(
                    "Found {} routes without trips in stop_times.txt "
                            + "out of a total of {} routes defined in the routes.txt file.",
                    numberOfRoutesWithoutTrips,
                    routeIds.size());
        }

        // Let user know what is going on
        logger.info(
                "Finished processing Routes objects data in processRouteData(). Took {} msec.", timer.elapsedMsec());
    }

    /**
     * Reads stops.txt files from both gtfsDirectoryName and supplementDir and combines them
     * together. Processes all of the titles to make them more readable. Puts the stops into the
     * gtfsStopsMap and stopsMap members.
     */
    private void processStopData() {
        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Processing stops.txt data...");

        // Read in standard route data
        GtfsStopsReader stopsReader = new GtfsStopsReader(config.getGtfsDirectoryName());
        List<GtfsStop> gtfsStops = stopsReader.get();

        // Put GtfsStop objects in Map so easy to find the right ones
        gtfsStopsMap = new HashMap<>(gtfsStops.size());
        for (GtfsStop gtfsStop : gtfsStops) {
            gtfsStopsMap.put(gtfsStop.getStopId(), gtfsStop);
        }

        // Read in supplemental stop data
        if (config.hasSupplementDir()) {
            // Read in the supplemental stop data
            GtfsStopsSupplementReader stopsSupplementReader = new GtfsStopsSupplementReader(config.getGtfsDirectoryName());
            List<GtfsStop> gtfsStopsSupplement = stopsSupplementReader.get();

            // Modify the main GtfsStop objects using the supplemental data
            for (GtfsStop supplementStop : gtfsStopsSupplement) {
                GtfsStop gtfsStop = gtfsStopsMap.get(supplementStop.getStopId());
                if (gtfsStop == null) {
                    logger.error(
                            "Found supplemental stop data for stopId={} "
                                    + "but that stop did not exist in the main "
                                    + "stops.txt file. {}",
                            supplementStop.getStopId(),
                            supplementStop);
                    continue;
                }

                // Create a new GtfsStop object that combines the original
                // data with the supplemental data
                GtfsStop combinedStop = new GtfsStop(gtfsStop, supplementStop);

                // Store that combined data stop in the map
                gtfsStopsMap.put(combinedStop.getStopId(), combinedStop);
            }
        }

        // Create the map of the Stop objects. Use a ConcurrentHashMap instead
        // of a regular HashMap so that trimStops() can delete unused stops
        // while iterating across the hash map.
        stopsMap = new ConcurrentHashMap<>(gtfsStops.size());
        for (GtfsStop gtfsStop : gtfsStopsMap.values()) {
            Stop stop = new Stop(revs.getConfigRev(), gtfsStop, config.getStopCodeBaseValue(), titleFormatter);
            stopsMap.put(stop.getId(), stop);
        }

        // Let user know what is going on
        logger.info("Finished processing stops.txt data. Took {} msec.", timer.elapsedMsec());
    }

    /**
     * Sometimes will be using a partial configuration. For example, for MBTA commuter rail only
     * want to use the trips defined for commuter rail even though the GTFS data can have trips for
     * other modes defined. This can mean that the data includes many stops that are actually not
     * used by the subset of trips. Therefore trim out the unused stops from the stopsMap member.
     */
    private void trimStops() {
        // Make sure needed data is already read in. T
        if (stopsMap == null || stopsMap.isEmpty()) {
            logger.error("processStopData() must be called before " + "GtfsData.trimStops() is. Exiting.");
            System.exit(-1);
        }

        if (tripPatternMap == null || tripPatternMap.isEmpty()) {
            logger.error("createTripsAndTripPatterns() must be called before " + "GtfsData.trimStops() is. Exiting.");
            System.exit(-1);
        }

        // Determine all the stops used in the trip patterns
        Set<String> stopIdsUsed = new HashSet<>();
        for (TripPattern tripPattern : tripPatternMap.values()) {
            stopIdsUsed.addAll(tripPattern.getStopIds());
        }

        // Remove any stops not in trip patterns from the stopsMap
        for (String stopId : stopsMap.keySet()) {
            if (!stopIdsUsed.contains(stopId)) {
                // The stop is not actually used so remove it from stopsMap
                stopsMap.remove(stopId);
            }
        }
    }

    /** Reads data from trips.txt and puts it into gtfsTripsMap. */
    private void processTripsData() {
        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Make sure needed data is already read in. This method uses
        // GtfsRoutes info to make sure all trips reference a route.
        if (gtfsRoutesMap == null || gtfsRoutesMap.isEmpty()) {
            logger.error("processGtfsRouteMap() must be called before "
                    + "GtfsData.processTripsData() is or no routes were "
                    + "found. Exiting.");
            System.exit(-1);
        }

        // Let user know what is going on
        logger.info("Processing trips.txt data...");

        // Create the map where the data is going to go
        gtfsTripsMap = new HashMap<>();

        // Read in the trips.txt GTFS data from file
        GtfsTripsReader tripsReader = new GtfsTripsReader(config.getGtfsDirectoryName(), gtfsFilter, readerHelper);
        List<GtfsTrip> gtfsTrips = tripsReader.get();

        // For each GTFS trip make sure route is OK and and the trip to the
        // gtfsTripsMap.
        for (GtfsTrip gtfsTrip : gtfsTrips) {
            // Make sure that each trip references a valid route. If it does
            // not then something is fishy with the data so output a warning.
            if (getGtfsRoute(gtfsTrip.getRouteId()) != null) {
                // Refers to a valid route so we should process this trip
                gtfsTripsMap.put(gtfsTrip.getTripId(), gtfsTrip);
            } else {
                logger.warn(
                        "The trip id={} in the trips.txt file at "
                                + "line # {} refers to route id={} but that route is "
                                + "not in the routes.txt file. Therefore "
                                + "that trip is being discarded.",
                        gtfsTrip.getTripId(),
                        gtfsTrip.getLineNumber(),
                        gtfsTrip.getRouteId());
            }
        }

        // Read in supplemental trips data
        if (config.hasSupplementDir()) {
            // Read in the supplemental trip data
            GtfsTripsSupplementReader tripsSupplementReader = new GtfsTripsSupplementReader(config.getSupplementDir(), readerHelper);
            List<GtfsTrip> gtfsTripsSupplement = tripsSupplementReader.get();

            // Modify the main GtfsTrip objects using the supplemental data
            for (GtfsTrip supplementTrip : gtfsTripsSupplement) {
                // First try matching supplemental trip to the regular trip
                // using trip ID
                String supplementTripId = supplementTrip.getTripId();
                GtfsTrip gtfsTrip = null;
                if (supplementTripId != null) {
                    // Determine the GTFS trip that has the same trip id
                    // as the supplemental GTFS trip
                    gtfsTrip = gtfsTripsMap.get(supplementTripId);
                    if (gtfsTrip == null) {
                        logger.error(
                                "Found supplemental trip data for "
                                        + "tripId={} on line {} but that trip did not "
                                        + "exist in the main trips.txt file. {}",
                                supplementTripId,
                                supplementTrip.getLineNumber(),
                                supplementTrip);
                        continue;
                    }
                } else {
                    // trip ID wasn't specified in supplemental trips.txt so try
                    // identifying trip using the trip short name
                    String supplementTripShortName = supplementTrip.getTripShortName();

                    if (supplementTripShortName != null) {
                        // Determine the GTFS trip that has the same trip short
                        // name as the supplemental GTFS trip
                        for (GtfsTrip gTrip : gtfsTrips) {
                            if (supplementTripShortName.equals(gTrip.getTripShortName())) {
                                gtfsTrip = gTrip;
                                break;
                            }
                        }
                        if (gtfsTrip == null) {
                            logger.error(
                                    "Found supplemental trip data for "
                                            + "tripShortName={} on line {} but that "
                                            + "trip did not exist in "
                                            + "the main trips.txt file. {}",
                                    supplementTripShortName,
                                    supplementTrip.getLineNumber(),
                                    supplementTrip);
                            continue;
                        }
                    } else {
                        logger.error(
                                "Neither trip_id nor trip_short_name "
                                        + "specified for supplemental trip data on "
                                        + "line {}. ",
                                supplementTrip.getLineNumber());
                        continue;
                    }
                }

                // Create a new GtfsStop object that combines the original
                // data with the supplemental data
                GtfsTrip combinedTrip = new GtfsTrip(gtfsTrip, supplementTrip);

                // Store that combined data stop in the map
                gtfsTripsMap.put(combinedTrip.getTripId(), combinedTrip);
            }
        }

        // Let user know what is going on
        logger.info("Finished processing trips.txt data. Took {} msec.", timer.elapsedMsec());
    }

    /**
     * Sorts gtfsStopTimesForTrip and then goes through the data to make sure it is OK. If data is a
     * real problem, like a duplicate stop, it is not included in the returned list. If there is a
     * duplicate stop and the times are different then it is assumed that it is a wait stop and the
     * arrival time from the first stop entry and the departure time for the second stop entry are
     * used. Any problems found are logged.
     *
     * @return Processed/cleaned up list of GtfsStopTime for the trip
     */
    private List<GtfsStopTime> processStopTimesForTrip(List<GtfsStopTime> gtfsStopTimesForTrip) {
        // For returning results
        List<GtfsStopTime> processedGtfsStopTimesForTrip = new ArrayList<>();

        // Sort the list so that the stop times are in sequence order.
        // This way can treat first and last stop times for a trip
        // specially. Plus want them in order to determine trip patterns
        // and such.
        gtfsStopTimesForTrip.sort(null);

        // Iterate over stop times for trip and remove inappropriate ones.
        // Also, log any warning or error messages.
        String previousStopId = null;
        boolean firstStopInTrip = true;
        int previousTimeForTrip = 0;
        for (GtfsStopTime gtfsStopTime : gtfsStopTimesForTrip) {
            // Convenience variable
            String tripId = gtfsStopTime.getTripId();

            // There can be a situation where the agency defines a stop as the
            // start terminal of a trip but where the vehicle actually starts
            // the trip at a subsequent stop. Yes, the agency should fix this
            // data but they won't. An example is sfmta 21-Hayes route
            // downtown where the first stop defined in config is wrong.
            // Therefore filter out such stops. Note that only filtering
            // out such stops if they are the first stops in the trip.
            GtfsStop gtfsStop = getGtfsStop(gtfsStopTime.getStopId());
            if (gtfsStop == null) continue;
            if (gtfsStop.getDeleteFromRoutesStr() != null || (firstStopInTrip && gtfsStop.getDeleteFirstStopFromRoutesStr() != null)) {
                GtfsTrip gtfsTrip = getGtfsTrip(gtfsStopTime.getTripId());
                GtfsRoute gtfsRoute = getGtfsRoute(gtfsTrip.getRouteId());
                String routeShortName = gtfsRoute.getRouteShortName();
                String deleteFromRoutesStr = gtfsStop.getDeleteFromRoutesStr() != null
                        ? gtfsStop.getDeleteFromRoutesStr()
                        : gtfsStop.getDeleteFirstStopFromRoutesStr();
                if (gtfsStop.shouldDeleteFromRoute(routeShortName, deleteFromRoutesStr)) {
                    continue;
                }
            }

            // If the GtfsStopTime refers to a non-existent stop than log an error
            if (getStop(gtfsStopTime.getStopId()) == null) {
                logger.error(
                        "In stop_times.txt line {} refers to stop_id {} but "
                                + "it is not defined in the stops.txt file. Therefore this "
                                + "stop will be ignored for trip {}.",
                        gtfsStopTime.getLineNumber(),
                        gtfsStopTime.getStopId(),
                        gtfsStopTime.getTripId());
                continue;
            }

            // Make sure arrival/departure times OK.
            Integer arr = gtfsStopTime.getArrivalTimeSecs();
            Integer dep = gtfsStopTime.getDepartureTimeSecs();
            // Make sure that first stop has a departure time and the
            // last one has an arrival time.
            if (firstStopInTrip && dep == null) {
                if (arr == null) {
                    logger.error(
                            "First stop in trip {} does not have a "
                                    + "departure time and no arrival time either. "
                                    + "The problem is in the "
                                    + "stop_times.txt file at line {}. ",
                            tripId,
                            getGtfsTrip(tripId).getLineNumber());
                } else {
                    logger.error(
                            "First stop in trip {} does not have a "
                                    + "departure time. The problem is in the "
                                    + "stop_times.txt file at line {}. Using arrival "
                                    + "time of {} as the departure time.",
                            tripId,
                            getGtfsTrip(tripId).getLineNumber(),
                            Time.timeOfDayStr(arr));
                    dep = arr;
                }
            }
            boolean lastStopInTrip = gtfsStopTime == gtfsStopTimesForTrip.get(gtfsStopTimesForTrip.size() - 1);
            if (lastStopInTrip && arr == null) {
                logger.error(
                        "Last stop in trip {} does not have an arrival "
                                + "time. The problem is in the stop_times.txt file at "
                                + "line {}.",
                        tripId,
                        getGtfsTrip(tripId).getLineNumber());
            }

            // Make sure that not listing the same stop twice in a row.
            if (gtfsStopTime.getStopId().equals(previousStopId)) {
                // If same time for same stop then filter out the duplicate stop.
                // Yes, SFMTA actually has done this!
                if (arr == null || arr == previousTimeForTrip) {
                    // This stop doesn't have an arrival time or it is an
                    // identical time to the previous stop which means it
                    // it is an uneeded duplicated. Therefore don't use it.
                    logger.warn(
                            "Encountered stopId={} twice in a row with the "
                                    + "same times for tripId={} "
                                    + "in stop_times.txt at line {}. The second "
                                    + "stop will not be included.",
                            gtfsStopTime.getStopId(),
                            gtfsStopTime.getTripId(),
                            gtfsStopTime.getLineNumber());
                    continue;
                } else {
                    // Special case where a stop was defined twice in a row but
                    // with different schedule time. This likely means that the
                    // stop is a wait stop but it isn't configured correctly.
                    // For this case remove the original GtfsStopTime and
                    // create a new one with the previous arrival time but the
                    // new departure time and create it as a wait stop.
                    GtfsStopTime arrivalStopTime =
                            processedGtfsStopTimesForTrip.remove(processedGtfsStopTimesForTrip.size() - 1);
                    gtfsStopTime = new GtfsStopTime(gtfsStopTime, arrivalStopTime.getArrivalTimeSecs());
                    logger.warn(
                            "Encountered stopId={} twice in a row with "
                                    + "different times for tripId={} in stop_times.txt at line {}. Assuming "
                                    + "it is supposed to be a mid trip wait stop. {}",
                            gtfsStopTime.getStopId(),
                            gtfsStopTime.getTripId(),
                            gtfsStopTime.getLineNumber(),
                            gtfsStopTime);
                }
            }

            // Make sure departure time >= arrival time.
            // Of course either one can be null so bit more complicated.
            if (arr != null && dep != null && dep < arr) {
                logger.error(
                        "The departure time {} is before the arrival time {} in the stop_times.txt file at line {}",
                        Time.timeOfDayStr(dep),
                        Time.timeOfDayStr(arr),
                        gtfsStopTime.getLineNumber());
            }

            // Now make sure that arrival/departures times never go backwards in time
            if (arr != null && arr < previousTimeForTrip) {
                logger.error(
                        "The arrival time {} is before the time {} for a previous stop for the trip. See stop_times.txt file line {}",
                        Time.timeOfDayStr(arr),
                        Time.timeOfDayStr(previousTimeForTrip),
                        gtfsStopTime.getLineNumber());
            }
            if (dep != null && dep < previousTimeForTrip) {
                logger.error(
                        "The departure time {} is before the time {} for a previous stop for the trip. See stop_times.txt file line {}",
                        Time.timeOfDayStr(dep),
                        Time.timeOfDayStr(previousTimeForTrip),
                        gtfsStopTime.getLineNumber());
            }
            // Update previous time so can check the next stop for the trip
            if (arr != null) {
                previousTimeForTrip = arr;
            }
            if (dep != null) {
                previousTimeForTrip = dep;
            }

            // The GtfsStopTime is acceptable so add it to list to be returned
            processedGtfsStopTimesForTrip.add(gtfsStopTime);

            // For next time through loop
            previousStopId = gtfsStopTime.getStopId();
            firstStopInTrip = false;
        }

        // Return processed stop times for trip
        return processedGtfsStopTimesForTrip;
    }

    /**
     * Reads the data from stop_times.txt and puts it into gtfsStopTimesForTripMap map. Also
     * processes the data to determine Trips and TripPatterns. When processing Trips uses
     * frequency.txt data to determine if each trip ID is actually for multiple trips with unique
     * start times defined by the headway.
     */
    private void processStopTimesData() {
        // Make sure needed data is already read in. This method determines
        // trips and trip patterns from the stop_time.txt file. This objects
        // need to know lat & lon so can figure out bounding box. Therefore
        // stops.txt file must be read in first. Also, need to know which route
        // is associated with a trip determined in stop_time.txt file. This
        // info is in trips.txt so it needs to be processed first.
        if (gtfsStopsMap == null || gtfsStopsMap.isEmpty()) {
            logger.error("processStopData() must be called before processStopTimesData() is. Exiting.");
            System.exit(-1);
        }
        if (stopsMap == null || stopsMap.isEmpty()) {
            logger.error("processStopData() must be called before processStopTimesData() is. Exiting.");
            System.exit(-1);
        }
        if (gtfsTripsMap == null || gtfsTripsMap.isEmpty()) {
            logger.error("processTripsData() must be called before processStopTimesData() is. Exiting.");
            System.exit(-1);
        }

        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Processing stop_times.txt data...");

        // Read in the stop_times.txt GTFS data from file. Use a large initial
        // array size so when reading in data won't have to constantly increase
        // array size and do array copying. SFMTA for example has 1,100,000
        // stop times so starting with a value of 500,000 certainly should be
        // reasonable.
        GtfsStopTimesReader stopTimesReader = new GtfsStopTimesReader(config.getGtfsDirectoryName(), gtfsFilter);
        Collection<GtfsStopTime> gtfsStopTimes = stopTimesReader.get(500000);

        // Handle possible supplemental stop_times.txt file.
        // Match the supplemental data to the main data using both
        // trip_id and stop_id.
        if (config.hasSupplementDir()) {
            GtfsStopTimesSupplementReader stopTimesSupplementReader = new GtfsStopTimesSupplementReader(config.getSupplementDir());
            List<GtfsStopTime> stopTimesSupplement = stopTimesSupplementReader.get();

            if (!stopTimesSupplement.isEmpty()) {
                // Put original shapes into map for quick searching
                Map<MapKey, GtfsStopTime> map = new HashMap<>();
                for (GtfsStopTime gtfsStopTime : gtfsStopTimes) {
                    MapKey key = new MapKey(gtfsStopTime.getTripId(), gtfsStopTime.getStopId());
                    map.put(key, gtfsStopTime);
                }

                // Modify main GtfsShape objects using supplemental data
                for (GtfsStopTime stopTimeSupplement : stopTimesSupplement) {
                    MapKey key = new MapKey(stopTimeSupplement.getTripId(), stopTimeSupplement.getStopId());

                    // Handle depending on whether the supplemental data
                    // indicates the point is to be deleted, added, or modified
                    if (stopTimeSupplement.shouldDelete()) {
                        // The supplemental shape indicates that the point
                        // should be deleted
                        GtfsStopTime oldStopTime = map.remove(key);
                        if (oldStopTime == null) {
                            logger.error(
                                    "Supplement stop_times.txt file for "
                                            + "trip_id={} and stop_id={} specifies "
                                            + "that the stop time should be removed "
                                            + "but it is not actually configured in "
                                            + "the regular stop_times.txt file",
                                    stopTimeSupplement.getTripId(),
                                    stopTimeSupplement.getStopId());
                        }
                    } else if (map.get(key) != null) {
                        // The stop time is already in map so modify it
                        GtfsStopTime combinedShape = new GtfsStopTime(map.get(key), stopTimeSupplement);
                        map.put(key, combinedShape);
                    } else {
                        // The stop time is not already in map so add it
                        map.put(key, stopTimeSupplement);
                    }
                }

                // Use the new combined shapes
                gtfsStopTimes = map.values();
            }
        }

        // The GtfsStopTimes are put into this map and then can create Trips
        // and TripPatterns. Keyed by tripId
        gtfsStopTimesForTripMap = new HashMap<>();

        // Put the GtfsStopTimes into the map
        for (GtfsStopTime gtfsStopTime : gtfsStopTimes) {
            String tripId = gtfsStopTime.getTripId();
            gtfsStopTimesForTripMap
                    .computeIfAbsent(tripId, k -> new ArrayList<>())
                    .add(gtfsStopTime);
        }

        // Go through the stop times for each tripId. Sort them and look for
        // any problems with the data.
        Set<String> tripIds = gtfsStopTimesForTripMap.keySet();
        for (String tripId : tripIds) {
            List<GtfsStopTime> gtfsStopTimesForTrip = gtfsStopTimesForTripMap.get(tripId);
            List<GtfsStopTime> processedGtfsStopTimesForTrip = processStopTimesForTrip(gtfsStopTimesForTrip);

            // Replace the stop times for the trip with the processed/cleaned
            // up version
            gtfsStopTimesForTripMap.put(tripId, processedGtfsStopTimesForTrip);
        }

        // Log if a trip is defined in the trips.txt file but not in
        // stop_times.txt
        int numberOfProblemTrips = 0;
        for (String tripIdFromTripsFile : gtfsTripsMap.keySet()) {
            if (gtfsStopTimesForTripMap.get(tripIdFromTripsFile) == null) {
                ++numberOfProblemTrips;
                logger.warn(
                    "trip_id={} was defined on line #{} in trips.txt but there was no such trip defined in the stop_times.txt file",
                    tripIdFromTripsFile,
                    gtfsTripsMap.get(tripIdFromTripsFile).getLineNumber());
            }
        }
        if (numberOfProblemTrips > 0)
            logger.warn(
                "Found {} trips were defined in trips.txt but not in stop_times.txt out of a total of {} trips in trips.txt",
                numberOfProblemTrips,
                gtfsTripsMap.size());

        // Now that have all the stop times gtfs data create the trips
        // and the trip patterns.
        createTripsAndTripPatterns(gtfsStopTimesForTripMap);

        // Let user know what is going on
        logger.info("Finished processing stop_times.txt data. Took {} msec.", timer.elapsedMsec());
    }

    /**
     * For the trip being created go through all the stop times from the stop_times.txt GTFS file
     * and determine all the stop paths for the trip. Also update the trip patterns map when a new
     * trip pattern encountered.
     *
     * @param trip The trip being created
     * @return List of ScheduleTime objects for the trip
     */
    private List<ScheduleTime> getScheduleTimesForTrip(Trip trip) {
        // Make sure necessary data already read in
        if (gtfsStopTimesForTripMap == null || gtfsStopTimesForTripMap.isEmpty()) {
            logger.error("gtfsStopTimesForTripMap not filled in before GtfsData.getScheduleTimesForTrip() was. Exiting.");
            System.exit(-1);
        }
        if (gtfsRoutesMap == null) {
            logger.error("gtfsRoutesMap not filled in before GtfsData.getScheduleTimesForTrip() was. Exiting.");
            System.exit(-1);
        }

        // Create list of Paths for creating trip pattern
        List<StopPath> paths = new ArrayList<>();

        // Create set of path IDs for this trip so can tell if looping
        // back on path such that need to create a unique path ID
        Set<String> pathIdsForTrip = new HashSet<>();

        // Determine the gtfs stop times for this trip
        List<GtfsStopTime> gtfsStopTimesForTrip = gtfsStopTimesForTripMap.get(trip.getId());
        new StopTimeInterpolator(gtfsStopTimesForTrip).interpolate();

        // For each stop time for the trip...
        List<ScheduleTime> newScheduleTimesList = new ArrayList<>();
        String previousStopId = null;
        for (int i = 0; i < gtfsStopTimesForTrip.size(); ++i) {
            // The current gtfsStopTime
            GtfsStopTime gtfsStopTime = gtfsStopTimesForTrip.get(i);

            // Convenience variables
            Integer arrTime = gtfsStopTime.getArrivalTimeSecs();
            Integer depTime = gtfsStopTime.getDepartureTimeSecs();
            boolean firstStopInTrip = i == 0;
            boolean lastStopInTrip = i == gtfsStopTimesForTrip.size() - 1;
            String stopId = gtfsStopTime.getStopId();

            // Add the schedule time to the Trip object. Some agencies configure the
            // same arrival and departure time for every stop. That is just silly
            // and overly verbose. If the times are the same should just use
            // departure time, except for last stop for trip where should use
            // arrival time.
            Integer filteredArr = arrTime;
            Integer filteredDep = depTime;
            if (arrTime != null && arrTime.equals(depTime)) {
                if (lastStopInTrip) {
                    filteredDep = null;
                } else {
                    filteredArr = null;
                }
            }
            ScheduleTime scheduleTime = new ScheduleTime(filteredArr, filteredDep);
            newScheduleTimesList.add(scheduleTime);

            // Create StopPath so it can be used to create TripPattern.
            // First determine attributes layoverStop,
            // waitStop, and scheduleAdherenceStop. They are true
            // if there is a departure time and they are configured or
            // are first stop in trip.
            Stop stop = getStop(stopId);

            // Determine if layover stop. If trip doesn't have schedule then
            // it definitely can't be a layover.
            boolean layoverStop = false;

            if (!isTripFrequencyBasedWithoutExactTimes(trip.getId())) {
                if (depTime != null && !trip.isNoSchedule()) {
                    if (stop.getLayoverStop() == null) {
                        layoverStop = firstStopInTrip;
                    } else {
                        layoverStop = stop.getLayoverStop();
                    }
                }
            }

            // Determine if it is a waitStop. If trip doesn't have schedule then
            // it definitely can't be a waitStop.
            boolean waitStop = false;

            if (!isTripFrequencyBasedWithoutExactTimes(trip.getId())) {
                if (depTime != null && !trip.isNoSchedule()) {
                    if (stop.getWaitStop() == null) {
                        waitStop = firstStopInTrip || gtfsStopTime.isWaitStop();
                    } else {
                        waitStop = stop.getWaitStop();
                    }
                }
            }

            // This one is a bit complicated. Should be a scheduleAdherenceStop
            // if there is an associated time and it is configured to be such.
            // But should also be true if there is associated time and it is
            // first or last stop of the trip.
            boolean scheduleAdherenceStop = false;

            if (!isTripFrequencyBasedWithoutExactTimes(trip.getId())) {
                scheduleAdherenceStop = (depTime != null && (firstStopInTrip || gtfsStopTime.isTimepointStop() || stop.isTimepointStop())) || (arrTime != null && lastStopInTrip);
            }

            // Determine the pathId. Make sure that use a unique path ID by
            // appending "_loop" if looping over the same stops
            String pathId = StopPathHelper.determinePathId(previousStopId, stopId);
            while (pathIdsForTrip.contains(pathId)) pathId += "_loop";
            pathIdsForTrip.add(pathId);

            // Determine the GtfsRoute so that can get break time
            GtfsRoute gtfsRoute = gtfsRoutesMap.get(trip.getRouteId());

            // Create the new StopPath and add it to the list
            // for this trip.
            StopPath path = new StopPath(
                    revs.getConfigRev(),
                    pathId,
                    stopId,
                    gtfsStopTime.getStopSequence(),
                    lastStopInTrip,
                    trip.getRouteId(),
                    layoverStop,
                    waitStop,
                    scheduleAdherenceStop,
                    gtfsRoute.getBreakTime(),
                    gtfsStopTime.getMaxDistance(),
                    gtfsStopTime.getMaxSpeed(),
                    gtfsStopTime.getShapeDistTraveled());
            paths.add(path);

            previousStopId = stopId;
        } // End of for each stop_time for trip

        // Now that have Paths defined for the trip, if need to,
        // also create new trip pattern
        updateTripPatterns(trip, paths);

        return newScheduleTimesList;
    }

    /**
     * For when encountering a new trip. Creates the trip. Doesn't set the travel times or startTime
     * and endTime.
     *
     * @param tripId
     * @param gtfsStopTimesForTrip needed in case headsign not set in trips.txt GTFS file so that
     *     can get it from stop_headsign in the stop_times.txt file as a backup.
     * @return The new trip, or null if there is a problem with this trip and should skip it.
     */
    private Trip createNewTrip(String tripId, List<GtfsStopTime> gtfsStopTimesForTrip) {
        // Determine the GtfsTrip for the ID so can be used to construct the Trip object.
        GtfsTrip gtfsTrip = getGtfsTrip(tripId);

        // If resulting gtfsTrip is null because it wasn't defined in trips.txt then return null
        if (gtfsTrip == null) {
            return null;
        }

        // If the service ID for the trip is not valid in the future then don't need to process this Trip
        if (!validServiceIds.contains(gtfsTrip.getServiceId())) {
            // ServiceUtils ID not valid for this trip so log warning message
            // and continue on to next trip ID
            logger.warn(
                    "For tripId={} and serviceId={} the "
                            + "service is not valid in the future so the trip "
                            + "is being filtered out.",
                    gtfsTrip.getTripId(),
                    gtfsTrip.getServiceId());
            return null;
        }

        // For most agencies the headsign is obtained from the GTFS trips.txt
        // file but for some it is instead defined in the stop_times.txt file.
        String unprocessedHeadsign = gtfsTrip.getTripHeadsign();
        if (unprocessedHeadsign == null) {
            GtfsStopTime firstGtfsStopTime = gtfsStopTimesForTrip.get(0);
            unprocessedHeadsign = firstGtfsStopTime.getStopHeadsign();

            // If headsign not defined in stop times either then use default
            // of "Loop".
            if (unprocessedHeadsign == null) {
                unprocessedHeadsign = "Loop";
                logger.error(
                        "No headsign for tripId={} defined in either "
                                + "trips.txt nor stop_times.txt. Therefore using "
                                + "default of \"Loop\"",
                        gtfsTrip.getTripId());
            }
        }

        // If this route is actually a sub-route of a parent then use the parent ID.
        String properRouteId = getProperIdOfRoute(gtfsTrip.getRouteId());

        // Create the Trip and store the stop times into associated map
        GtfsRoute gtfsRoute = gtfsRoutesMap.get(gtfsTrip.getRouteId());
        String routeShortName = gtfsRoute.getRouteShortName();
        return new Trip(revs.getConfigRev(), gtfsTrip, properRouteId, routeShortName, unprocessedHeadsign, titleFormatter);
    }

    /**
     * Returns true if the trip specified in frequency.txt GTFS file as being frequency based with
     * an exact schedule. This means that need several copies of the trip, one for each start time.
     *
     * @param tripId
     * @return tTrue if specified trip is frequency based with exact_time set to true
     */
    private boolean isTripFrequencyBasedWithExactTimes(String tripId) {
        // Get list of frequencies associated with trip ID
        List<Frequency> frequencyList = getFrequencyList(tripId);

        // If first frequency is specified for "exact times" in GTFS then
        // return true.
        return frequencyList != null
                && !frequencyList.isEmpty()
                && frequencyList.get(0).isExactTimes();
    }

    /**
     * Returns true if the trip specified is frequency.txt GTFS file as being frequency based
     * without an exact schedule. This means the trip doesn't describe a schedule (it is
     * noSchedule). Instead, the vehicles are supposed to run in a loop at roughly the specified
     * headway.
     *
     * @param tripId
     * @return true if specified trip is frequency based but with exact_time set to false
     */
    private boolean isTripFrequencyBasedWithoutExactTimes(String tripId) {
        // Get list of frequencies associated with trip ID
        List<Frequency> frequencyList = getFrequencyList(tripId);

        // If first frequency is specified for "exact times" in GTFS then
        // return true.
        return frequencyList != null
                && !frequencyList.isEmpty()
                && !frequencyList.get(0).isExactTimes();
    }

    /**
     * Takes raw GTFS data and creates Trip and TripPattern objects.
     *
     * @param gtfsStopTimesForTripMap Keyed by tripId. Value is List of GtfsStopTimes for the
     *     tripId.
     */
    private void createTripsAndTripPatterns(Map<String, List<GtfsStopTime>> gtfsStopTimesForTripMap) {
        if (stopsMap == null || stopsMap.isEmpty()) {
            logger.error("processStopData() must be called before GtfsData.processStopTimesData() is. Exiting.");
            System.exit(-1);
        }
        if (frequencyMap == null) {
            logger.error("processFrequencies() must be called before GtfsData.createTripsAndTripPatterns() is. Exiting.");
            System.exit(-1);
        }
        if (validServiceIds == null) {
            logger.error("GtfsData.processServiceIds() must be called before GtfsData.createTripsAndTripPatterns() is. Exiting.");
            System.exit(-1);
        }
        if (validServiceIds.isEmpty()) {
            logger.warn("There are no services that are still active. This is "
                    + "only acceptable if every day is listed in the "
                    + "calendar_dates.txt file. Make "
                    + "sure you are processing the most up to date GTFS data "
                    + "that includes service that will be active. ");
        }

        // Create the necessary collections for trips. These collections are
        // populated in the other methods that are called by this method.
        tripsCollection = new ArrayList<>();
        tripPatternMap = new HashMap<>();
        tripPatternsByTripIdMap = new HashMap<>();
        tripPatternsByRouteIdMap = new HashMap<>();
        tripPatternIdSet = new HashSet<>();
        serviceIdsWithTrips = new HashSet<>();
        pathsMap = new HashMap<>();

        // For each trip in the stop_times.txt file ...
        for (String tripId : gtfsStopTimesForTripMap.keySet()) {
            // Create a Trip element for the trip ID.
            Trip trip = createNewTrip(tripId, gtfsStopTimesForTripMap.get(tripId));

            // If trip not valid then skip over it
            if (trip == null) {
                logger.warn(
                        "Encountered trip_id={} in the "
                                + "stop_times.txt file but that trip_id is not in "
                                + "the trips.txt file, the service ID for the "
                                + "trip is not valid in anytime in the future, "
                                + "or the associated route is filtered out, "
                                + "or the trip is filtered out. "
                                + "Therefore this trip cannot be configured and "
                                + "has been discarded.",
                        tripId);
                continue;
            }

            // Keep track of service IDs so can filter unneeded calendars
            serviceIdsWithTrips.add(trip.getServiceId());

            // All the schedule times are available in gtfsStopTimesForTripMap
            // so add them all at once to the Trip. This also sets the startTime
            // and endTime for the trip. This is done after the Trip is already
            // created since it deals with a few things including schedule
            // times list, trip patterns, paths, etc and so it is much simpler
            // to have getScheduleTimesForTrip() update an already existing
            // Trip object.
            List<ScheduleTime> scheduleTimesList = getScheduleTimesForTrip(trip);
            trip.addScheduleTimes(scheduleTimesList);

            if (isTripFrequencyBasedWithExactTimes(tripId)) {
                // This is special case where for this trip ID
                // there is an entry in the frequencies.txt
                // file with exact_times set indicating that need to create
                // a separate Trip for each actual trip.
                List<Frequency> frequencyListForTripId = frequencyMap.get(tripId);
                for (Frequency frequency : frequencyListForTripId) {
                    for (int tripStartTime = frequency.getStartTime();
                            tripStartTime < frequency.getEndTime();
                            tripStartTime += frequency.getHeadwaySecs()) {

                        tripsCollection.add(new Trip(trip, tripStartTime));
                    }
                }
            } else if (isTripFrequencyBasedWithoutExactTimes(tripId)) {
                // This is a trip defined in the GTFS frequency.txt file
                // to not be schedule based (not have exact_times set).
                // Need to create a trip for each time range defined for
                // the trip in frequency.txt .
                List<Frequency> frequencyListForTripId = frequencyMap.get(tripId);
                for (Frequency frequency : frequencyListForTripId) {
                    Trip frequencyBasedTrip = new Trip(trip, frequency.getStartTime(), frequency.getEndTime());
                    tripsCollection.add(frequencyBasedTrip);
                }
            } else {
                // This is the normal case, an actual Trip that is not affected
                // by exact times in frequencies.txt data. Therefore simply add
                // it to the collection. It still might be a trip with no
                // schedule, but it isn't one with exact times.
                tripsCollection.add(trip);
            }
        } // End of for each trip ID

        // Process the headsigns for the trips and the trip patterns to make sure that
        // they are unique for each destination.
        makeHeadsignsUniqueIfDifferentLastStop();
    }

    /**
     * For each route makes sure that the headsigns are unique if the last stop of the trip is
     * different. This way get different headsigns if the destination is different.
     */
    private void makeHeadsignsUniqueIfDifferentLastStop() {
        // Make sure all necessary data already read in
        if (gtfsRoutesMap == null || gtfsRoutesMap.isEmpty()) {
            logger.error("processRouteData() must be called before GtfsData.makeHeadsignsUniqueIfDifferentLastStop() is. Exiting.");
            System.exit(-1);
        }
        if (gtfsStopsMap == null || gtfsStopsMap.isEmpty()) {
            logger.error("processStopData() must be called before GtfsData.makeHeadsignsUniqueIfDifferentLastStop() is. Exiting.");
            System.exit(-1);
        }

        Set<String> routeIds = gtfsRoutesMap.keySet();
        for (String routeId : routeIds) {
            // Determine the trip patterns for the route so that they
            // can be included when constructing the route object.
            // If there aren't any then can't
            List<TripPattern> tripPatternsForRoute = getTripPatterns(routeId);

            // If no trip patterns for the route then can ignore it
            if (tripPatternsForRoute == null) {
                logger.warn(
                        "In makeHeadsignsUniqueIfDifferentLastStop() no "
                                + "trip patterns configured for routeId={} so skipping "
                                + "that route.",
                        routeId);
                continue;
            }

            // Keyed on headsign
            Map<String, List<TripPattern>> tripPatternsByHeadsign = new HashMap<>();
            for (TripPattern tripPattern : tripPatternsForRoute) {
                // Add the trip pattern to tripPatternsByHeadsign map
                String headsign = tripPattern.getHeadsign();
                tripPatternsByHeadsign
                    .computeIfAbsent(headsign, k -> new ArrayList<>())
                    .add(tripPattern);
            }

            // Now that we have list of trip patterns for each headsign can
            // make sure they have different final stops
            for (String headsign : tripPatternsByHeadsign.keySet()) {
                List<TripPattern> tripPatternsForHeadsign = tripPatternsByHeadsign.get(headsign);
                TripPattern firstTripPatternForHeadsign = tripPatternsForHeadsign.get(0);
                String lastStopId = firstTripPatternForHeadsign.getLastStopIdForTrip();
                Location firstTripPatternLastStopLoc = getStop(lastStopId).getLoc();

                for (TripPattern tripPattern : tripPatternsForHeadsign) {
                    String lastStopIdForTrip = tripPattern.getLastStopIdForTrip();
                    Location currentTripPatternLastStopLoc = getStop(lastStopIdForTrip).getLoc();
                    double distanceBetweenLastStops = Geo.distance(currentTripPatternLastStopLoc, firstTripPatternLastStopLoc);

                    // If for this headsign the last stops differ then want to
                    // differentiate the headsigns. But only modify a headsign
                    // if it wasn't already set by title formatter. This way can
                    // use title formatter to actually combine trip patterns
                    // that vary only slightly into a single headsign. Also,
                    // only modify headsign if the last stops are actually
                    // significantly apart (more than 1000 meters). There are a
                    // good number of times where there is just a small difference
                    // not worth taking into account.
                    if (!lastStopIdForTrip.equals(lastStopId)
                            && !titleFormatter.isReplaceTitle(headsign)
                            && distanceBetweenLastStops > config.getMinDistanceBetweenStopsToDisambiguateHeadsigns()) {
                        // The last stop is different for this trip pattern even
                        // though the configured headsign is the same. Therefore
                        // modify the shorter trip pattern to append the last
                        // stop name to the headsign
                        if (firstTripPatternForHeadsign.getNumberStopPaths() < tripPattern.getNumberStopPaths()) {
                            // The first trip pattern is shorter so it should have headsign modified
                            String modifiedHeadsign = "%s to %s".formatted(firstTripPatternForHeadsign.getHeadsign(), getStop(firstTripPatternForHeadsign.getLastStopIdForTrip()).getName());
                            logger.warn(
                                    "Modifying headsign \"{}\" to \"{}\" since it has a different"
                                            + " last stop {} away which is further away than"
                                            + " transitclock.gtfs.minDistanceBetweenStopsToDisambiguateHeadsigns"
                                            + " of {}. TripPattern {}. Other TripPattern {}",
                                    firstTripPatternForHeadsign.getHeadsign(),
                                    modifiedHeadsign,
                                    StringUtils.distanceFormat(distanceBetweenLastStops),
                                    config.getMinDistanceBetweenStopsToDisambiguateHeadsigns(),
                                    firstTripPatternForHeadsign.toShortString(),
                                    tripPattern.toShortString());
                            firstTripPatternForHeadsign.setHeadsign(modifiedHeadsign);
                            for (Trip trip : firstTripPatternForHeadsign.getTrips()) {
                                trip.setHeadsign(modifiedHeadsign);
                            }
                        } else {
                            // The current trip pattern is shorter so it should have
                            // headsign modified
                            String modifiedHeadsign = "%s to %s".formatted(tripPattern.getHeadsign(), getStop(tripPattern.getLastStopIdForTrip()).getName());
                            logger.warn(
                                    "Modifying headsign \"{}\" to \"{}\" since it has a different"
                                            + " last stop {} away which is further away than"
                                            + " transitclock.gtfs.minDistanceBetweenStopsToDisambiguateHeadsigns"
                                            + " of {}. TripPattern {}. Other TripPattern {}",
                                    tripPattern.getHeadsign(),
                                    modifiedHeadsign,
                                    StringUtils.distanceFormat(distanceBetweenLastStops),
                                    config.getMinDistanceBetweenStopsToDisambiguateHeadsigns(),
                                    tripPattern.toShortString(),
                                    firstTripPatternForHeadsign.toShortString());
                            tripPattern.setHeadsign(modifiedHeadsign);
                            for (Trip trip : tripPattern.getTrips()) {
                                trip.setHeadsign(modifiedHeadsign);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * This method is called for each trip. Determines if the corresponding trip pattern is already
     * in tripPatternMap. If it is then it updates the trip pattern to include this trip as a
     * member. If this trip pattern not already encountered then it adds it to the tripPatternMap.
     *
     * @param trip
     * @param stopPaths List of StopPath objects that define the trip pattern
     */
    private void updateTripPatterns(Trip trip, List<StopPath> stopPaths) {
        // Create a TripPatternBase from the Trip object
        TripPatternKey tripPatternKey = new TripPatternKey(trip.getShapeId(), stopPaths);

        // Determine if the TripPattern is already stored.
        TripPattern tripPatternFromMap = tripPatternMap.get(tripPatternKey);
        // If not already stored then create and store the trip pattern.
        if (tripPatternFromMap == null) {
            // Create the trip pattern
            TripPattern tripPattern = new TripPattern(revs.getConfigRev(), trip.getShapeId(), stopPaths, trip, this);

            // Add the new trip pattern to the maps
            tripPatternMap.put(tripPatternKey, tripPattern);
            tripPatternsByTripIdMap.put(trip.getId(), tripPattern);
            tripPatternIdSet.add(tripPattern.getId());

            // Also add the new TripPattern to tripPatternsByRouteIdMap
            tripPatternsByRouteIdMap
                .computeIfAbsent(tripPattern.getRouteId(), k -> new ArrayList<>())
                .add(tripPattern);

            // Update the Trip to indicate which TripPattern it is for
            trip.setTripPattern(tripPattern);

            // Now that we have the trip pattern ID update the map of Paths
            for (StopPath path : tripPattern.getStopPaths())
                putPath(tripPattern.getId(), path.getId(), path);
        } else {
            // This trip pattern already in map so just add the Trip
            // to the list of trips that refer to it.
            tripPatternFromMap.addTrip(trip);

            // Add it to tripPatternsByTripIdMap as well
            tripPatternsByTripIdMap.put(trip.getId(), tripPatternFromMap);

            // Update the Trip to indicate which TripPattern it is for
            trip.setTripPattern(tripPatternFromMap);
        }
    }

    /**
     * Goes through all the trips and constructs block assignments from them. Also, goes through the
     * frequencies and created unscheduled blocks for them.
     */
    private void processBlocks() {
        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Processing blocks...");

        // Actually process the block info and get back list of blocks
        blocks = new BlocksProcessor(this)
                .process(revs.getConfigRev());

        // Let user know what is going on
        logger.info("Finished processing blocks. Took {} msec.", timer.elapsedMsec());
    }

    /** Reads frequencies.txt file and puts data into _frequencies list. */
    private void processFrequencies() {
        // Make sure needed data is already read in.
        if (gtfsTripsMap == null || gtfsTripsMap.isEmpty()) {
            logger.error("processTripsData() must be called before " + "GtfsData.processFrequencies() is. Exiting.");
            System.exit(-1);
        }

        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Processing frequencies.txt data...");

        // Create the map where the data is going to go
        frequencyMap = new HashMap<>();

        // Read in the frequencies.txt GTFS data from file
        GtfsFrequenciesReader frequenciesReader = new GtfsFrequenciesReader(config.getGtfsDirectoryName(), gtfsFilter);
        List<GtfsFrequency> gtfsFrequencies = frequenciesReader.get();

        for (GtfsFrequency gtfsFrequency : gtfsFrequencies) {
            // Make sure this Frequency is in trips.txt
            GtfsTrip gtfsTrip = gtfsTripsMap.get(gtfsFrequency.getTripId());
            if (gtfsTrip == null) {
                logger.error(
                        "The frequency from line # {} of frequencies.txt "
                                + "refers to trip_id={} but that trip is not in the "
                                + "trips.txt file. Therefore this frequency will be "
                                + "ignored.",
                        gtfsFrequency.getLineNumber(),
                        gtfsFrequency.getTripId());
                continue;
            }

            // Create the Frequency object and put it into the frequenctMap
            Frequency frequency = new Frequency(revs.getConfigRev(), gtfsFrequency);
            String tripId = frequency.getTripId();
            frequencyMap
                .computeIfAbsent(tripId, k -> new ArrayList<>())
                .add(frequency);
        }

        // Let user know what is going on
        logger.info("Finished processing frequencies.txt data. Took {} msec.", timer.elapsedMsec());
    }

    /**
     * Reads in shapes.txt file and processes the information into StopPath objects. Using the term
     * "StopPath" instead of "Shape" to be more descriptive of what the data is really for.
     */
    private void processPaths() {
        // Make sure needed data is already read in. This method
        // converts the shapes into Paths such that each path ends
        // at a stop. Therefore need to have read in stop info first.
        if (stopsMap == null || stopsMap.isEmpty()) {
            logger.error("processStopData() must be called before " + "GtfsData.processPaths() is. Exiting.");
            System.exit(-1);
        }

        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Processing shapes.txt data...");

        // Read in the shapes.txt GTFS data from file
        GtfsShapesReader shapesReader = new GtfsShapesReader(config.getGtfsDirectoryName());
        Collection<GtfsShape> gtfsShapes = shapesReader.get();

        // Handle possible supplemental shapes.txt file.
        // Match the supplemental data to the main data using both
        // shape_id and shape_pt_sequence.
        if (config.hasSupplementDir()) {
            GtfsShapesSupplementReader shapesSupplementReader = new GtfsShapesSupplementReader(config.getSupplementDir());
            List<GtfsShape> shapesSupplement = shapesSupplementReader.get();

            if (!shapesSupplement.isEmpty()) {
                // Put original shapes into map for quick searching
                Map<MapKey, GtfsShape> map = new HashMap<>();
                for (GtfsShape gtfsShape : gtfsShapes) {
                    MapKey key = new MapKey(gtfsShape.getShapeId(), gtfsShape.getShapePtSequence());
                    map.put(key, gtfsShape);
                }

                // Modify main GtfsShape objects using supplemental data.
                for (GtfsShape shapeSupplement : shapesSupplement) {
                    MapKey key = new MapKey(shapeSupplement.getShapeId(), shapeSupplement.getShapePtSequence());

                    // Handle depending on whether the supplemental data
                    // indicates the point is to be deleted, added, or modified
                    if (shapeSupplement.shouldDelete()) {
                        // The supplemental shape indicates that the point
                        // should be deleted
                        GtfsShape oldShape = map.remove(key);
                        if (oldShape == null) {
                            logger.error(
                                    "Supplement shapes.txt file for "
                                            + "shape_id={} and shape_pt_sequence={} "
                                            + "specifies that the shape point should "
                                            + "be removed but it is not actually "
                                            + "configured in the regular shapes.txt "
                                            + "file",
                                    shapeSupplement.getShapeId(),
                                    shapeSupplement.getShapePtSequence());
                        }
                    } else if (map.get(key) != null) {
                        // The shape point is already in map so modify it
                        GtfsShape combinedShape = new GtfsShape(map.get(key), shapeSupplement);
                        map.put(key, combinedShape);
                    } else {
                        // The shape point is not already in map so add it
                        map.put(key, shapeSupplement);
                    }
                }

                // Use the new combined shapes
                gtfsShapes = map.values();
            }
        }

        // Process all the shapes into stopPaths
        StopPathProcessor pathProcessor = new StopPathProcessor(
                Collections.unmodifiableCollection(gtfsShapes),
                Collections.unmodifiableMap(stopsMap),
                Collections.unmodifiableCollection(tripPatternMap.values()),
                config.getPathOffsetDistance(),
                config.getMaxStopToPathDistance(),
                config.getMaxDistanceForEliminatingVertices(),
                config.isTrimPathBeforeFirstStopOfTrip(),
                config.getMaxDistanceBetweenStops(),
                config.isDisableSpecialLoopBackToBeginningCase());
        pathProcessor.processPathSegments();

        // Let user know what is going on
        logger.info("Finished processing shapes.txt data. Took {} msec.", timer.elapsedMsec());
    }

    /** Reads agency.txt file and puts data into agencies list. */
    private void processAgencyData() {
        // Make sure necessary data read in
        if (routesMap == null || routesMap.isEmpty()) {
            // Route data first needed so can determine extent of agency
            logger.error("GtfsData.processRoutesData() must be called before GtfsData.processAgencyData() is. Exiting.");
            System.exit(-1);
        }

        // Let user know what is going on
        logger.info("Processing agency.txt data...");

        // Create the array where the data is going to go
        agencies = new ArrayList<>();

        // Read in the agency.txt GTFS data from file
        GtfsAgencyReader agencyReader = new GtfsAgencyReader(config.getGtfsDirectoryName());
        List<GtfsAgency> gtfsAgencies = agencyReader.get();
        HashMap<String, GtfsAgency> gtfsAgenciesMap = new HashMap<>(gtfsAgencies.size());

        for (GtfsAgency gtfsAgency : gtfsAgencies)
            gtfsAgenciesMap.put(gtfsAgency.getAgencyId(), gtfsAgency);

        // Read in supplemental agency data
        if (config.hasSupplementDir()) {
            // Read in the supplemental agency data
            GtfsAgenciesSupplementReader agenciesSupplementReader = new GtfsAgenciesSupplementReader(config.getSupplementDir());
            List<GtfsAgency> gtfsAgenciesSupplement = agenciesSupplementReader.get();
            for (GtfsAgency gtfsAgencySupplement : gtfsAgenciesSupplement) {
                // Determine the proper agency by agencyId.
                GtfsAgency gtfsAgency = gtfsAgenciesMap.get(gtfsAgencySupplement.getAgencyId());
                if (gtfsAgency == null) {
                    logger.error(
                            "Found supplemental agency data for "
                                    + "agencyId={} but that agency did not exist in "
                                    + "the main agency.txt file. {}",
                            gtfsAgencySupplement.getAgencyId(),
                            gtfsAgencySupplement);
                    continue;
                }

                // Create a new GtfsAgency object that combines the original
                // data with the supplemental data
                GtfsAgency combinedAgency = new GtfsAgency(gtfsAgency, gtfsAgencySupplement);

                // Store that combined data agency in the map
                gtfsAgenciesMap.put(combinedAgency.getAgencyId(), combinedAgency);
            }
        }

        // Go through the agencies as they were listed in the agency.txt file
        // and add the combined agencies (including the supplemental data) to
        // the agencies member in the proper order. This way when getting an
        // agency for the UI can just use the first agency.
        for (GtfsAgency originalGtfsAgency : gtfsAgencies) {
            GtfsAgency combinedGtfsAgency = gtfsAgenciesMap.get(originalGtfsAgency.getAgencyId());

            // Create the Agency object and put it into the array
            agencies.add(new Agency(revs.getConfigRev(), combinedGtfsAgency, routesMap.values()));
        }

        // Let user know what is going on
        logger.info("Finished processing agencies.txt data. ");
    }

    /**
     * Determines if the specified calendar is active in the future. It is active if the end date is
     * in the future or if it is added in the future via calendar_dates.txt
     */
    private static boolean isCalendarActiveInTheFuture(Calendar calendar, List<CalendarDate> calendarDates) {
        // If calendar end date is for sometime in the future then it is
        // definitely active.
        if (calendar.getEndDate().getTime() > System.currentTimeMillis()) return true;

        // End date is not in the future so see if it is being added as an
        // exception via the calendar_dates.txt file.
        for (CalendarDate calendarDate : calendarDates) {
            if (calendar.getServiceId().equals(calendarDate.getServiceId())
                    && calendarDate.addService()
                    && calendarDate.getDate().getTime() > System.currentTimeMillis()) {
                return true;
            }
        }

        // The calendar is for in the past and the associated service is not
        // listed as an "add service" in a calendar date so must not be valid.
        return false;
    }

    /**
     * Returns true if the specified calendar date is in the future and is for adding service.
     */
    private static boolean isCalendarDateActiveInTheFuture(CalendarDate calendarDate) {
        return calendarDate.getDate().getTime() > System.currentTimeMillis() && calendarDate.addService();
    }

    /** Reads calendar.txt file and puts data into calendars list. */
    private void processCalendars() {
        // Let user know what is going on
        logger.info("Processing calendar.txt data...");

        // Create the map where the data is going to go
        calendars = new ArrayList<>();

        // Read in the calendar.txt GTFS data from file
        GtfsCalendarReader calendarReader = new GtfsCalendarReader(config.getGtfsDirectoryName());
        List<GtfsCalendar> gtfsCalendars = calendarReader.get();

        if (gtfsCalendars.isEmpty()) {
            logger.info("calendar.txt not found, will generate calendars and assume all services are"
                    + " always available...");
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
            java.util.Calendar cal = java.util.Calendar.getInstance();
            String start = format.format(cal.getTime());
            cal.add(java.util.Calendar.MONTH, 6);
            String end = format.format(cal.getTime());
            GtfsTripsReader tripsReader = new GtfsTripsReader(config.getGtfsDirectoryName(), gtfsFilter, readerHelper);
            List<GtfsTrip> gtfsTrips = tripsReader.get();
            Set<String> serviceIds = new HashSet<>();
            for (GtfsTrip gtfsTrip : gtfsTrips) {
                serviceIds.add(gtfsTrip.getServiceId());
            }
            for (String serviceId : serviceIds) {
                GtfsCalendar gtfsCalendar = new GtfsCalendar(serviceId, "1", "1", "1", "1", "1", "1", "1", start, end);
                gtfsCalendars.add(gtfsCalendar);
            }
        }

        for (GtfsCalendar gtfsCalendar : gtfsCalendars) {
            // Create the Calendar object and put it into the array)
            Calendar calendar = new Calendar(revs.getConfigRev(), gtfsCalendar, getDateFormatter());
            calendars.add(calendar);
        }

        // Let user know what is going on
        logger.info("Finished processing calendar.txt data. ");
    }

    /** Reads calendar_dates.txt file and puts data into calendarDates list. */
    private void processCalendarDates() {
        // Let user know what is going on
        logger.info("Processing calendar_dates.txt data...");

        // Create the map where the data is going to go
        calendarDates = new ArrayList<>();

        // Read in the calendar_dates.txt GTFS data from file
        GtfsCalendarDatesReader calendarDatesReader = new GtfsCalendarDatesReader(config.getGtfsDirectoryName());
        List<GtfsCalendarDate> gtfsCalendarDates = calendarDatesReader.get();

        for (GtfsCalendarDate gtfsCalendarDate : gtfsCalendarDates) {
            // Create the CalendarDate object
            CalendarDate calendarDate = new CalendarDate(revs.getConfigRev(), gtfsCalendarDate, getDateFormatter());

            // If calendar date is not sometime in the future then can ignore
            // it and not store it. This can be useful because an agency might
            // not clean out old dates from the calendar_dates.txt file even
            // if they are no longer useful.
            if (calendarDate.getDate().getTime() + Time.DAY_IN_MSECS < System.currentTimeMillis()) continue;

            // The calendar date is for in the future so store it
            calendarDates.add(calendarDate);
        }

        // Let user know what is going on
        logger.info("Finished processing calendar_dates.txt data. ");
    }

    /** Creates validServiceIds member by going through calendars */
    private void processServiceIds() {
        // Make sure needed data is already read in.
        if (calendars == null) {
            logger.error("GtfsData.processCalendars() must be called " + "before GtfsData.processServiceIds() is. Exiting.");
            System.exit(-1);
        }
        if (calendarDates == null) {
            logger.error("GtfsData.processCalendarDates() must be called before GtfsData.processServiceIds() is. Exiting.");
            System.exit(-1);
        }

        // Create set of service IDs from the calendar.txt data
        validServiceIds = new HashSet<>();
        for (Calendar calendar : calendars) {
            if (isCalendarActiveInTheFuture(calendar, calendarDates)) {
                validServiceIds.add(calendar.getServiceId());
            } else {
                logger.warn(
                        "The service ID {} is not configured for in the "
                                + "future in calendar.txt and calendar_dates.txt and "
                                + "is therefore not being included in the "
                                + "configuration. {}",
                        calendar.getServiceId(),
                        calendar);
            }
        }

        // Add in service IDs that might be in calendar_date.txt but not in
        // calendar.txt file
        for (CalendarDate calendarDate : calendarDates) {
            if (isCalendarDateActiveInTheFuture(calendarDate)) validServiceIds.add(calendarDate.getServiceId());
        }
    }

    /**
     * Get rid of calendars and calendar dates that don't have any trips associated to try to pare
     * down number of service IDs. Especially useful when processing just part of an agency config,
     * like MBTA commuter rail.
     */
    private void trimCalendars() {
        // Make sure needed data is already read in.
        if (serviceIdsWithTrips == null) {
            logger.error("GtfsData.processStopTimesData() must be called " + "before GtfsData.trimCalendars() is. Exiting.");
            System.exit(-1);
        }

        // Trim calendar list
        calendars.removeIf(calendar -> !serviceIdsWithTrips.contains(calendar.getServiceId()));

        // Trim calendar date list
        calendarDates.removeIf(calendarDate -> !serviceIdsWithTrips.contains(calendarDate.getServiceId()));

        // Trim serviceIds list
        validServiceIds.removeIf(serviceId -> !serviceIdsWithTrips.contains(serviceId));
    }

    /** Reads fare_attributes.txt file and puts data into fareAttributes list. */
    private void processFareAttributes() {
        // Let user know what is going on
        logger.info("Processing fare_attributes.txt data...");

        // Create the map where the data is going to go
        fareAttributes = new ArrayList<>();

        // Read in the fare_attributes.txt GTFS data from file
        GtfsFareAttributesReader fareAttributesReader = new GtfsFareAttributesReader(config.getGtfsDirectoryName());
        List<GtfsFareAttribute> gtfsFareAttributes = fareAttributesReader.get();

        for (GtfsFareAttribute gtfsFareAttribute : gtfsFareAttributes) {
            // Create the FareAttribute object and put it into the array
            FareAttribute FareAttribute = new FareAttribute(revs.getConfigRev(), gtfsFareAttribute);
            fareAttributes.add(FareAttribute);
        }

        // Let user know what is going on
        logger.info("Finished processing fare_attributes.txt data. ");
    }

    /** Reads fare_rules.txt file and puts data into fareRules list. */
    private void processFareRules() {
        // Let user know what is going on
        logger.info("Processing fare_rules.txt data...");

        // Create the map where the data is going to go
        fareRules = new ArrayList<>();

        // Read in the fare_rules.txt GTFS data from file
        GtfsFareRulesReader fareRulesReader = new GtfsFareRulesReader(config.getGtfsDirectoryName());
        List<GtfsFareRule> gtfsFareRules = fareRulesReader.get();

        // Get rid of duplicates
        Set<GtfsFareRule> gtfsFareRulesSet = new HashSet<>(gtfsFareRules);

        for (GtfsFareRule gtfsFareRule : gtfsFareRulesSet) {
            // If this route is actually a sub-route of a parent then use the
            // parent ID.
            String parentRouteId = getProperIdOfRoute(gtfsFareRule.getRouteId());

            // Create the CalendarDate object and put it into the array
            FareRule fareRule = new FareRule(revs.getConfigRev(), gtfsFareRule, parentRouteId);
            fareRules.add(fareRule);
        }

        // Let user know what is going on
        logger.info("Finished processing fare_rules.txt data. ");
    }

    /** Reads transfers.txt file and puts data into transfers list. */
    private void processTransfers() {
        // Let user know what is going on
        logger.info("Processing transfers.txt data...");

        // Create the map where the data is going to go
        transfers = new ArrayList<>();

        // Read in the transfers.txt GTFS data from file
        GtfsTransfersReader transfersReader = new GtfsTransfersReader(config.getGtfsDirectoryName());
        List<GtfsTransfer> gtfsTransfers = transfersReader.get();

        for (GtfsTransfer gtfsTransfer : gtfsTransfers) {
            // Create the CalendarDate object and put it into the array
            Transfer transfer = new Transfer(revs.getConfigRev(), gtfsTransfer);
            transfers.add(transfer);
        }

        // Let user know what is going on
        logger.info("Finished processing transfers.txt data. ");
    }

    /**
     * @param routeId
     * @return the GtfsRoute from the trips.txt file for the specified routeId, null if that route
     *     Id not defined in the file.
     */
    public GtfsRoute getGtfsRoute(String routeId) {
        return gtfsRoutesMap.get(routeId);
    }

    /**
     * @param tripId
     * @return the GtfsTrip from the trips.txt file for the specified tripId, null if that trip Id
     *     not defined in the file.
     */
    public GtfsTrip getGtfsTrip(String tripId) {
        return gtfsTripsMap.get(tripId);
    }

    /**
     * Returns true if GTFS stop times read in and are available
     */
    public boolean isStopTimesReadIn() {
        return gtfsStopTimesForTripMap != null && !gtfsStopTimesForTripMap.isEmpty();
    }

    /**
     * Returns list of GtfsStopTimes for the trip specified
     */
    public List<GtfsStopTime> getGtfsStopTimesForTrip(String tripId) {
        return gtfsStopTimesForTripMap.get(tripId);
    }

    /**
     * @return Collection of all the Trip objects
     */
    public Collection<Trip> getTrips() {
        return tripsCollection;
    }

    /**
     * @return True if tripsMap read in and is usable
     */
    public boolean isTripsReadIn() {
        return tripsCollection != null && !tripsCollection.isEmpty();
    }

    public GtfsStop getGtfsStop(String stopId) {
        return gtfsStopsMap.get(stopId);
    }

    public Collection<Stop> getStops() {
        return stopsMap.values();
    }

    /**
     * Gets the Stop from the stopsMap.
     *
     * @param stopId
     * @return The Stop for the specified stopId
     */
    public Stop getStop(String stopId) {
        return stopsMap.get(stopId);
    }

    /**
     * @return Collection of all TripPatterns
     */
    public Collection<TripPattern> getTripPatterns() {
        return tripPatternsByTripIdMap.values();
    }

    /**
     * @param routeId
     * @return List of TripPatterns for the routeId. Can be null.
     */
    public List<TripPattern> getTripPatterns(String routeId) {
        return tripPatternsByRouteIdMap.get(routeId);
    }

    /**
     * @param tripId The trip ID to return the TripPattern for
     * @return The TripPattern for the specified trip ID
     */
    public TripPattern getTripPatternByTripId(String tripId) {
        return tripPatternsByTripIdMap.get(tripId);
    }

    public boolean isTripPatternIdAlreadyUsed(String tripPatternId) {
        return tripPatternIdSet.contains(tripPatternId);
    }

    /**
     * Returns the specified trip pattern. Not very efficient because does a linear search through
     * the set of trip patterns but works well enough for debugging.
     *
     * @param tripPatternId
     * @return
     */
    public TripPattern getTripPattern(String tripPatternId) {
        for (TripPattern tp : tripPatternMap.values()) {
            if (tp.getId().equals(tripPatternId)) return tp;
        }

        // Couldn't find the specified trip pattern so return null;
        return null;
    }

    /**
     * For use with pathsMap member.
     */
    public static String getPathMapKey(String tripPatternId, String pathId) {
        return tripPatternId + "|" + pathId;
    }

    /**
     * Returns the StopPath for the specified tripPatternId and pathId. Can't just use pathId since
     * lots of trip patterns will traverse the same stops, resulting in identical pathIds. And don't
     * want to make the pathIds themselves unique because then wouldn't be able to reuse travel time
     * data as much.
     */
    public StopPath getPath(String tripPatternId, String pathId) {
        String key = getPathMapKey(tripPatternId, pathId);
        return pathsMap.get(key);
    }

    /**
     * @return Collection of all the Paths
     */
    public Collection<StopPath> getPaths() {
        return pathsMap.values();
    }

    /**
     * Adds the StopPath object to the pathMap.
     */
    public void putPath(String tripPatternId, String pathId, StopPath path) {
        String key = getPathMapKey(tripPatternId, pathId);
        pathsMap.put(key, path);
    }

    /**
     * If a route is configured to be a sub-route of a parent then this method will return the route
     * ID of the parent route. Otherwise returns null.
     *
     * @param routeId
     * @return route ID of parent route if there is one. Otherwise, null.
     */
    public String getProperIdOfRoute(String routeId) {
        if (routeId == null)
            return null;
        return properRouteIdMap.get(routeId);
    }

    /**
     * Returns true if according to frequency.txt GTFS file that specified trip is frequencies based
     * and doesn't have exact_times set. Note that if exact_times is set then a schedule is used. It
     * is just that the schedule is based on the frequencies and start time of trip.
     *
     * @param tripId
     * @return true if frequency based trip
     */
    public boolean isTripFrequencyBased(String tripId) {
        List<Frequency> frequencyListForTrip = getFrequencyList(tripId);
        return frequencyListForTrip != null && !frequencyListForTrip.get(0).isExactTimes();
    }

    /**
     * @param tripId
     * @return The Frequency list specified by tripId param
     */
    public List<Frequency> getFrequencyList(String tripId) {
        return frequencyMap.get(tripId);
    }

    /**
     * Returns collection of all the Frequency objects. This method goes through the internal
     * frequencyMap and compiles the collection each time this member is called.
     *
     * @return
     */
    public Collection<Frequency> getFrequencies() {
        Collection<Frequency> collection = new ArrayList<>();
        for (List<Frequency> frequencyListForTripId : frequencyMap.values()) {
            collection.addAll(frequencyListForTripId);
        }
        return collection;
    }

    /**
     * Returns information about the current revision.
     *
     * @return
     */
    public ConfigRevision getConfigRevision() {
        return new ConfigRevision(revs.getConfigRev(), new Date(), zipFileLastModifiedTime, "");
    }

    public void outputRoutesForGraphing() {
        if (config.getOutputPathsAndStopsForGraphingRouteIds() == null)
            return;

        String[] routeIds = config.getOutputPathsAndStopsForGraphingRouteIds().split(",");
        for (String routeId : routeIds) {
            outputPathsAndStopsForGraphing(routeId);
        }
    }

    /**
     * Outputs data for specified route grouped by trip pattern. The resulting data can be
     * visualized on a map by cutting and pasting it in to http://www.gpsvisualizer.com/map_input .
     *
     * @param routeId
     */
    public void outputPathsAndStopsForGraphing(String routeId) {
        System.err.println("\nPaths for routeId=" + routeId);

        // Also need to be able to get trip patterns associated
        // with a route so can be included in Route object.
        // Key is routeId.
        List<TripPattern> tripPatterns = tripPatternsByRouteIdMap.get(routeId);
        for (TripPattern tripPattern : tripPatterns) {
            System.err.println("\n\n================= TripPatternId="
                    + tripPattern.getId()
                    + " shapeId="
                    + tripPattern.getShapeId()
                    + "=======================\n");

            // Output the header info
            System.err.println("name,symbol,color,label,latitude,longitude");

            // Output the stop locations so can see where they are relative to path
            for (StopPath path : tripPattern.getStopPaths()) {
                String stopId = path.getStopId();
                Stop stop = getStop(stopId);
                System.err.println(", pin, red, stop "
                        + stopId
                        + ", "
                        + Geo.format(stop.getLoc().getLat())
                        + ", "
                        + Geo.format(stop.getLoc().getLon()));
            }

            int pathCnt = 0;
            for (StopPath path : tripPattern.getStopPaths()) {
                // Use different colors and symbols so can tell how things are progressing
                ++pathCnt;
                String symbolAndColor = switch (pathCnt % 13) {
                    case 0 -> "star, blue";
                    case 1 -> "googlemini, green";
                    case 2 -> "diamond, blue";
                    case 3 -> "square, green";
                    case 4 -> "triangle, blue";
                    case 5 -> "cross, green";
                    case 6 -> "circle, blue";
                    case 7 -> "star, red";
                    case 8 -> "googlemini, yellow";
                    case 9 -> "diamond, red";
                    case 10 -> "square, yellow";
                    case 11 -> "triangle, red";
                    case 12 -> "cross, yellow";
                    default -> "circle, red";
                };

                // Output the path info for this trip pattern
                int i = 0;
                for (Location loc : path.getLocations()) {
                    String popupName = i + " lat=" + Geo.format(loc.getLat()) + " lon=" + Geo.format(loc.getLon());
                    String label = "" + i;
                    System.err.println(popupName
                            + ", "
                            + symbolAndColor
                            + ", "
                            + label
                            + ", "
                            + Geo.format(loc.getLat())
                            + ", "
                            + Geo.format(loc.getLon()));
                    ++i;
                }
            }
        }
    }


    /** Does all the work. Processes the data and store it in internal structures */
    public void processData() {

        // Let user know what is going on
        logger.info("Processing GTFS data from {} ...", config.getGtfsDirectoryName());

        // Note. The order of how these are processed in important because
        // some data sets rely on others in order to be fully processed.
        // If the order is wrong then the methods below will log an error and
        // exit.
        processRouteData();
        processStopData();
        processCalendarDates();
        processCalendars();
        processServiceIds();
        processTripsData();
        processFrequencies();
        processStopTimesData();
        processRouteMaps();
        processBlocks();
        processPaths();
        processAgencyData();

        // Following are simple objects that don't require combining tables
        processFareAttributes();
        processFareRules();
        processTransfers();

        // Sometimes will be using a partial configuration. For example, for
        // MBTA commuter rail only want to use the trips defined for
        // commuter rail even though the GTFS data can have trips for
        // other modes defined. This can mean that the data includes many
        // stops that are actually not used by the subset of trips.
        // Therefore trim out the unused stops.
        trimStops();

        // Get rid of calendars and calendar dates that don't have any trips
        // associated to try to pare down number of service IDs. Especially
        // useful when processing just part of an agency config, like
        // MBTA commuter rail.
        trimCalendars();

        // Optionally output routes for debug graphing
        outputRoutesForGraphing();

        // Now process travel times and update the Trip objects.
        TravelTimesProcessorForGtfsUpdates travelTimesProcessor = new TravelTimesProcessorForGtfsUpdates(
                revs, originalTravelTimesRev,
                config.getMaxTravelTimeSegmentLength(),
                config.getDefaultWaitTimeAtStopMsec(),
                config.getMaxSpeedKph());
        travelTimesProcessor.process(session, this);
    }
}
