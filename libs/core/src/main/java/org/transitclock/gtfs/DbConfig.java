/* (C)2023 */
package org.transitclock.gtfs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.transitclock.core.ServiceUtils;
import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.repository.AgencyRepository;
import org.transitclock.domain.repository.BlockRepository;
import org.transitclock.domain.repository.CalendarDateRepository;
import org.transitclock.domain.repository.CalendarRepository;
import org.transitclock.domain.repository.FareAttributeRepository;
import org.transitclock.domain.repository.FareRuleRepository;
import org.transitclock.domain.repository.FrequencyRepository;
import org.transitclock.domain.repository.RouteRepository;
import org.transitclock.domain.repository.StopRepository;
import org.transitclock.domain.repository.TransferRepository;
import org.transitclock.domain.repository.TripPatternRepository;
import org.transitclock.domain.repository.TripRepository;
import org.transitclock.domain.structs.Agency;
import org.transitclock.domain.structs.Block;
import org.transitclock.domain.structs.Calendar;
import org.transitclock.domain.structs.CalendarDate;
import org.transitclock.domain.structs.FareAttribute;
import org.transitclock.domain.structs.FareRule;
import org.transitclock.domain.structs.Frequency;
import org.transitclock.domain.structs.Route;
import org.transitclock.domain.structs.Stop;
import org.transitclock.domain.structs.Transfer;
import org.transitclock.domain.structs.Trip;
import org.transitclock.domain.structs.TripPattern;
import org.transitclock.properties.ServiceProperties;
import org.transitclock.utils.IntervalTimer;
import org.transitclock.utils.MapKey;
import org.transitclock.utils.SystemTime;
import org.transitclock.utils.Time;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;

/**
 * Reads all the configuration data from the database. The data is based on GTFS but is heavily
 * processed into things like TripPatterns and better Paths to make the data far easier to use.
 * <br/>
 * DbConfig is intended for the core application such that the necessary top level data can be
 * read in at system startup. This doesn't read in all the low-level data such as paths and travel
 * times. Those items are very voluminous and are therefore lazy loaded.
 *
 * @author SkiBu Smith
 */
@Slf4j
@Getter
public class DbConfig {

    private final String agencyId;

    // Keeps track of which revision of config data was read in
    private final int configRev;

    // Following is for all the data read from the database
    private List<Block> blocks;

    // So can access blocks by service ID and block ID easily.
    // Keyed on serviceId. Submap keyed on blockId
    private Map<String, Map<String, Block>> blocksByServiceMap = null;

    // So can access blocks by service ID and route ID easily
    private Map<RouteServiceMapKey, List<Block>> blocksByRouteMap = null;

    // Ordered list of routes
    private List<Route> routes;
    // Keyed on routeId
    private Map<String, Route> routesByRouteIdMap;
    // Keyed on routeShortName
    private Map<String, Route> routesByRouteShortNameMap;
    // Keyed on stopiD
    private Map<String, Collection<Route>> routesListByStopIdMap;

    // Keyed on routeId
    private Map<String, List<TripPattern>> tripPatternsByRouteMap;
    // For when reading in all trips from db. Keyed on tripId
    private Map<String, Trip> tripsMap;
    // For trips that have been read in individually. Keyed on tripId.
    private final Map<String, Trip> individualTripsMap = new HashMap<>();
    // For trips that have been read in individually. Keyed on trip short name.
    // Contains
    private final Map<String, List<Trip>> individualTripsByShortNameMap = new HashMap<>();

    private List<Agency> agencies;
    private List<Calendar> calendars;
    private List<CalendarDate> calendarDates;
    // So can efficiently look up calendar dates
    private Map<Long, List<CalendarDate>> calendarDatesMap;
    private Map<String, Calendar> calendarByServiceIdMap;
    private List<FareAttribute> fareAttributes;
    private List<FareRule> fareRules;
    private List<Frequency> frequencies;
    private List<Transfer> transfers;

