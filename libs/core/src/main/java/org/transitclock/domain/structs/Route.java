/* (C)2023 */
package org.transitclock.domain.structs;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.*;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.DynamicUpdate;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.gtfs.TitleFormatter;
import org.transitclock.gtfs.model.GtfsRoute;
import org.transitclock.utils.OrderedCollection;

/**
 * For storing in db information for a route. Based on GTFS information from routes.txt and other
 * files.
 *
 * @author SkiBu Smith
 */
@Entity
@DynamicUpdate
@Getter @Setter
@Table(name = "routes")
@Slf4j
public class Route implements Serializable {

    @Id
    @Column(name = "config_rev")
    private final int configRev;

    @Id
    @Column(name = "id", length = 60)
    private final String id;

    @Column(name = "color", length = 10)
    private final String color;

    @Column(name = "text_color", length = 10)
    private final String textColor;

    // Not declared final because need to set route order for all
    // routes that did not have the order configured in the db,
    // but can only do so once all routes read in and sorted.
    @Column(name = "route_order")
    private Integer routeOrder;

    @Column(name = "hidden")
    private final boolean hidden;

    @Column(name = "type", length = 2)
    private final String type;

    @Column(name = "description", length = 1024)
    private final String description;

    // Directly from GTFS data
    @Column(name = "short_name")
    private final String shortName;

    // Directly from GTFS data
    @Column(name = "long_name")
    private final String longName;

    // Processed name combing the GTFS route_short_name and route_long_name
    @Column(name = "name")
    private final String name;

    @Embedded
    private final Extent extent;

    // Optional parameter that specifies how far away AVL report can be
    // from segments and still be considered a match.
    @Column(name = "max_distance")
    private final Double maxDistance;

    // Later will probably want to store this in database,
    // but not yet sure. This means it is not available to application!
    @Transient
    @ToString.Exclude
    private final List<TripPattern> tripPatternsForRoute;

    // For getStops()
    @Transient
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Collection<Stop> stops = null;

    // For getPathSegments()
    @Transient
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Collection<Vector> stopPaths = null;

    // For getOrderedStopsByDirection()
    @Transient
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Map<String, List<String>> orderedStopsPerDirectionMap = null;

    // For getStopOrder().
    // Keeps track of stop order for each direction. Keyed on direction id.
    // The submap is keyed on stop id and contains list of stop orders.
    // Need a list since a stop can be in a trip multiple times.
    @Transient
    @EqualsAndHashCode.Exclude
    private Map<String, Map<String, List<Integer>>> stopOrderByDirectionMap = null;

    public Route(
            int configRev, GtfsRoute gtfsRoute, List<TripPattern> tripPatternsForRoute, TitleFormatter titleFormatter) {
        // Because will be writing data to the sandbox in the db
        this.configRev = configRev;

        // Here are most of the params from GtfsRoute
        this.id = gtfsRoute.getRouteId();
        this.color = gtfsRoute.getRouteColor();
        this.textColor = gtfsRoute.getRouteTextColor();
        this.routeOrder = gtfsRoute.getRouteOrder();
        this.hidden = gtfsRoute.getHidden();
        this.type = gtfsRoute.getRouteType();
        this.description = gtfsRoute.getRouteDesc();

        this.longName = titleFormatter.processTitle(gtfsRoute.getRouteLongName());

        // Handle short name specially. route_short_name is optional
        // but Transitime uses it as an identifier since route_ids
        // are not always consistent across schedule changes. Therefore
        // if the GTFS route_short_name is not set then use the
        // route_long_name.
        String shortName = gtfsRoute.getRouteShortName();
        this.shortName = shortName != null && !shortName.isEmpty() ? shortName : gtfsRoute.getRouteLongName();

        // Get the name of the route. Need to do some fancy processing here because
        // need to fix the capitalization using the TitleFormatter. This also
        // does all the regex processing to fix other issues. Might also need
        // to combine short and long names into a single name.
        if (gtfsRoute.getRouteLongName() != null
                && !gtfsRoute.getRouteLongName().isEmpty()) {
            // route_long_name is set so use it

            // Prepend the route short name plus a " - ", but only if
            // route short name is defined and it is short. This way
            // will end up with a route name like "38 - Geary" but
            // not "HYDE-POWELL - Hyde Powell". For LA Metro some route short
            // names to prepend are something like "51/52/352" so need to go up
            // to 9 characters. Also, only prepend the route short name if
            // the route long name doesn't already contain it.
            String shortNameComponent = "";
            if (gtfsRoute.getRouteShortName() != null
                    && gtfsRoute.getRouteShortName().length() <= 9
                    && !this.longName.contains(gtfsRoute.getRouteShortName()))
                shortNameComponent = gtfsRoute.getRouteShortName() + " - ";

            this.name = shortNameComponent + this.longName;
        } else {
            // route_long_name not set so just use the route_short_name
            this.name = titleFormatter.processTitle(this.shortName);
        }

        this.tripPatternsForRoute = tripPatternsForRoute;
        this.maxDistance = gtfsRoute.getMaxDistance();

        // Determine the extent of the route by looking at the extent
        // of all the trip patterns.
        this.extent = new Extent();
        for (TripPattern tp : tripPatternsForRoute) {
            this.extent.add(tp.getExtent());
        }
    }

