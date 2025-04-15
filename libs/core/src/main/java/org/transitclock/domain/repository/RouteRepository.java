package org.transitclock.domain.repository;

import org.hibernate.HibernateException;
import org.hibernate.Session;

import org.transitclock.domain.structs.Route;
import org.transitclock.utils.StringUtils;

import java.util.Comparator;
import java.util.List;

public class RouteRepository extends BaseRepository<Route> {
    /**
     * Comparator for sorting Routes into proper order.
     *
     * <p>If routeOrder is set and is below 1,000 then the route will be at beginning of list and
     * will be ordered by routeOrder. If routeOrder is set and is above 1,000,000 then route will be
     * put at end of list and will be ordered by routeOrder. If routeOrder is not set then will
     * order by route short name. If route short name starts with numbers it will be padded by zeros
     * so that proper numerical order will be used.
     */
    public static final Comparator<Route> routeComparator = new Comparator<>() {
        /**
         * Returns negative if r1<r2, zero if r1=r2, and positive if r1>r2
         */
        @Override
        public int compare(Route r1, Route r2) {
            var r1Order = r1.getRouteOrder();
            boolean r1atBeginning = r1Order != null && r1Order < BEGINNING_OF_LIST_ROUTE_ORDER;
            boolean r1atEnd = r1Order != null && r1Order > END_OF_LIST_ROUTE_ORDER;


            var r2Order = r2.getRouteOrder();
            boolean r2atBeginning = r2Order != null && r2Order < BEGINNING_OF_LIST_ROUTE_ORDER;
            boolean r2atEnd = r2Order != null && r2Order > END_OF_LIST_ROUTE_ORDER;


            // Handle if routeOrder indicates r1 should be at beginning of list
            if (r1atBeginning) {
                // If r2 also at beginning and it should be before r1...
                if (r1atBeginning && r1.getRouteOrder() > r2.getRouteOrder()) {
                    return 1;
                } else {
                    return -1;
                }
            }

            // Handle if routeOrder indicates r1 should be at end of list
            if (r1atEnd) {
                // If r2 also at end and it should be after r1...
                if (r2atEnd && r1.getRouteOrder() < r2.getRouteOrder()) {
                    return -1;
                } else {
                    return 1;
                }
            }

            // r1 is in the middle so check to see if r2 is at beginning or end
            if (r2atBeginning) {
                return 1;
            }
            if (r2atEnd) {
                return -1;
            }

            // Both r1 and r2 don't have a route order to order them by
            // route name
            return StringUtils.paddedName(r1.getName()).compareTo(StringUtils.paddedName(r2.getName()));
        }
    };
    // For dealing with route order
    private static final int BEGINNING_OF_LIST_ROUTE_ORDER = 1000;
    private static final int END_OF_LIST_ROUTE_ORDER = 1000000;
    /**
     * Deletes rev from the Routes table
     *
     * @param session
     * @param configRev
     * @return Number of rows deleted
     * @throws HibernateException
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        // Note that hql uses class name, not the table name
        return session.createMutationQuery("DELETE Route WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
    }

    /**
     * Returns List of Route objects for the specified database revision. Orders them based on the
     * GTFS route_order extension or the route short name if route_order not set.
     *
     * @param session
     * @param configRev
     * @return Map of routes keyed on routeId
     * @throws HibernateException
     */
    public static List<Route> getRoutes(Session session, int configRev) throws HibernateException {
        // Get list of routes from database
        List<Route> routesList = session.createQuery("FROM Route WHERE configRev = :configRev ORDER BY routeOrder, shortName", Route.class)
                .setParameter("configRev", configRev)
                .list();

        // Need to set the route order for each route so that can sort
        // predictions based on distance from stop and route order. For
        // the routes that didn't have route ordered configured in db
        // start with 1000 and count on up.
        int routeOrderForWhenNotConfigured = 1000;
        for (Route route : routesList) {
            var routeOrder = route.getRouteOrder();
            boolean atBeginning = routeOrder != null && routeOrder < BEGINNING_OF_LIST_ROUTE_ORDER;
            boolean atEnd = routeOrder != null && routeOrder > END_OF_LIST_ROUTE_ORDER;

            if (!atBeginning && !atEnd) {
                route.setRouteOrder(routeOrderForWhenNotConfigured++);
            }
        }

        // Return the list of routes
        return routesList;
    }
}