    // Keyed by stop_id.
    private Map<String, Stop> stopsMap;
    // Keyed by stop_code
    private Map<Integer, Stop> stopsByStopCode;

    // Remember the session. This is a bit odd because usually
    // close sessions but want to keep it open so can do lazy loading
    // and so that can read in TripPatterns later using the same session.
    private Session globalSession;

    private final ServiceUtils serviceUtils;
    private final Time time;

    public DbConfig(ServiceProperties serviceProperties, String agencyId, int configRev) {
        this.agencyId = agencyId;
        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Reading configuration database for configRev={}...", configRev);

        // Remember which revision of data is being used
        this.configRev = configRev;

        // Do the low-level processing
        try {
            actuallyReadData(configRev);
        } catch (HibernateException e) {
            logger.error(
                    "Error reading configuration data from db for "
                            + "configRev={}. NOTE: Exiting because could not read in "
                            + "data!!!!",
                    configRev,
                    e);

            System.exit(-1);
        }

        // Let user know what is going on
        logger.info("Finished reading configuration data from database . " + "Took {} msec.", timer.elapsedMsec());
        this.serviceUtils = new ServiceUtils(serviceProperties, this);
        this.time = new Time(this);
    }

    /**
     * Returns the global session used for lazy loading data. Useful for determining if the global
     * session has changed.
     *
     * @return the global session used for lazy loading of data
     */
    public final Session getGlobalSession() {
        return globalSession;
    }

    /**
     * For when the session dies, which happens when db failed over or rebooted. Idea is to create a
     * new session that can be attached to persistent objects so can lazy load data.
     */
    public void createNewGlobalSession() {
        logger.info("Creating a new session for agencyId={}", agencyId);
        HibernateUtils.clearSessionFactory();
        globalSession = HibernateUtils.getSession(agencyId);
    }

    /**
     * Creates a map of a map so that blocks can be looked up easily by service and block IDs.
     *
     * @param blocks List of blocks to be put into map
     * @return Map keyed on service ID of map keyed on block ID of blocks
     */
    private static Map<String, Map<String, Block>> putBlocksIntoMap(List<Block> blocks) {
        Map<String, Map<String, Block>> blocksByServiceMap = new HashMap<>();

        for (Block block : blocks) {
            Map<String, Block> blocksByBlockIdMap =
                    blocksByServiceMap.computeIfAbsent(block.getServiceId(), k -> new HashMap<>());
            blocksByBlockIdMap.put(block.getId(), block);
        }

        return blocksByServiceMap;
    }

    private static class RouteServiceMapKey extends MapKey {
        private RouteServiceMapKey(String serviceId, String routeId) {
            super(serviceId, routeId);
        }

        @Override
        public String toString() {
            return "RouteServiceMapKey [" + "serviceId=" + o1 + ", routeId=" + o2 + "]";
        }
    }

    /**
     * To be used by putBlocksIntoMapByRoute().
     *
     * @param serviceId
     * @param routeId
     * @param block
     */
    private static void addBlockToMapByRouteMap(
            Map<RouteServiceMapKey, List<Block>> blocksByRouteMap, String serviceId, String routeId, Block block) {
        RouteServiceMapKey key = new RouteServiceMapKey(serviceId, routeId);
        List<Block> blocksList = blocksByRouteMap.computeIfAbsent(key, k -> new ArrayList<>());
        blocksList.add(block);
    }