    /** Needed because Hibernate requires no-arg constructor */
    @SuppressWarnings("unused")
    protected Route() {
        configRev = -1;
        id = null;
        color = null;
        textColor = null;
        routeOrder = null;
        hidden = false;
        type = null;
        description = null;
        shortName = null;
        longName = null;
        name = null;
        extent = null;
        tripPatternsForRoute = null;
        maxDistance = null;
    }


    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Route ["
                + "configRev="
                + configRev
                + ", id="
                + id
                + ", name="
                + name
                + ", color="
                + color
                + ", textColor="
                + textColor
                + ", routeOrder="
                + routeOrder
                + ", hidden="
                + hidden
                + ", type="
                + type
                + ", description="
                + description
                + ", shortName="
                + shortName
                + ", longName="
                + longName
                + ", extent"
                + extent
                + "]";
    }

    /**
     * Returns unordered collection of stops associated with route.
     *
     * <p>Synchronized because caching stops.
     *
     * @return
     */
    public synchronized Collection<Stop> getStops(DbConfig dbConfig) {
        // If stop collection already determined then simply return it
        if (stops != null)
            return stops;

        // Get the trip patterns for the route. Can't use the member
        // variable tripPatternsForRoute since it is only set when the
        // GTFS data is processed and stored in the db. Since this member
        // is transient it is not stored in the db and therefore not
        // available to this client application. But it can be obtained
        // from the DbConfig.
        List<TripPattern> tripPatternsForRoute = dbConfig.getTripPatternsForRoute(id);

        // Stop list not yet determined so determine it now using
        // trip patterns.
        Map<String, Stop> stopMap = new HashMap<>();
        for (TripPattern tripPattern : tripPatternsForRoute) {
            for (StopPath stopPath : tripPattern.getStopPaths()) {
                String stopId = stopPath.getStopId();

                // If already added this stop then continue to next one
                if (stopMap.containsKey(stopId))
                    continue;

                Stop stop = dbConfig.getStop(stopId);
                stopMap.put(stopId, stop);
            }
        }
        stops = stopMap.values();

        // Return the newly created collection of stops
        return stops;
    }

    /**
     * Returns the specified trip pattern, or null if that trip pattern doesn't exist for the route.
     *
     * @param tripPatternId
     * @return
     */
    public TripPattern getTripPattern(DbConfig dbConfig, String tripPatternId) {
        List<TripPattern> tripPatternsForRoute = dbConfig.getTripPatternsForRoute(getId());
        for (TripPattern tripPattern : tripPatternsForRoute) {
            if (Objects.equals(tripPattern.getId(), tripPatternId))
                return tripPattern;
        }

        // Never found the specified trip pattern
        return null;
    }

    /**
     * Returns longest trip pattern for the directionId specified. Note: gets trip patterns from
     * Core, which means it works in the core application, not just when processing GTFS data.
     *
     * @param dbConfig
     * @param directionId
     * @return
     */
    public TripPattern getLongestTripPatternForDirection(DbConfig dbConfig, String directionId) {
        List<TripPattern> tripPatternsForRoute = dbConfig.getTripPatternsForRoute(getId());
        TripPattern longestTripPatternForDir = null;
        for (TripPattern tripPattern : tripPatternsForRoute) {
            if (Objects.equals(tripPattern.getDirectionId(), directionId)) {
                if (longestTripPatternForDir == null || tripPattern.getNumberStopPaths() > longestTripPatternForDir.getNumberStopPaths())
                    longestTripPatternForDir = tripPattern;
            }
        }

        return longestTripPatternForDir;
    }

    /**
     * Returns the longest trip pattern for each direction ID for the route. Will typically be two
     * trip patterns since there are usually two directions per route.
     *
     * @return
     */
    public List<TripPattern> getLongestTripPatternForEachDirection(DbConfig dbConfig) {
        List<TripPattern> tripPatterns = new ArrayList<>();

        List<String> directionIds = getDirectionIds(dbConfig);
        for (String directionId : directionIds) {
            tripPatterns.add(getLongestTripPatternForDirection(dbConfig, directionId));
        }

        return tripPatterns;
    }

