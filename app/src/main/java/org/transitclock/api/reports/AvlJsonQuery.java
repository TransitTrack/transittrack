/* (C)2023 */
package org.transitclock.api.reports;

import org.transitclock.core.reports.GenericJsonQuery;
import org.transitclock.utils.Time;

import java.text.ParseException;

/**
 * Does a query of AVL data and returns result in JSON format.
 *
 * @author SkiBu Smith
 */
public class AvlJsonQuery {
    // Maximum number of rows that can be retrieved by a query
    private static final int MAX_ROWS = 50000;

    /**
     * Queries agency for AVL data and returns result as a JSON string. Limited to returning
     * MAX_ROWS (50,000) data points.
     *
     * @param agencyId
     * @param vehicleId Which vehicle to get data for. Set to null or empty string to get data for
     *     all vehicles
     * @param beginDate date to start query
     * @param numdays of days to collect data for
     * @param beginTime optional time of day during the date range
     * @param endTime optional time of day during the date range
     * @return AVL reports in JSON format. Can be empty JSON array if no data meets criteria.
     */
    public static String getAvlJson(
            String agencyId, String vehicleId, String beginDate, String numdays, String beginTime, String endTime) {
        // Determine the time portion of the SQL
        String timeSql = "";
        // If beginTime or endTime set but not both then use default values
        if ((beginTime != null && !beginTime.isEmpty()) || (endTime != null && !endTime.isEmpty())) {
            if (beginTime == null || beginTime.isEmpty()) beginTime = "00:00";
            if (endTime == null || endTime.isEmpty()) endTime = "24:00";
        }
        // cast('2000-01-01 01:12:00'::timestamp as time);
        if (beginTime != null && !beginTime.isEmpty()) {
            timeSql = " AND cast(time::timestamp as time) BETWEEN '" + beginTime + "' AND '" + endTime + "' ";
        }

        String sql = "SELECT vehicle_id, name, time, assignment_id, lat, lon, speed, heading,"
                    + " time_processed, source FROM avl_reports INNER JOIN vehicle_configs ON"
                    + " vehicle_configs.id = avl_reports.vehicle_id WHERE time BETWEEN  cast(? as"
                    + " timestamp) AND cast(? as timestamp) + INTERVAL '"
                    + numdays
                    + " day' "
                    + timeSql;

        // If only want data for single vehicle then specify so in SQL
        if (vehicleId != null && !vehicleId.isEmpty()) {
            sql += " AND vehicle_id='" + vehicleId + "' ";
        }

        // Make sure data is ordered by vehicleId so that can draw lines
        // connecting the AVL reports per vehicle properly. Also then need
        // to order by time to make sure they are in proper order. And
        // lastly, limit AVL reports to 5000 so that someone doesn't try
        // to view too much data at once.

        sql += "ORDER BY vehicle_id, time LIMIT " + MAX_ROWS;

        try {
            java.util.Date startdate = Time.parseDate(beginDate);

            return GenericJsonQuery.getJsonString(agencyId, sql, startdate, startdate);
        } catch (ParseException e) {
            return e.getMessage();
        }
    }

    /**
     * Queries agency for AVL data and corresponding Match and Trip data. By joining in Match and
     * Trip data can see what the block and trip IDs, the routeShortName, and possibly other
     * information, for each AVL report. Returns result as a JSON string. Limited to returning
     * MAX_ROWS (50,000) data points.
     *
     * @param agencyId
     * @param vehicleId Which vehicle to get data for. Set to empty string to get data for all
     *     vehicles. If null then will get data by route.
     * @param routeId Which route to get data for. Set to empty string to get data for all routes.
     *     If null then will get data by vehicle.
     * @param beginDate date to start query
     * @param numdays of days to collect data for
     * @param beginTime optional time of day during the date range
     * @param endTime optional time of day during the date range
     * @return AVL reports in JSON format. Can be empty JSON array if no data meets criteria.
     */
    public static String getAvlWithMatchesJson(
            String agencyId,
            String vehicleId,
            String routeId,
            String beginDate,
            String numdays,
            String beginTime,
            String endTime) {
        // Determine the time portion of the SQL
        // If beginTime or endTime set but not both then use default values
        if ((beginTime != null && !beginTime.isEmpty()) || (endTime != null && !endTime.isEmpty())) {
            if (beginTime == null || beginTime.isEmpty()) {
                beginTime = "00:00";
            }
            if (endTime == null || endTime.isEmpty()) {
                endTime = "24:00";
            }
        }

        String timeSql = "";
        if (beginTime != null && !beginTime.isEmpty()) {
            timeSql = " AND time::time BETWEEN '" + beginTime + "' AND '" + endTime + "' ";
        }

        String sql = "SELECT a.vehicle_id, a.time, a.assignment_id, a.lat, a.lon, "
                + "     a.speed, a.heading, a.time_processed, "
                + "     vs.block_id, vs.trip_id, vs.trip_short_name, vs.route_id, "
                + "     vs.route_short_name, vs.schedule_adherence_msec, vs.schedule_adherence, "
                + "     vs.is_delayed, vs.is_layover, vs.is_wait_stop  "
                + "FROM avl_reports a "
                + "  LEFT JOIN vehicle_states vs "
                + "    ON vs.vehicle_id = a.vehicle_id AND vs.avl_time = a.time "
                + "WHERE a.time BETWEEN '"
                + beginDate
                + "' "
                + "     AND TIMESTAMP '"
                + beginDate
                + "' + INTERVAL '"
                + numdays
                + " day' "
                + timeSql;

        // If only want data for single route then specify so in SQL.
        // Since some agencies like sfmta don't have consistent route IDs
        // across schedule changes need to try to match to GTFS route_id or
        // route_short_name.
        if (vehicleId == null && routeId != null && !routeId.trim().isEmpty()) {
            sql += "AND (vs.route_id='" + routeId + "' OR vs.route_short_name='" + routeId + "') ";
        }

        // If only want data for single vehicle then specify so in SQL
        if (vehicleId != null && !vehicleId.trim().isEmpty()) {
            sql += "AND a.vehicle_id='" + vehicleId + "' ";
        }

        // Make sure data is ordered by vehicleId so that can draw lines
        // connecting the AVL reports per vehicle properly. Also then need
        // to order by time to make sure they are in proper order. And
        // lastly, limit AVL reports to 5000 so that someone doesn't try
        // to view too much data at once.
        sql += "ORDER BY a.vehicle_id, time LIMIT " + MAX_ROWS;

        return GenericJsonQuery.getJsonString(agencyId, sql);
    }
}