    /**
     * Takes in List of Blocks read from db and puts them into the blocksByRouteMap so that
     * getBlocksForRoute() can be used to retrieve the list of blocks that are associated with a
     * route for a specified service ID.
     *
     * @param blocks
     * @return the newly created blocksByRouteMap
     */
    private static Map<RouteServiceMapKey, List<Block>> putBlocksIntoMapByRoute(List<Block> blocks) {
        Map<RouteServiceMapKey, List<Block>> blocksByRouteMap = new HashMap<RouteServiceMapKey, List<Block>>();

        for (Block block : blocks) {
            String serviceId = block.getServiceId();

            Collection<String> routeIdsForBlock = block.getRouteIds();
            for (String routeId : routeIdsForBlock) {
                // Add the block to the map by keyed serviceId and routeId
                addBlockToMapByRouteMap(blocksByRouteMap, serviceId, routeId, block);

                // Also add block to map using serviceId of null so that
                // can retrieve blocks for all service classes for a route
                // by using a service ID of null.
                addBlockToMapByRouteMap(blocksByRouteMap, null, routeId, block);
            }
        }

        return blocksByRouteMap;
    }

    /**
     * Returns List of Blocks associated with the serviceId and routeId.
     *
     * @param serviceId Specified service ID that want blocks for. Can set to null to blocks for all
     *     service IDs for the route.
     * @param routeId
     * @return List of Blocks. Null of no blocks for the serviceId and routeId
     */
    public List<Block> getBlocksForRoute(String serviceId, String routeId) {
        RouteServiceMapKey key = new RouteServiceMapKey(serviceId, routeId);
        return blocksByRouteMap.get(key);
    }

    /**
     * Returns List of Blocks associated with the routeId for all service IDs.
     *
     * @param routeId
     * @return
     */
    public List<Block> getBlocksForRoute(String routeId) {
        return getBlocksForRoute(null, routeId);
    }

    /**
     * Converts the stops list into a map.
     *
     * @param stopsList To be converted
     * @return The map, keyed on stop_id
     */
    private static Map<String, Stop> putStopsIntoMap(List<Stop> stopsList) {
        Map<String, Stop> map = new HashMap<>();
        for (Stop stop : stopsList) {
            map.put(stop.getId(), stop);
        }
        return map;
    }

    /**
     * Converts the stops list into a map keyed by stop code.
     *
     * @param stopsList To be converted
     * @return The map, keyed on stop_code
     */
    private static Map<Integer, Stop> putStopsIntoMapByStopCode(List<Stop> stopsList) {
        Map<Integer, Stop> map = new HashMap<>();
        for (Stop stop : stopsList) {
            Integer stopCode = stop.getCode();
            if (stopCode != null) {
                map.put(stopCode, stop);
            }
        }
        return map;
    }

    /**
     * Returns the stop IDs for the specified route. Stop IDs can be included multiple times.
     *
     * @param routeId
     * @return collection of stop IDs for route
     */
    private Collection<String> getStopIdsForRoute(String routeId) {
        List<String> stopIds = new ArrayList<>();
        List<TripPattern> tripPatternsForRoute = tripPatternsByRouteMap.get(routeId);
        if (tripPatternsForRoute != null) {
            for (TripPattern tripPattern : tripPatternsForRoute) {
                stopIds.addAll(tripPattern.getStopIds());
            }
        } else {
            logger.error("No pattern for route {}", routeId);
        }

        return stopIds;
    }

    /**
     * Returns map, keyed on stopId, or collection of routes. Allows one to determine all routes
     * associated with a stop.
     *
     * @param routes
     * @return map, keyed on stopId, or collection of routes
     */
    private Map<String, Collection<Route>> putRoutesIntoMapByStopId(List<Route> routes) {
        Map<String, Collection<Route>> map = new HashMap<>();
        for (Route route : routes) {
            for (String stopId : getStopIdsForRoute(route.getId())) {
                var routesForStop = map.computeIfAbsent(stopId, k -> new HashSet<>());
                routesForStop.add(route);
            }
        }

        // Return the created map
        return map;
    }

    /**
     * Converts trip patterns into map keyed on route ID
     *
     * @param tripPatterns
     * @return
     */
    private static Map<String, List<TripPattern>> putTripPatternsIntoMap(List<TripPattern> tripPatterns) {
        Map<String, List<TripPattern>> map = new HashMap<>();
        for (TripPattern tripPattern : tripPatterns) {
            String routeId = tripPattern.getRouteId();
            List<TripPattern> tripPatternsForRoute = map.computeIfAbsent(routeId, k -> new ArrayList<>());
            tripPatternsForRoute.add(tripPattern);
        }

        return map;
    }