    /**
     * Returns list of trip patterns for the directionId specified.
     *
     * @param directionId
     * @return
     */
    public List<TripPattern> getTripPatterns(DbConfig dbConfig, String directionId) {
        List<TripPattern> tripPatternsForRoute = dbConfig.getTripPatternsForRoute(getId());
        List<TripPattern> tripPatternsForDir = new ArrayList<>();
        for (TripPattern tripPattern : tripPatternsForRoute) {
            if (Objects.equals(tripPattern.getDirectionId(), directionId)) tripPatternsForDir.add(tripPattern);
        }

        return tripPatternsForDir;
    }

    /**
     * Returns list of direction IDs for the route.
     *
     * @return
     */
    public List<String> getDirectionIds(DbConfig dbConfig) {
        List<TripPattern> tripPatternsForRoute = dbConfig.getTripPatternsForRoute(getId());
        if (tripPatternsForRoute == null) {
            return new ArrayList<>();
        }

        Set<String> directionIds = new HashSet<>();
        for (TripPattern tripPattern : tripPatternsForRoute) {
            directionIds.add(tripPattern.getDirectionId());
        }
        return directionIds.stream().toList();
    }

    /**
     * Returns unordered collection of path vectors associated with route
     *
     * @return
     */
    public synchronized Collection<org.transitclock.domain.structs.Vector> getPathSegments(DbConfig dbConfig) {
        // If stop paths collection already determined then simply return it
        if (stopPaths != null) return stopPaths;

        // Get the trip patterns for the route. Can't use the member
        // variable tripPatternsForRoute since it is only set when the
        // GTFS data is processed and stored in the db. Since this member
        // is transient it is not stored in the db and therefore not
        // available to this client application. But it can be obtained
        // from the DbConfig.
        List<TripPattern> tripPatternsForRoute = dbConfig.getTripPatternsForRoute(id);

        Map<String, StopPath> stopPathMap = new HashMap<>();
        for (TripPattern tripPattern : tripPatternsForRoute) {
            for (StopPath stopPath : tripPattern.getStopPaths()) {
                String stopPathId = stopPath.getId();

                // If already added this stop then continue to next one
                if (stopPathMap.containsKey(stopPathId)) continue;

                stopPathMap.put(stopPathId, stopPath);
            }
        }

        // For each of the unique stop paths add the vectors to the collection
        stopPaths = new ArrayList<Vector>(stopPathMap.values().size());
        for (StopPath stopPath : stopPathMap.values()) {
            stopPaths.addAll(stopPath.getSegmentVectors());
        }

        // Return the newly created collection of stop paths
        return stopPaths;
    }

    /**
     * For each GTFS direction ID returns list of stops that in the appropriate order for the
     * direction. The appropriate order means that when there are different trip patterns that the
     * stops that are different will be inserted appropriately into the list. Synchronized since can
     * be access through multiple threads.
     *
     * @return Map keyed by direction ID and value of List of ordered stop IDs.
     */
    public synchronized Map<String, List<String>> getOrderedStopsByDirection(DbConfig dbConfig) {
        // If already determined the stops return the cached map
        if (orderedStopsPerDirectionMap != null) return orderedStopsPerDirectionMap;

        // Haven't yet determined ordered stops so do so now
        orderedStopsPerDirectionMap = new HashMap<>();

        // For each direction
        for (String directionId : getDirectionIds(dbConfig)) {
            // Determine ordered collection of stops for direction
            OrderedCollection orderedCollection = new OrderedCollection();
            List<TripPattern> tripPatternsForDir = getTripPatterns(dbConfig, directionId);
            List<List<String>> stopIdsForTripPatternList = new ArrayList<>();
            for (TripPattern tripPattern : tripPatternsForDir) {
                List<String> stopIdsForTripPattern = tripPattern.getStopIds();
                stopIdsForTripPatternList.add(stopIdsForTripPattern);
            }
            orderedCollection.add(stopIdsForTripPatternList);

            orderedStopsPerDirectionMap.put(directionId, orderedCollection.get());
        }

        return orderedStopsPerDirectionMap;
    }