    /**
     * Reads in trips patterns from db and puts them into a map
     *
     * @return trip patterns map, keyed by route ID
     */
    private Map<String, List<TripPattern>> putTripPatternsInfoRouteMap() {
        IntervalTimer timer = new IntervalTimer();
        logger.debug("About to load trip patterns for all routes...");

        // Use the global session so that don't need to read in any
        // trip patterns that have already been read in as part of
        // reading in block assignments. This makes reading of the
        // trip pattern data much faster.
        List<TripPattern> tripPatterns = TripPatternRepository.getTripPatterns(globalSession, configRev);
        Map<String, List<TripPattern>> theTripPatternsByRouteMap = putTripPatternsIntoMap(tripPatterns);

        logger.debug("Reading trip patterns for all routes took {} msec", timer.elapsedMsec());

        return theTripPatternsByRouteMap;
    }

    /**
     * Returns the list of trip patterns associated with the specified route. Reads the trip
     * patterns from the database and stores them in cache so that subsequent calls get them
     * directly from the cache. The first time this is called it can take a few seconds. Therefore
     * this is not done at startup since want startup to be quick.
     *
     * @param routeId
     * @return List of TripPatterns for the route, or null if no such route
     */
    public List<TripPattern> getTripPatternsForRoute(String routeId) {
        // If haven't read in the trip pattern data yet, do so now and cache it
        if (tripPatternsByRouteMap == null) {
            logger.error("tripPatternsByRouteMap not set when " + "getTripPatternsForRoute() called. Exiting!");
            System.exit(-1);
        }

        // Return cached trip pattern data
        return tripPatternsByRouteMap.get(routeId);
    }

    /**
     * Returns cached map of all Trips. Can be slow first time accessed because it can take a while
     * to read in all trips including all sub-data.
     *
     * @return
     */
    public Map<String, Trip> getTrips() {
        if (tripsMap == null) {
            IntervalTimer timer = new IntervalTimer();

            // Need to sync such that block data, which includes trip
            // pattern data, is only read serially (not read simultaneously
            // by multiple threads). Otherwise get a "force initialize loading
            // collection" error.
            synchronized (BlockRepository.getLazyLoadingSyncObject()) {
                logger.debug("About to load trips...");

                // Use the global session so that don't need to read in any
                // trip patterns that have already been read in as part of
                // reading in block assignments. This makes reading of the
                // trip pattern data much faster.
                tripsMap = TripRepository.getTrips(globalSession, configRev);
            }
            logger.debug("Reading trips took {} msec", timer.elapsedMsec());
        }

        // Return cached trip data
        return tripsMap;
    }

    /**
     * For more quickly getting a trip. If trip not already read in yet it only reads in the
     * specific trip from the db, not all trips like getTrips(). If trip ID not found then sees if
     * can match to a trip short name.
     *
     * @param tripIdOrShortName
     * @return The trip, or null if no such trip
     */
    public Trip getTrip(String tripIdOrShortName) {
        Trip trip = individualTripsMap.get(tripIdOrShortName);

        // If trip not read in yet, do so now
        if (trip == null) {
            logger.debug(
                    "Trip for tripIdOrShortName={} not read from db yet " + "so reading it now.", tripIdOrShortName);

            // Need to sync such that block data, which includes trip
            // pattern data, is only read serially (not read simultaneously
            // by multiple threads). Otherwise get a "force initialize loading
            // collection" error.
            synchronized (BlockRepository.getLazyLoadingSyncObject()) {
                trip = TripRepository.getTrip(globalSession, configRev, tripIdOrShortName);
            }

            if (trip != null) {
                individualTripsMap.put(tripIdOrShortName, trip);
            }
        }

        // If couldn't get trip by tripId then see if using the trip short name.
        if (trip == null) {
            logger.debug(
                    "Could not find tripId={} so seeing if there is a tripShortName with that ID.",
                    tripIdOrShortName);
            trip = getTripUsingTripShortName(tripIdOrShortName);

            // If the trip successfully read in it also needs to be added to
            // individualTripsMap so that it doesn't need to be read in next
            // time getTrip() is called.
            if (trip != null) {
                logger.debug("Read tripIdOrShortName={} from db", tripIdOrShortName);
                individualTripsMap.put(trip.getId(), trip);
            }
        }

        return trip;
    }

    /**
     * Looks through the trips passed in and returns the one that has a service ID that is currently
     * valid.
     *
     * @param trips
     * @return trip whose service ID is currently valid
     */
    private Trip getTripForCurrentService(List<Trip> trips) {
        Date now = SystemTime.getDate();
        Collection<String> currentServiceIds = serviceUtils.getServiceIds(now);
        for (Trip trip : trips) {
            for (String serviceId : currentServiceIds) {
                if (trip.getServiceId().equals(serviceId)) {
                    // Found a service ID match so return this trip
                    return trip;
                }
            }
        }

        // No such trip is currently active
        return null;
    }

    /**
     * For more quickly getting a trip. If trip not already read in yet it only reads in the
     * specific trip from the db, not all trips like getTrips().
     *
     * @param tripShortName
     * @return
     */
    public Trip getTripUsingTripShortName(String tripShortName) {
        // Find trip with the tripShortName with a currently active service ID
        // from the map. If found, return it.
        List<Trip> trips = individualTripsByShortNameMap.get(tripShortName);
        if (trips != null) {
            Trip trip = getTripForCurrentService(trips);
            if (trip != null) {
                logger.debug("Read in trip using tripShortName={}", tripShortName);

                return trip;
            } else {
                // Trips for the tripShortName already read in but none valid
                // for the current service IDs so return null
                logger.debug(
                        "When reading tripShortName={} found trips " + "but not for current service.", tripShortName);
                return null;
            }
        }

        logger.info("FIXME tripShortName={} not yet read from db so reading it in now", tripShortName);

        // Trips for the short name not read in yet, do so now
        // Need to sync such that block data, which includes trip
        // pattern data, is only read serially (not read simultaneously
        // by multiple threads). Otherwise get a "force initialize loading
        // collection" error.
        synchronized (BlockRepository.getLazyLoadingSyncObject()) {
            trips = TripRepository.getTripByShortName(globalSession, configRev, tripShortName);
        }

        // Add the newly read trips to the map
        individualTripsByShortNameMap.put(tripShortName, trips);

        return getTripForCurrentService(trips);
    }

    /**
     * Creates a map of routes keyed by route ID so that can easily find a route using its ID.
     *
     * @param routes
     * @return
     */
    private static Map<String, Route> putRoutesIntoMapByRouteId(List<Route> routes) {
        // Convert list of routes to a map keyed on routeId
        Map<String, Route> routesMap = new HashMap<>();
        for (Route route : routes) {
            routesMap.put(route.getId(), route);
        }
        return routesMap;
    }

    /**
     * Creates a map of routes keyed by route short name so that can easily find a route.
     *
     * @param routes
     * @return
     */
    private static Map<String, Route> putRoutesIntoMapByRouteShortName(List<Route> routes) {
        // Convert list of routes to a map keyed on routeId
        Map<String, Route> routesMap = new HashMap<>();
        for (Route route : routes) {
            routesMap.put(route.getShortName(), route);
        }
        return routesMap;
    }