    /**
     * Initializes stopOrderByDirectionMap member if haven't done so yet. Used by getStopOrder().
     * Synchronized since can be access through multiple threads.
     */
    private synchronized void createStopOrderByDirectionMapIfNeedTo(DbConfig dbConfig) {
        // If map already created then done
        if (stopOrderByDirectionMap != null) return;

        // Map not yet created so create it now
        stopOrderByDirectionMap = new HashMap<>();
        Map<String, List<String>> orderedStopsByDirection = getOrderedStopsByDirection(dbConfig);

        // Create submap for each direction Id
        for (String directionId : orderedStopsByDirection.keySet()) {
            // Create the submap for the direction ID. Map is keyed on stop Id
            // and contains list of stop order for the stop. Need a list since
            // a stop can be in a trip multiple times.
            Map<String, List<Integer>> stopOrderMap = new HashMap<>();
            stopOrderByDirectionMap.put(directionId, stopOrderMap);

            // Add each stop for the direction Id
            List<String> orderedStopsForDirectionList = orderedStopsPerDirectionMap.get(directionId);
            for (int stopOrder = 0; stopOrder < orderedStopsForDirectionList.size(); ++stopOrder) {
                // Determine stopId (we already have stopOrder)
                String stopId = orderedStopsForDirectionList.get(stopOrder);

                // Get list of stop orders for the stop Id
                List<Integer> stopOrderListForStopId = stopOrderMap.computeIfAbsent(stopId, k -> new ArrayList<Integer>(1));

                // For the stopId add the stop order to its list of stop orders
                stopOrderListForStopId.add(stopOrder);
            }
        }
    }

    /**
     * Gets the order for the stop. Important when have multiple trip patterns for a direction.
     * Allows each stop in the trip to have a unique sequential stop order so that can save the stop
     * order in the db and then use it for db queries to order the stop data for a direction for a
     * route.
     *
     * @param directionId
     * @param stopId
     * @param stopIndex so that can handle stop being on trip multiple times
     * @return the stop order for the stop for the direction for the route
     */
    public int getStopOrder(DbConfig dbConfig, String directionId, String stopId, int stopIndex) {
        // Make sure initialized
        createStopOrderByDirectionMapIfNeedTo(dbConfig);

        // For the direction get the map of stop orders
        Map<String, List<Integer>> stopOrderMap = stopOrderByDirectionMap.get(directionId);
        if (stopOrderMap == null) {
            logger.error("In Route.getStopOrder() directionId={} is not valid " + "for routeId={}.", directionId, id);
            return -1;
        }

        // For the stop Id for the direction get the list of stop orders
        List<Integer> stopOrderListForStopId = stopOrderMap.get(stopId);
        if (stopOrderListForStopId == null) {
            logger.error(
                    "In Route.getStopOrder() stopId={} is not valid " + "for routeId={} directionId={}.",
                    stopId,
                    id,
                    directionId);
            return -1;
        }

        // Return the appropriate stop order. In order to handle situation
        // where stop might be in trip multiple times need to make sure the
        // stop order is greater than the stop index.
        for (int stopOrder : stopOrderListForStopId) {
            if (stopOrder >= stopIndex) return stopOrder;
        }

        // Didn't find appropriate stop order
        logger.error(
                "In Route.getStopOrder() did not find stop order for "
                        + "for routeId={} directionId={} stopId={} stopIndex={}.",
                id,
                directionId,
                stopId,
                stopIndex);
        return -1;
    }

    /**
     * The short name is either specified by route_short_name in the routes.txt GTFS file or if that
     * is null it will be the long name name.
     *
     * @return the short name for the route
     */
    public String getShortName() {
        return shortName != null ? shortName : name;
    }

    /**
     * The long name is either specified by route_long_name in the routes.txt GTFS file or if that
     * is null it will be the short name name.
     *
     * @return the long name for the route
     */
    public String getLongName() {
        return longName != null ? longName : name;
    }

    /**
     * For specifying on a per route basis how far AVL report can be from segment and still have it
     * be considered a match.
     *
     * @return the max distance if set, otherwise NaN
     */
    public double getMaxAllowableDistanceFromSegment() {
        if (maxDistance != null) {
            return maxDistance;
        }

        return Double.NaN;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Route route)) return false;
        return configRev == route.configRev
            && hidden == route.hidden
            && Objects.equals(id, route.id)
            && Objects.equals(color, route.color)
            && Objects.equals(textColor, route.textColor)
            && Objects.equals(routeOrder, route.routeOrder)
            && Objects.equals(type, route.type)
            && Objects.equals(description, route.description)
            && Objects.equals(shortName, route.shortName)
            && Objects.equals(longName, route.longName)
            && Objects.equals(name, route.name)
            && Objects.equals(extent, route.extent)
            && Objects.equals(maxDistance, route.maxDistance)
//            && Objects.equals(tripPatternsForRoute, route.tripPatternsForRoute)
//            && Objects.equals(stops, route.stops)
//            && Objects.equals(stopPaths, route.stopPaths)
//            && Objects.equals(orderedStopsPerDirectionMap, route.orderedStopsPerDirectionMap)
//            && Objects.equals(stopOrderByDirectionMap, route.stopOrderByDirectionMap)
            ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(configRev, id, color, textColor, routeOrder,
            hidden, type, description, shortName, longName, name, extent,
            maxDistance
//            tripPatternsForRoute,
//            stops, stopPaths, orderedStopsPerDirectionMap, stopOrderByDirectionMap
        );
    }
}