    /**
     * Reads the individual data structures from the database.
     *
     * @param configRev
     */
    private void actuallyReadData(int configRev) {
        IntervalTimer timer;

        // Open up Hibernate session so can read in data. Remember this
        // session as a member variable. This is a bit odd because usually
        // close sessions but want to keep it open so can do lazy loading
        // and so that can read in TripPatterns later using the same session.
        globalSession = HibernateUtils.getSession(agencyId);

        // // NOTE. Thought that it might speed things up if would read in
        // // trips, trip patterns, and stopPaths all at once so that can use a
        // single
        // // query instead of one for each trip or trip pattern when block data
        // is
        // // read in. But surprisingly it didn't speed up the overall queries.
        // // Yes, reading in trips and trip patterns first means that reading
        // // in blocks takes far less time. But the total time for reading
        // // everything in stays the same. The tests were done on a laptop
        // // that both contained DbConfig program plus the database. So
        // // should conduct this test again with the database on a different
        // // server because perhaps then reading in trips and trip patterns
        // // first might make a big difference.
        // timer = new IntervalTimer();
        // List<Trip> trips = Trip.getTrips(session, configRev);
        // System.out.println("Reading trips took " + timer.elapsedMsec() +
        // " msec");
        //
        // timer = new IntervalTimer();
        // tripPatterns = TripPattern.getTripPatterns(session, configRev);
        // System.out.println("Reading trip patterns took " +
        // timer.elapsedMsec() + " msec");
        //
        // timer = new IntervalTimer();
        // stopPaths = StopPath.getPaths(session, configRev);
        // logger.debug("Reading stopPaths took {} msec", timer.elapsedMsec());

        timer = new IntervalTimer();
        blocks = BlockRepository.getBlocks(globalSession, configRev);
        blocksByServiceMap = putBlocksIntoMap(blocks);
        blocksByRouteMap = putBlocksIntoMapByRoute(blocks);
        logger.debug("Reading blocks took {} msec", timer.elapsedMsec());

        timer = new IntervalTimer();
        routes = RouteRepository.getRoutes(globalSession, configRev);
        routesByRouteIdMap = putRoutesIntoMapByRouteId(routes);
        routesByRouteShortNameMap = putRoutesIntoMapByRouteShortName(routes);
        logger.debug("Reading routes took {} msec", timer.elapsedMsec());

        tripPatternsByRouteMap = putTripPatternsInfoRouteMap();

        timer = new IntervalTimer();
        List<Stop> stopsList = StopRepository.getStops(globalSession, configRev);
        stopsMap = putStopsIntoMap(stopsList);
        stopsByStopCode = putStopsIntoMapByStopCode(stopsList);
        routesListByStopIdMap = putRoutesIntoMapByStopId(routes);
        logger.debug("Reading stops took {} msec", timer.elapsedMsec());

        timer = new IntervalTimer();
        agencies = AgencyRepository.getAgencies(globalSession, configRev);
        calendars = CalendarRepository.getCalendars(globalSession, configRev);
        calendarDates = CalendarDateRepository.getCalendarDates(globalSession, configRev);

        calendarByServiceIdMap = new HashMap<>();
        for (Calendar calendar : calendars) {
            if(calendarByServiceIdMap.get(calendar.getServiceId()) == null){
                calendarByServiceIdMap.put(calendar.getServiceId(), calendar);
            } else{
                logger.warn("Duplicate Service Id {} in Calendar", calendar.getServiceId());
            }
        }

        calendarDatesMap = new HashMap<>();
        for (CalendarDate calendarDate : calendarDates) {
            Long time = calendarDate.getTime();
            List<CalendarDate> calendarDatesForDate = calendarDatesMap.computeIfAbsent(time, k -> new ArrayList<>(1));
            calendarDatesForDate.add(calendarDate);
        }

        fareAttributes = FareAttributeRepository.getFareAttributes(globalSession, configRev);
        fareRules = FareRuleRepository.getFareRules(globalSession, configRev);
        frequencies = FrequencyRepository.getFrequencies(globalSession, configRev);
        transfers = TransferRepository.getTransfers(globalSession, configRev);

        logger.debug("Reading everything else took {} msec", timer.elapsedMsec());
    }

    /**
     * Returns the block specified by the service and block ID parameters.
     *
     * @param serviceId Specified which service class to look for block. If null then will use the
     *     first service class for the current time that has the specified block.
     * @param blockId Specifies which block to return
     * @return The block for the service and block IDs specified, or null if no such block.
     */
    public Block getBlock(String serviceId, String blockId) {
        // If service ID not specified then use today's. This way
        // makes it easier to find block info
        if (serviceId != null) {
            // For determining blocks for the service
            Map<String, Block> blocksMap = blocksByServiceMap.get(serviceId);

            // If no such service class defined for the blocks then return
            // null. This can happen if service classes are defined that
            // even though no blocks use that service class, such as when
            // working with a partial configuration.
            if (blocksMap == null) return null;

            return blocksMap.get(blockId);
        } else {
            // Service ID was not specified so determine current ones for now
            Date now = SystemTime.getDate();

            Collection<String> currentServiceIds = serviceUtils.getServiceIds(now);
            for (String currentServiceId : currentServiceIds) {
                Block block = getBlock(currentServiceId, blockId);
                if (block != null) return block;
            }

            // Couldn't find that block ID for any of the current service IDs
            return null;
        }
    }

    public int getBlockCount() {
        int blockCount = 0;
        for (String serviceId : blocksByServiceMap.keySet()) {
            blockCount += (blocksByServiceMap.get(serviceId) != null
                    ? blocksByServiceMap.get(serviceId).size()
                    : 0);
        }
        return blockCount;
    }

    /**
     * Returns sorted lists of block IDs what belong to all service IDs
     *
     * @return Map of all service IDs with belong to block IDs
     */
    public Map<String, List<String>> getBlockIdsForAllServiceIds() {
        Map<String, List<String>> serviceIdsWithBlocks = new HashMap<>();

        blocksByServiceMap.forEach((key, element) -> {
            List<String> ids = new ArrayList<>();
            element.forEach((innerKey, block) -> ids.add(block.getId()));
            Collections.sort(ids);
            serviceIdsWithBlocks.put(key, ids);
        });
        return serviceIdsWithBlocks;
    }

    /**
     * Returns blocks for the specified blockId for all service IDs.
     *
     * @param blockId Which blocks to return
     * @return Collection of blocks
     */
    public Collection<Block> getBlocksForAllServiceIds(String blockId) {
        Collection<Block> blocks = new ArrayList<>();

        Collection<String> serviceIds = blocksByServiceMap.keySet();
        for (String serviceId : serviceIds) {
            Block block = getBlock(serviceId, blockId);
            if (block != null) blocks.add(block);
        }

        return blocks;
    }

    /**
     * Returns unmodifiable collection of blocks associated with the specified serviceId.
     *
     * @param serviceId
     * @return Blocks associated with service ID. If no blocks then an empty collection is returned
     *     instead of null.
     */
    public Collection<Block> getBlocks(String serviceId) {
        Map<String, Block> blocksForServiceMap = blocksByServiceMap.get(serviceId);
        if (blocksForServiceMap != null) {
            Collection<Block> blocksForService = blocksForServiceMap.values();
            return Collections.unmodifiableCollection(blocksForService);
        } else {
            return new ArrayList<>(0);
        }
    }

    /**
     * Returns unmodifiable list of blocks for the agency.
     *
     * @return blocks for the agency
     */
    public List<Block> getBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    /**
     * Returns Map of routesMap keyed on the routeId.
     *
     * @return
     */
    public Map<String, Route> getRoutesByRouteIdMap() {
        return Collections.unmodifiableMap(routesByRouteIdMap);
    }

    /**
     * Returns ordered list of routes.
     *
     * @return
     */
    public List<Route> getRoutes() {
        return Collections.unmodifiableList(routes);
    }

    /**
     * Returns the Route with the specified routeId.
     *
     * @param routeId
     * @return The Route specified by the ID, or null if no such route
     */
    public Route getRouteById(String routeId) {
        return routesByRouteIdMap.get(routeId);
    }

    /**
     * Returns the Route with the specified routeShortName
     *
     * @param routeShortName
     * @return The route, or null if route doesn't exist
     */
    public Route getRouteByShortName(String routeShortName) {
        return routesByRouteShortNameMap.get(routeShortName);
    }

    /**
     * Returns the Stop with the specified stopId.
     *
     * @param stopId
     * @return The stop, or null if no such stop
     */
    public Stop getStop(String stopId) {
        return stopsMap.get(stopId);
    }

    /**
     * Returns the Stop with the specified stopCode.
     *
     * @param stopCode
     * @return The stop, or null if no such stop
     */
    public Stop getStop(Integer stopCode) {
        return stopsByStopCode.get(stopCode);
    }

    /**
     * Returns collection of routes that use the specified stop.
     *
     * @param stopId
     * @return collection of routes for the stop
     */
    public Collection<Route> getRoutesForStop(String stopId) {
        return routesListByStopIdMap.get(stopId);
    }

    /**
     * Returns list of all calendars
     *
     * @return calendars
     */
    public List<Calendar> getCalendars() {
        return Collections.unmodifiableList(calendars);
    }



    public Calendar getCalendarByServiceId(String serviceId) {
        return calendarByServiceIdMap.get(serviceId);
    }

    /**
     * Returns list of calendars that are currently active
     *
     * @return current calendars
     */
    public List<Calendar> getCurrentCalendars() {
        // Get list of currently active calendars
        return serviceUtils.getCurrentCalendars(SystemTime.getMillis());
    }

    /**
     * Returns list of all calendar dates from the GTFS calendar_dates.txt file.
     *
     * @return list of calendar dates
     */
    public List<CalendarDate> getCalendarDates() {
        return Collections.unmodifiableList(calendarDates);
    }

    /**
     * Returns CalendarDate for the current day. This method is pretty quick since it looks through
     * a hashmap, instead of doing a linear search through a possibly very large number of dates.
     *
     * @return CalendarDate for current day if there is one, otherwise null.
     */
    public List<CalendarDate> getCalendarDatesForNow() {
        long startOfDay = Time.getStartOfDay(SystemTime.getDate());
        return calendarDatesMap.get(startOfDay);
    }

    /**
     * Returns CalendarDate for the current day. This method is pretty quick since it looks through
     * a hashmap, instead of doing a linear search through a possibly very large number of dates.
     *
     * @param epochTime the time that want calendar dates for
     * @return CalendarDate for current day if there is one, otherwise null.
     */
    public List<CalendarDate> getCalendarDates(Date epochTime) {
        long startOfDay = Time.getStartOfDay(epochTime);
        return calendarDatesMap.get(startOfDay);
    }

    /**
     * Returns list of all service IDs
     *
     * @return service IDs
     */
    public List<String> getServiceIds() {
        List<String> serviceIds = new ArrayList<>();
        for (Calendar calendar : getCalendars()) {
            serviceIds.add(calendar.getServiceId());
        }
        return serviceIds;
    }

    /**
     * Returns list of service IDs that are currently active
     *
     * @return current service IDs
     */
    public List<String> getCurrentServiceIds() {
        List<String> serviceIds = new ArrayList<String>();
        for (Calendar calendar : getCurrentCalendars()) {
            serviceIds.add(calendar.getServiceId());
        }
        return serviceIds;
    }

    /**
     * There can be multiple agencies but usually there will be just one. For getting timezone and
     * such want to be able to easily access the main agency, hence this method.
     *
     * @return The first agency, or null if no agencies configured
     */
    public Agency getFirstAgency() {
        return !agencies.isEmpty() ? agencies.get(0) : null;
    }

    public List<Agency> getAgencies() {
        return Collections.unmodifiableList(agencies);
    }

    /**
     * Returns the database revision of the configuration data that was read in.
     *
     * @return The db rev
     */
    public int getConfigRev() {
        return configRev;
    }
}
