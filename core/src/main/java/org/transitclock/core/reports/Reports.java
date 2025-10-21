/* (C)2023 */
package org.transitclock.core.reports;

import org.json.JSONArray;
import org.json.JSONObject;
import org.transitclock.domain.webstructs.WebAgency;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.transitclock.config.data.TraccarConfig.mphInsteadOfKmh;
import static org.transitclock.core.reports.ScheduleAdhStopsReport.*;
import static org.transitclock.utils.Time.DAY_IN_MSECS;
import static org.transitclock.utils.Time.YEAR_IN_MSECS;

public class Reports {

    private static final int MAX_ROWS = 200000;

    private static final int MAX_NUM_DAYS = 7;

    /**
     * Queries agency for AVL data and returns result as a JSON string. Limited to returning
     * MAX_ROWS (50,000) data points.
     *
     * @param agencyId
     * @param vehicleId Which vehicle to get data for. Set to null or empty string to get data for
     *                  all vehicles
     * @param beginDate date to start query
     * @param numdays   of days to collect data for
     * @param beginTime optional time of day during the date range
     * @param endTime   optional time of day during the date range
     * @return AVL reports in JSON format. Can be empty JSON array if no data meets criteria.
     */
    public static String getAvlJson(
            String agencyId, String vehicleId, String beginDate, String numdays, String beginTime, String endTime) {
        // Determine the time portion of the SQL
        String timeSql = "";
        WebAgency agency = WebAgency.getCachedWebAgency(agencyId);
        // If beginTime or endTime set but not both then use default values
        if ((beginTime != null && !beginTime.isEmpty()) || (endTime != null && !endTime.isEmpty())) {
            if (beginTime == null || beginTime.isEmpty()) beginTime = "00:00";
            if (endTime == null || endTime.isEmpty()) endTime = "24:00";
        }
        // cast('2000-01-01 01:12:00'::timestamp as time);
        if (beginTime != null && !beginTime.isEmpty() && endTime != null && !endTime.isEmpty()) {
            if ("mysql".equals(agency.getDbType())) {
                timeSql = " AND time(time) BETWEEN '" + beginTime + "' AND '" + endTime + "' ";
            } else {
                timeSql = " AND cast(time::timestamp as time) BETWEEN '" + beginTime + "' AND '" + endTime + "' ";
            }
        }

        StringBuilder sql;
        if ("mysql".equals(agency.getDbType())) {
            sql = new StringBuilder("SELECT vehicle_id, name, time, assignment_id, lat, lon, speed, heading,");
            sql.append(" time_processed, source FROM avl_reports INNER JOIN vehicle_configs ON");
            sql.append(" vehicle_configs.id = avl_reports.vehicle_id WHERE time BETWEEN  cast(? as");
            sql.append(" datetime) AND date_add(cast(? as datetime), INTERVAL ");
            sql.append(numdays);
            sql.append(" day) ");
            sql.append(timeSql);
        } else {
            sql = new StringBuilder("SELECT vehicle_id, name, time, assignment_id, lat, lon, speed, heading,");
            sql.append(" time_processed, source FROM avl_reports INNER JOIN vehicle_configs ON");
            sql.append(" vehicle_configs.id = avl_reports.vehicle_id WHERE time BETWEEN  cast(? as");
            sql.append(" timestamp) AND cast(? as timestamp) + INTERVAL '");
            sql.append(numdays);
            sql.append(" day' ");
            sql.append(timeSql);
        }

        // If only want data for single vehicle then specify so in SQL
        if (vehicleId != null && !vehicleId.isEmpty()) sql.append(" AND vehicle_id = '" + vehicleId + "' ");

        // Make sure data is ordered by vehicleId so that can draw lines
        // connecting the AVL reports per vehicle properly. Also then need
        // to order by time to make sure they are in proper order. And
        // lastly, limit AVL reports to 5000 so that someone doesn't try
        // to view too much data at once.
        sql.append("ORDER BY vehicle_id, time LIMIT ").append(MAX_ROWS);

        String json;
        Date startdate = null;
        try {
            startdate = getValidateDate(beginDate);
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        json = GenericJsonQuery.getJsonString(agencyId, sql.toString(), startdate, startdate);

        return json;
    }

    public static String getSingleAvlReportsForEachVehicleId(String agencyId, String date, String beginTime, String endTime) {

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT vehicle_id, name, time, heading, assignment_id, lat, lon, speed, time_processed, source \n");
        sqlBuilder.append("FROM ( SELECT  avl.vehicle_id,\n");
        sqlBuilder.append("               config.name,\n");
        sqlBuilder.append("               avl.time,\n");
        sqlBuilder.append("               avl.assignment_id,\n");
        sqlBuilder.append("               avl.lat,\n");
        sqlBuilder.append("               avl.lon,\n");
        sqlBuilder.append("               avl.speed,\n");
        sqlBuilder.append("               avl.heading,\n");
        sqlBuilder.append("               avl.time_processed,\n");
        sqlBuilder.append("               avl.source,\n");
        sqlBuilder.append("               ROW_NUMBER() OVER (PARTITION BY avl.vehicle_id ORDER BY avl.time DESC) as row\n");
        sqlBuilder.append("         FROM avl_reports avl\n");
        sqlBuilder.append("                  INNER JOIN vehicle_configs config ON\n");
        sqlBuilder.append("             config.id = avl.vehicle_id\n");
        sqlBuilder.append("         WHERE ");
        sqlBuilder.append(SqlUtils.timeRangeClause("avl.time", date, beginTime, endTime));
        sqlBuilder.append("     ) t\n");
        sqlBuilder.append("WHERE t.row = 1\n");
        sqlBuilder.append("ORDER BY t.time;\n");

        return GenericJsonQuery.getJsonString(agencyId, sqlBuilder.toString());
    }

    public static String getTripsFromArrivalAndDeparturesByDate(String agencyId, String date) {
        // postgresql only, should throw error if it's other database type
        String sql = "SELECT "
                + "	arrivals_departures.trip_id as tripId "
                + "FROM arrivals_departures "
                + "WHERE Date(arrivals_departures.time) = DATE('" + date + "') "
                + "GROUP BY arrivals_departures.trip_id";

        String json = null;
        json = GenericJsonQuery.getJsonString(agencyId, sql);

        return json;
    }

    public static String getTripWithTravelTimes(String agencyId, String tripId, String date) {
        // postgresql only, should throw error if it's other database type
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("WITH include_prev_passenger AS (SELECT arrivals_departures.trip_id             AS tripId,\n");
        sqlBuilder.append("       arrivals_departures.direction_id        AS directionId,\n");
        sqlBuilder.append("       arrivals_departures.stop_id             AS stopId,\n");
        sqlBuilder.append("       stops.code                              AS stopCode,\n");
        sqlBuilder.append("       stops.name                              AS stopName,\n");
        sqlBuilder.append("       stops.lat                               AS lat,\n");
        sqlBuilder.append("       stops.lon                               AS lon,\n");
        sqlBuilder.append("       arrivals_departures.stop_order          AS stopOrder,\n");
        sqlBuilder.append("       arrivals_departures.vehicle_id          AS vehicleId,\n");
        sqlBuilder.append("       vehicle_configs.name                    AS vehicleName,\n");
        sqlBuilder.append("       arrivals_departures.time                AS arrivalTime,\n");
        sqlBuilder.append("       ADDeparture.time                        AS departureTime,\n");
        sqlBuilder.append("       CASE\n");
        sqlBuilder.append("           WHEN avl_reports.passenger_count = -1 THEN NULL\n");
        sqlBuilder.append("           ELSE avl_reports.passenger_count END AS passenger_count,");
        sqlBuilder.append("  LAG( CASE WHEN avl_reports.passenger_count = -1 THEN NULL ELSE avl_reports.passenger_count END) \n");
        sqlBuilder.append("  OVER ( PARTITION BY arrivals_departures.vehicle_id ORDER BY arrivals_departures.time) AS prevPassengerCount,\n");
        sqlBuilder.append("	 CASE WHEN ADDeparture.scheduled_time ISNULL");
        sqlBuilder.append("	 THEN DATE('");
        sqlBuilder.append(date);
        sqlBuilder.append("') + trip_scheduled_times_list.arrival_time * interval '1 second'\n");
        sqlBuilder.append("         ELSE ADDeparture.scheduled_time END AS scheduledTime,\n");
        sqlBuilder.append("    CASE\n");
        sqlBuilder.append("         WHEN ADDeparture.scheduled_time ISNULL THEN regexp_replace(CAST(DATE_TRUNC('second', DATE('");
        sqlBuilder.append(date);
        sqlBuilder.append("') + \n");
        sqlBuilder.append("                                                         trip_scheduled_times_list.arrival_time *\n");
        sqlBuilder.append("                                                         interval '1 second') -\n");
        sqlBuilder.append("                                    DATE_TRUNC('second', arrivals_departures.time::timestamp) AS VARCHAR),\n");
        sqlBuilder.append("                                                                    '^00:', '')\n");
        sqlBuilder.append("         ELSE regexp_replace(CAST(DATE_TRUNC('second', ADDeparture.scheduled_time::timestamp) -\n");
        sqlBuilder.append("                                    DATE_TRUNC('second', ADDeparture.time::timestamp) AS VARCHAR), '^00:',\n");
        sqlBuilder.append("                               '') END         AS difference_in_seconds\n");
        sqlBuilder.append("FROM arrivals_departures\n");
        sqlBuilder.append("         LEFT JOIN stops ON stops.id = arrivals_departures.stop_id AND stops.config_rev = arrivals_departures.config_rev\n");
        sqlBuilder.append("         LEFT JOIN vehicle_configs ON vehicle_configs.id = arrivals_departures.vehicle_id\n");
        sqlBuilder.append("         LEFT JOIN avl_reports ON arrivals_departures.avl_time = avl_reports.time AND arrivals_departures.vehicle_id = avl_reports.vehicle_id\n");
        sqlBuilder.append("         LEFT JOIN LATERAL (SELECT *\n");
        sqlBuilder.append("                            FROM arrivals_departures ad\n");
        sqlBuilder.append("                            WHERE ad.trip_id = arrivals_departures.trip_id\n");
        sqlBuilder.append("                              AND ad.direction_id = arrivals_departures.direction_id\n");
        sqlBuilder.append("                              AND ad.stop_id = arrivals_departures.stop_id\n");
        sqlBuilder.append("                              AND LOWER(ad.type) = LOWER('DEPARTURE')\n");
        sqlBuilder.append("                              AND DATE(ad.avl_time) = DATE(arrivals_departures.avl_time)\n");
        sqlBuilder.append("                              AND ad.time >= arrivals_departures.time\n");
        sqlBuilder.append("                            ORDER BY ad.time ASC\n");
        sqlBuilder.append("                            LIMIT 1) ADDeparture ON True\n");
        sqlBuilder.append("         LEFT JOIN trip_scheduled_times_list ON trip_scheduled_times_list.trip_trip_id = arrivals_departures.trip_id AND\n");
        sqlBuilder.append("                                                trip_scheduled_times_list.trip_config_rev =\n");
        sqlBuilder.append("                                                arrivals_departures.config_rev AND\n");
        sqlBuilder.append("                                                trip_scheduled_times_list.list_index = arrivals_departures.stop_order\n");
        sqlBuilder.append("WHERE arrivals_departures.trip_id = '");
        sqlBuilder.append(tripId);
        sqlBuilder.append("' AND arrivals_departures.is_arrival = 'True'\n");
        sqlBuilder.append("  AND Date(arrivals_departures.time) = DATE('");
        sqlBuilder.append(date);
        sqlBuilder.append("')\n");
        sqlBuilder.append("ORDER BY arrivals_departures.time ASC, arrivals_departures.direction_id ASC, arrivals_departures.gtfs_stop_seq ASC)");
        sqlBuilder.append("SELECT pp.tripId, pp.directionId, pp.stopId, pp.stopCode, pp.stopName, pp.lat, pp.lon, pp.stopOrder, pp.vehicleId, pp.vehicleName, pp.arrivalTime, pp.departureTime, pp.scheduledTime, pp.difference_in_seconds, pp.passenger_count,\n");
        sqlBuilder.append("       CASE\n");
        sqlBuilder.append("           WHEN passenger_count > prevPassengerCount THEN passenger_count - prevPassengerCount ELSE passenger_count - prevPassengerCount END AS passengerIncrease\n");
        sqlBuilder.append("FROM include_prev_passenger pp\n");
        sqlBuilder.append("ORDER BY stopOrder");

        return GenericJsonQuery.getJsonString(agencyId, sqlBuilder.toString(), date, date, tripId, date);
    }

    public static String getTripsWithTravelTimes(String agencyId, String date) {
        // postgresql only, should throw error if it's other database type
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("WITH include_prev_passenger AS (SELECT arrivals_departures.trip_id             AS tripId,\n");
        sqlBuilder.append("       arrivals_departures.direction_id        AS directionId,\n");
        sqlBuilder.append("       arrivals_departures.stop_id             AS stopId,\n");
        sqlBuilder.append("       stops.code                              AS stopCode,\n");
        sqlBuilder.append("       stops.name                              AS stopName,\n");
        sqlBuilder.append("       stops.lat                               AS lat,\n");
        sqlBuilder.append("       stops.lon                               AS lon,\n");
        sqlBuilder.append("       arrivals_departures.stop_order          AS stopOrder,\n");
        sqlBuilder.append("       arrivals_departures.vehicle_id          AS vehicleId,\n");
        sqlBuilder.append("       vehicle_configs.name                    AS vehicleName,\n");
        sqlBuilder.append("       arrivals_departures.time                AS arrivalTime,\n");
        sqlBuilder.append("       ADDeparture.time                        AS departureTime,\n");
        sqlBuilder.append("       CASE\n");
        sqlBuilder.append("           WHEN avl_reports.passenger_count = -1 THEN NULL\n");
        sqlBuilder.append("           ELSE avl_reports.passenger_count END AS passenger_count,");
        sqlBuilder.append("	 CASE WHEN ADDeparture.scheduled_time ISNULL");
        sqlBuilder.append("	 THEN DATE('");
        sqlBuilder.append(date);
        sqlBuilder.append("') + trip_scheduled_times_list.arrival_time * interval '1 second'\n");
        sqlBuilder.append("         ELSE ADDeparture.scheduled_time END AS scheduledTime,\n");
        sqlBuilder.append("    LAG( CASE WHEN avl_reports.passenger_count = -1 THEN NULL ELSE avl_reports.passenger_count END) \n");
        sqlBuilder.append("    OVER ( PARTITION BY arrivals_departures.vehicle_id ORDER BY arrivals_departures.time) AS prevPassengerCount,\n");
        sqlBuilder.append("    CASE\n");
        sqlBuilder.append("         WHEN ADDeparture.scheduled_time ISNULL THEN regexp_replace(CAST(DATE_TRUNC('second', DATE('");
        sqlBuilder.append(date);
        sqlBuilder.append("') + \n");
        sqlBuilder.append("                                                         trip_scheduled_times_list.arrival_time *\n");
        sqlBuilder.append("                                                         interval '1 second') -\n");
        sqlBuilder.append("                                    DATE_TRUNC('second', arrivals_departures.time::timestamp) AS VARCHAR),\n");
        sqlBuilder.append("                                                                    '^00:', '')\n");
        sqlBuilder.append("         ELSE regexp_replace(CAST(DATE_TRUNC('second', ADDeparture.scheduled_time::timestamp) -\n");
        sqlBuilder.append("                                    DATE_TRUNC('second', ADDeparture.time::timestamp) AS VARCHAR), '^00:',\n");
        sqlBuilder.append("                               '') END         AS difference_in_seconds\n");
        sqlBuilder.append("FROM arrivals_departures\n");
        sqlBuilder.append("         LEFT JOIN stops ON stops.id = arrivals_departures.stop_id AND stops.config_rev = arrivals_departures.config_rev\n");
        sqlBuilder.append("         LEFT JOIN vehicle_configs ON vehicle_configs.id = arrivals_departures.vehicle_id\n");
        sqlBuilder.append("         LEFT JOIN avl_reports ON arrivals_departures.avl_time = avl_reports.time AND arrivals_departures.vehicle_id = avl_reports.vehicle_id\n");
        sqlBuilder.append("         LEFT JOIN LATERAL (SELECT *\n");
        sqlBuilder.append("                            FROM arrivals_departures ad\n");
        sqlBuilder.append("                            WHERE ad.trip_id = arrivals_departures.trip_id\n");
        sqlBuilder.append("                              AND ad.direction_id = arrivals_departures.direction_id\n");
        sqlBuilder.append("                              AND ad.stop_id = arrivals_departures.stop_id\n");
        sqlBuilder.append("                              AND LOWER(ad.type) = LOWER('DEPARTURE')\n");
        sqlBuilder.append("                              AND DATE(ad.avl_time) = DATE(arrivals_departures.avl_time)\n");
        sqlBuilder.append("                              AND ad.time >= arrivals_departures.time\n");
        sqlBuilder.append("                            ORDER BY ad.time ASC\n");
        sqlBuilder.append("                            LIMIT 1) ADDeparture ON True\n");
        sqlBuilder.append("         LEFT JOIN trip_scheduled_times_list ON trip_scheduled_times_list.trip_trip_id = arrivals_departures.trip_id AND\n");
        sqlBuilder.append("                                                trip_scheduled_times_list.trip_config_rev =\n");
        sqlBuilder.append("                                                arrivals_departures.config_rev AND\n");
        sqlBuilder.append("                                                trip_scheduled_times_list.list_index = arrivals_departures.stop_order\n");
        sqlBuilder.append("WHERE arrivals_departures.is_arrival = 'True'\n");
        sqlBuilder.append("  AND Date(arrivals_departures.time) = DATE('");
        sqlBuilder.append(date);
        sqlBuilder.append("')\n");
        sqlBuilder.append("ORDER BY arrivals_departures.time ASC, arrivals_departures.direction_id ASC, arrivals_departures.gtfs_stop_seq ASC)");
        sqlBuilder.append("SELECT pp.tripId, pp.directionId, pp.stopId, pp.stopCode, pp.stopName, pp.lat, pp.lon, pp.stopOrder, pp.vehicleId, pp.vehicleName, pp.arrivalTime, pp.departureTime, pp.scheduledTime, pp.difference_in_seconds, pp.passenger_count,\n");
        sqlBuilder.append("       CASE\n");
        sqlBuilder.append("           WHEN passenger_count > prevPassengerCount THEN passenger_count - prevPassengerCount ELSE passenger_count - prevPassengerCount END AS passengerIncrease\n");
        sqlBuilder.append("FROM include_prev_passenger pp\n");
        sqlBuilder.append("ORDER BY stopOrder");

        return GenericJsonQuery.getJsonString(agencyId, sqlBuilder.toString(), date, date, date);
    }

    public static String getMaxIncreasePaxPerRoute(String agencyId, String routId, String routeShortName, String date) {
        Date beginDate = getValidateDate(date);

        StringBuilder sqlBuilder = new StringBuilder("WITH passenger_deltas AS (\n");
        sqlBuilder.append("    SELECT ad.trip_id,\n");
        sqlBuilder.append("           t.route_id,\n");
        sqlBuilder.append("           t.route_short_name   AS route_Name,\n");
        sqlBuilder.append("           t.headsign           AS headsign,\n");
        sqlBuilder.append("           vehicle_configs.name AS vehicle_Name,\n");
        sqlBuilder.append("           ad.vehicle_id,\n");
        sqlBuilder.append("           ad.block_id,\n");
        sqlBuilder.append("           TO_CHAR((t.start_time || 'second')::interval, 'HH24:MI:SS') AS start_Time,\n");
        sqlBuilder.append("           TO_CHAR((t.end_time || 'second')::interval, 'HH24:MI:SS')   AS end_Time,\n");
        sqlBuilder.append("           CASE\n");
        sqlBuilder.append("               WHEN ar.passenger_count = -1 THEN NULL\n");
        sqlBuilder.append("               ELSE ar.passenger_count\n");
        sqlBuilder.append("               END AS passenger_Count,\n");
        sqlBuilder.append("           LAG(\n");
        sqlBuilder.append("           CASE WHEN ar.passenger_count = -1 THEN NULL ELSE ar.passenger_count END\n");
        sqlBuilder.append("              ) OVER (PARTITION BY ad.trip_id ORDER BY ad.time) AS prev_passenger_Count\n");
        sqlBuilder.append("    FROM arrivals_departures ad\n");
        sqlBuilder.append("             LEFT JOIN trips t ON t.trip_id = ad.trip_id AND t.config_rev = ad.config_rev\n");
        sqlBuilder.append("             LEFT JOIN vehicle_configs ON vehicle_configs.id = ad.vehicle_id\n");
        sqlBuilder.append("             LEFT JOIN avl_reports ar\n");
        sqlBuilder.append("                       ON ad.avl_time = ar.time AND ad.vehicle_id = ar.vehicle_id\n");
        sqlBuilder.append("    WHERE ");
        if (routId != null && !routId.isEmpty()) {
            sqlBuilder.append("ad.route_id = '");
            sqlBuilder.append(routId);
            sqlBuilder.append("' AND ");
        } else if (routeShortName != null && !routeShortName.isEmpty()) {
            sqlBuilder.append("ad.route_short_name = '");
            sqlBuilder.append(routeShortName);
            sqlBuilder.append("' AND ");
        }
        sqlBuilder.append("DATE(ad.time) = DATE('");
        sqlBuilder.append(beginDate);
        sqlBuilder.append("')\n");
        sqlBuilder.append("         AND ad.is_arrival = true),\n");
        sqlBuilder.append("summed_increase AS (\n");
        sqlBuilder.append("    SELECT trip_id, route_id, route_Name, headsign, vehicle_Name, vehicle_id, block_id, start_Time, end_Time,\n");
        sqlBuilder.append("           SUM(\n");
        sqlBuilder.append("                   CASE\n");
        sqlBuilder.append("                       WHEN prev_passenger_Count IS NULL AND passenger_Count IS NOT NULL\n");
        sqlBuilder.append("                           THEN passenger_Count\n");
        sqlBuilder.append("                       WHEN passenger_Count IS NOT NULL\n");
        sqlBuilder.append("                           AND prev_passenger_Count IS NOT NULL\n");
        sqlBuilder.append("                           AND passenger_Count > prev_passenger_Count\n");
        sqlBuilder.append("                           THEN passenger_Count - prev_passenger_Count\n");
        sqlBuilder.append("                       END\n");
        sqlBuilder.append("           ) AS max_passenger_count\n");
        sqlBuilder.append("    FROM passenger_deltas\n");
        sqlBuilder.append("    GROUP BY trip_id, route_id, route_Name, headsign,\n");
        sqlBuilder.append("             vehicle_Name, vehicle_id, block_id, start_Time, end_Time )\n");
        sqlBuilder.append("SELECT *\n");
        sqlBuilder.append("FROM summed_increase\n");
        sqlBuilder.append("WHERE max_passenger_count IS NOT NULL\n");
        sqlBuilder.append("ORDER BY start_Time;");

        return GenericJsonQuery.getJsonString(agencyId, sqlBuilder.toString());
    }

    public static String getOccupancyPerTripWithContinuePickUp(String agencyId, String tripId, String date) {
        Date beginDate = getValidateDate(date);

        StringBuilder sqlBuilder = new StringBuilder("WITH enriched_arrivals AS (");
        sqlBuilder.append("    SELECT\n");
        sqlBuilder.append("        ad.trip_id AS tripId,\n");
        sqlBuilder.append("        tr.route_short_name AS routeId,\n");
        sqlBuilder.append("        tr.headsign AS headsing,\n");
        sqlBuilder.append("        ad.stop_id AS stopId,\n");
        sqlBuilder.append("        ar.lat AS lat,\n");
        sqlBuilder.append("        ar.lon AS lon,\n");
        sqlBuilder.append("        ad.stop_order AS stopOrder,\n");
        sqlBuilder.append("        ad.vehicle_id AS vehicleId,\n");
        sqlBuilder.append("        ar.vehicle_name AS vehicleName,\n");
        sqlBuilder.append("        ad.time AS arrivalTime,\n");
        sqlBuilder.append("        CASE WHEN ar.passenger_count = -1 THEN NULL ELSE ar.passenger_count END AS passengerCount,\n");
        sqlBuilder.append("        LAG(CASE WHEN ar.passenger_count = -1 THEN NULL ELSE ar.passenger_count END)\n");
        sqlBuilder.append("        OVER (PARTITION BY ad.vehicle_id ORDER BY ad.time) AS prevPassengerCount\n");
        sqlBuilder.append("    FROM arrivals_departures ad\n");
        sqlBuilder.append("             LEFT JOIN avl_reports ar\n");
        sqlBuilder.append("                       ON ad.avl_time = ar.time AND ad.vehicle_id = ar.vehicle_id\n");
        sqlBuilder.append("             LEFT JOIN trips tr\n");
        sqlBuilder.append("                       ON tr.trip_id = ad.trip_id AND tr.config_rev = ad.config_rev\n");
        sqlBuilder.append("    WHERE ad.is_arrival = TRUE\n");
        sqlBuilder.append("      AND ad.trip_id = '");
        sqlBuilder.append(tripId);
        sqlBuilder.append("'\n");
        sqlBuilder.append("      AND DATE(ad.time) = DATE('");
        sqlBuilder.append(beginDate);
        sqlBuilder.append("')),\n");
        sqlBuilder.append("     passenger_increases AS (\n");
        sqlBuilder.append("         SELECT\n");
        sqlBuilder.append("             vehicle_id AS vehicleId,\n");
        sqlBuilder.append("             vehicle_name AS vehicleName,\n");
        sqlBuilder.append("             time AS avlTime,\n");
        sqlBuilder.append("             lon,\n");
        sqlBuilder.append("             lat,\n");
        sqlBuilder.append("             CASE WHEN passenger_count = -1 THEN NULL ELSE passenger_count END AS passengerCount,\n");
        sqlBuilder.append("             LAG(CASE WHEN passenger_count = -1 THEN NULL ELSE passenger_count END)\n");
        sqlBuilder.append("             OVER (PARTITION BY vehicle_id ORDER BY time) AS prevPassengerCount\n");
        sqlBuilder.append("         FROM avl_reports),\n");
        sqlBuilder.append("\n");
        sqlBuilder.append("     filtered_increases AS (\n");
        sqlBuilder.append("         SELECT *, (passengerCount - prevPassengerCount) AS pi_passengerIncrease\n");
        sqlBuilder.append("         FROM passenger_increases\n");
        sqlBuilder.append("         WHERE passengerCount IS NOT NULL AND prevPassengerCount IS NOT NULL AND passengerCount > prevPassengerCount),\n");
        sqlBuilder.append("     time_bounds AS (\n");
        sqlBuilder.append("         SELECT MIN(arrivalTime) AS min_time, MAX(arrivalTime) AS max_time, vehicleId\n");
        sqlBuilder.append("         FROM enriched_arrivals\n");
        sqlBuilder.append("         GROUP BY vehicleId),\n");
        sqlBuilder.append("\n");
        sqlBuilder.append("     filtered_increases_within_bounds AS (\n");
        sqlBuilder.append("         SELECT pi.*\n");
        sqlBuilder.append("         FROM filtered_increases pi\n");
        sqlBuilder.append("                  JOIN time_bounds tb\n");
        sqlBuilder.append("                       ON pi.vehicleId = tb.vehicleId\n");
        sqlBuilder.append("                           AND pi.avlTime BETWEEN tb.min_time AND tb.max_time\n");
        sqlBuilder.append("         WHERE NOT EXISTS (\n");
        sqlBuilder.append("             SELECT 1\n");
        sqlBuilder.append("             FROM enriched_arrivals ea\n");
        sqlBuilder.append("             WHERE ea.vehicleId = pi.vehicleId AND ea.arrivalTime = pi.avlTime AND ea.lat = pi.lat AND ea.lon = pi.lon))\n");
        sqlBuilder.append("SELECT ea.tripId, ea.routeId, ea.headsing, ea.stopId, ea.lat, ea.lon, ea.stopOrder, ea.vehicleId, ea.vehicleName, ea.passengerCount, ea.prevPassengerCount, (ea.passengerCount - ea.prevPassengerCount) AS passengerIncrease, NULL AS pi_passengerCount, NULL AS pi_prevPassengerCount, NULL AS pi_passengerIncrease, ea.arrivalTime AS time\n");
        sqlBuilder.append("FROM enriched_arrivals ea\n");
        sqlBuilder.append("\n");
        sqlBuilder.append("UNION ALL\n");
        sqlBuilder.append("\n");
        sqlBuilder.append("SELECT NULL AS tripId, NULL AS routeId, NULL AS headsing, NULL AS stopId, pi.lat, pi.lon, NULL AS stopOrder,pi.vehicleId,pi.vehicleName, NULL AS passengerCount, NULL AS prevPassengerCount, NULL AS passengerIncrease, pi.passengerCount AS between_passengerCount, pi.prevPassengerCount AS between_prevPassengerCount, pi.pi_passengerIncrease AS between_passengerIncrease, pi.avlTime AS time\n");
        sqlBuilder.append("FROM filtered_increases_within_bounds pi\n");
        sqlBuilder.append("ORDER BY time;\n");

        return GenericJsonQuery.getJsonString(agencyId, sqlBuilder.toString());
    }

    public static String getAvgSpeedPerRoute(String agencyId,
                                             String beginDate,
                                             String endDate,
                                             String routeShortName,
                                             String routeId,
                                             String directionId,
                                             String beginTime,
                                             String endTime
    ) throws SQLException {
        boolean isMPH = mphInsteadOfKmh.getValue();

        String MPS_MPH = "2.23694";
        String MPS_KMH = "3.6";

        long numDays = ChronoUnit.DAYS.between(
                validateParseToLocalDate(beginDate),
                validateParseToLocalDate(endDate)) +1;

        if (numDays > 31) throw new IllegalArgumentException(String.format("%s - %s: more then 31!", beginDate, endDate));

        StringBuilder sqlBuilder = new StringBuilder("WITH ordered AS (SELECT\n");
        sqlBuilder.append("a.config_rev, a.vehicle_id, a.trip_id, a.route_id, a.route_short_name, a.direction_id, a.gtfs_stop_seq, a.stop_id, a.stop_path_length,\n");
        sqlBuilder.append("a.time AS stop_time,\n");
        sqlBuilder.append("LEAD(a.time) OVER ( PARTITION BY a.config_rev, a.vehicle_id, a.trip_id ORDER BY a.gtfs_stop_seq ) AS next_stop_time,\n");
        sqlBuilder.append("LEAD(a.stop_id) OVER ( PARTITION BY a.config_rev, a.vehicle_id, a.trip_id ORDER BY a.gtfs_stop_seq ) AS next_stop_id,\n");
        sqlBuilder.append("LEAD(a.stop_path_length) OVER ( PARTITION BY a.config_rev, a.vehicle_id, a.trip_id ORDER BY a.gtfs_stop_seq ) AS next_stop_path_length");
        sqlBuilder.append("    FROM arrivals_departures a\n");
        sqlBuilder.append("    WHERE a.scheduled_time IS NOT NULL");
        if (routeId != null && !routeId.isEmpty()) {
            sqlBuilder.append("      AND a.route_id = '").append(routeId).append("'\n");
        } else if (routeShortName != null && !routeShortName.isEmpty()) {
            sqlBuilder.append("      AND a.route_short_name = '").append(routeShortName).append("'\n");
        } else throw new SQLException("Route id or route short name not specified");
        sqlBuilder.append("      AND a.direction_id = '").append(!directionId.isEmpty() ? directionId : "0").append("'\n");
        sqlBuilder.append(SqlUtils.timeRangeClause("a.time", 31,(int) numDays, beginTime, endTime, beginDate));
        sqlBuilder.append("),\n");
        sqlBuilder.append("travel_times AS (SELECT \n");
        sqlBuilder.append("             config_rev, route_short_name, direction_id, trip_id, gtfs_stop_seq,\n");
        sqlBuilder.append("             stop_id                    AS from_stop_id,\n");
        sqlBuilder.append("             next_stop_id               AS to_stop_id,\n");
        sqlBuilder.append("             stop_path_length           AS from_stop_path_length,\n");
        sqlBuilder.append("             next_stop_path_length      AS to_stop_path_length,\n");
        sqlBuilder.append("             next_stop_path_length      AS segment_length,\n");
        sqlBuilder.append("             EXTRACT(EPOCH FROM (next_stop_time - stop_time)) AS travel_time_sec\n");
        sqlBuilder.append("         FROM ordered\n");
        sqlBuilder.append("         WHERE next_stop_time IS NOT NULL\n");
        sqlBuilder.append("           AND next_stop_id IS NOT NULL\n");
        sqlBuilder.append("           AND next_stop_id <> stop_id\n");
        sqlBuilder.append("           AND next_stop_time > stop_time\n");
        sqlBuilder.append("           AND EXTRACT(EPOCH FROM (next_stop_time - stop_time)) > 20\n");
        sqlBuilder.append("           AND next_stop_path_length IS NOT NULL\n");
        sqlBuilder.append("           AND next_stop_path_length > 0 ),\n");
        sqlBuilder.append("avg_speed AS (SELECT \n");
        sqlBuilder.append("   (t.from_stop_id || '_to_' || t.to_stop_id)                                 AS segment_id,\n");
        sqlBuilder.append("    MAX(t.config_rev)                                                         AS config_rev,\n");
        sqlBuilder.append("    MIN(t.gtfs_stop_seq)                                                      AS stop_order,\n");
        sqlBuilder.append("    t.from_stop_id,\n");
        sqlBuilder.append("    t.to_stop_id,\n");
        sqlBuilder.append("    fs.name                                                                   AS from_stop_name,\n");
        sqlBuilder.append("    ts.name                                                                   AS to_stop_name,\n");
        sqlBuilder.append("    t.route_short_name,\n");
        sqlBuilder.append("    t.direction_id,\n");
        sqlBuilder.append("    ROUND(AVG(t.segment_length)::numeric, 3)                                  AS segment_length_in_meters,");
        sqlBuilder.append("    COUNT(*) AS num_trips,\n");
        sqlBuilder.append("    ROUND(AVG(t.segment_length / NULLIF(t.travel_time_sec,0) * ").append(isMPH ? MPS_MPH : MPS_KMH).append(")::numeric, 2)::text AS avg_speed,\n");
        sqlBuilder.append("    ROUND(MIN(t.segment_length / NULLIF(t.travel_time_sec,0) * ").append(isMPH ? MPS_MPH : MPS_KMH).append(")::numeric, 2)::text AS min_speed,\n");
        sqlBuilder.append("    ROUND(MAX(t.segment_length / NULLIF(t.travel_time_sec,0) * ").append(isMPH ? MPS_MPH : MPS_KMH).append(")::numeric, 2)::text AS max_speed,\n");
        sqlBuilder.append("    ROUND(percentile_cont(0.10) WITHIN GROUP (ORDER BY (t.segment_length / NULLIF(t.travel_time_sec,0) * ")
                .append(isMPH ? MPS_MPH : MPS_KMH).append("))::numeric, 2)::text AS p10_speed,\n");
        sqlBuilder.append("    ROUND(percentile_cont(0.25) WITHIN GROUP (ORDER BY (t.segment_length / NULLIF(t.travel_time_sec,0) * ")
                .append(isMPH ? MPS_MPH : MPS_KMH).append("))::numeric, 2)::text AS p25_speed,\n");
        sqlBuilder.append("    ROUND(percentile_cont(0.50) WITHIN GROUP (ORDER BY (t.segment_length / NULLIF(t.travel_time_sec,0) * ")
                .append(isMPH ? MPS_MPH : MPS_KMH).append("))::numeric, 2)::text AS median_speed,\n");
        sqlBuilder.append("    ROUND(percentile_cont(0.75) WITHIN GROUP (ORDER BY (t.segment_length / NULLIF(t.travel_time_sec,0) * ")
                .append(isMPH ? MPS_MPH : MPS_KMH).append("))::numeric, 2)::text AS p75_speed,\n");
        sqlBuilder.append("    ROUND(percentile_cont(0.90) WITHIN GROUP (ORDER BY (t.segment_length / NULLIF(t.travel_time_sec,0) * ")
                .append(isMPH ? MPS_MPH : MPS_KMH).append("))::numeric, 2)::text AS p90_speed,");
        sqlBuilder.append("    '").append(isMPH ? "mph" : "kmh").append("' AS units\n");
        sqlBuilder.append("FROM travel_times t\n");
        sqlBuilder.append("         JOIN stops fs ON fs.config_rev = t.config_rev AND fs.id = t.from_stop_id\n");
        sqlBuilder.append("         JOIN stops ts ON ts.config_rev = t.config_rev AND ts.id = t.to_stop_id\n");
        sqlBuilder.append("GROUP BY\n");
        sqlBuilder.append("    t.config_rev, t.route_short_name, t.direction_id, t.from_stop_id, fs.name, t.to_stop_id, ts.name)\n");
        sqlBuilder.append("SELECT DISTINCT ON (route_short_name, direction_id, stop_order) *\n");
        sqlBuilder.append("FROM avg_speed\n");
        sqlBuilder.append("ORDER BY route_short_name, direction_id, stop_order, num_trips DESC;");

        return GenericJsonQuery.getJsonString(agencyId, sqlBuilder.toString());
    }

    /* Provides schedule adherence data in JSON format. Provides for
      the specified route the number arrivals/departures that
      are early, number late, number on time, and number total for each
      direction for each stop.
      Request parameters are:
        a - agency ID
        r - route ID or route short name.
        dateRange - in format "xx/xx/xx to yy/yy/yy"
        beginDate - date to begin query. For if dateRange not used.
        numDays - number of days can do query. Limited to 31 days. For if dateRange not used.
        beginTime - for optionally specifying time of day for query for each day
        endTime - for optionally specifying time of day for query for each day
        allowableEarlyMinutes - how early vehicle can be and still be OK.  Decimal format OK.
        allowableLateMinutes - how early vehicle can be and still be OK. Decimal format OK.
    */
    public static String getScheduleAdhByStops(
            String agencyId,
            String route,
            String beginDate,
            String allowableEarly,
            String allowableLate,
            String beginTime,
            String endTime,
            int numDays) {
        if (allowableEarly == null || allowableEarly.isEmpty()) allowableEarly = "1.0";
        String allowableEarlyMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableEarly) + " seconds'";

        if (allowableLate == null || allowableLate.isEmpty()) allowableLate = "4.0";
        String allowableLateMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableLate) + " seconds'";
        // To get stop name
        // Only need arrivals/departures that have a schedule time
        // Specifies which routes to provide data for
        // To get stop name
        // Only need arrivals/departures that have a schedule time
        // Specifies which routes to provide data for
        // Grouping and ordering is a bit complicated since might also be looking
        // at old arrival/departure data that doen't have stoporder defined. Also,
        // when configuration changes happen then the stop order can change.
        // Therefore want to group by directionId and stop name. Need to also
        // group by stop order so that can output it, which can be useful for
        // debugging, plus need to order by stop order. For the ORDER BY clause
        // need to order by direction id and stop order, but also the stop name
        // as a backup for if stoporder not defined for data and is therefore
        // always the same and doesn't provide any ordering info.
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("WITH trips_early_query_with_time AS ( SELECT trip_id AS trips_early, 	");
        sqlBuilder.append(" regexp_replace(CAST(DATE_TRUNC('second', ad.scheduled_time::timestamp) -");
        sqlBuilder.append(" DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR), '^00:', '')");
        sqlBuilder.append(" difference_in_seconds, \n");
        sqlBuilder.append("	 s.id AS stop_id, \n");
        sqlBuilder.append("	 ad.stop_order AS stop_order, \n");
        sqlBuilder.append("	 ad.vehicle_id AS v_id \n");
        sqlBuilder.append("FROM arrivals_departures ad, stops s \n");
        sqlBuilder.append("WHERE ");
        sqlBuilder.append(" ad.config_rev = s.config_rev \n");
        sqlBuilder.append(" AND ad.stop_id = s.id \n");
        sqlBuilder.append(" AND ad.scheduled_time IS NOT NULL \n");
        sqlBuilder.append(SqlUtils.routeClause(route, "ad"));
        sqlBuilder.append("\n");
        sqlBuilder.append(SqlUtils.timeRangeClause(
                "ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate));
        sqlBuilder.append("\n");
        sqlBuilder.append(" AND scheduled_time-time > ");
        sqlBuilder.append(allowableEarlyMinutesStr);
        sqlBuilder.append(" \n");
        sqlBuilder.append("	 ORDER BY direction_id, ad.stop_order, s.name ), \n");
        sqlBuilder.append("trips_late_query_with_time AS ( SELECT trip_id AS trips_late,  	");
        sqlBuilder.append(" regexp_replace(CAST(DATE_TRUNC('second', ad.time::timestamp) -");
        sqlBuilder.append(" DATE_TRUNC('second', ad.scheduled_time::timestamp) AS VARCHAR), '^00:',");
        sqlBuilder.append(" '') difference_in_seconds, \n");
        sqlBuilder.append("	 s.id AS stop_id, \n");
        sqlBuilder.append("	 ad.stop_order AS stop_order, \n");
        sqlBuilder.append("	 ad.vehicle_id AS v_id \n");
        sqlBuilder.append("FROM arrivals_departures ad, Stops s \n");
        sqlBuilder.append("WHERE ");
        sqlBuilder.append(" ad.config_rev = s.config_rev \n");
        sqlBuilder.append(" AND ad.stop_id = s.id \n");
        sqlBuilder.append(" AND ad.scheduled_time IS NOT NULL \n");
        sqlBuilder.append(SqlUtils.routeClause(route, "ad"));
        sqlBuilder.append("\n");
        sqlBuilder.append(SqlUtils.timeRangeClause(
                "ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate));
        sqlBuilder.append("\n");
        sqlBuilder.append(" AND time-scheduled_time > ");
        sqlBuilder.append(allowableLateMinutesStr);
        sqlBuilder.append(" \n");
        sqlBuilder.append("	 ORDER BY direction_id, ad.stop_order, s.name ), \n");
        sqlBuilder.append(" trips_late_query_v2 AS ( SELECT \n");
        sqlBuilder.append(" array_to_string(array_agg('trip: ' || trips_late::text || ' (' ||");
        sqlBuilder.append(" difference_in_seconds::text || '), vehicle: ' || v_id::text order by trips_late::text), '; ')");
        sqlBuilder.append(" AS trips_late,   \n");
        sqlBuilder.append("		 stop_id,  \n");
        sqlBuilder.append("		 stop_order  \n");
        sqlBuilder.append("FROM trips_late_query_with_time \n");
        sqlBuilder.append("		 GROUP BY stop_id, stop_order ), \n");
        sqlBuilder.append("	trips_early_query_v2 AS ( SELECT \n");
        sqlBuilder.append("	array_to_string(array_agg('trip: ' || trips_early::text || ' (' ||");
        sqlBuilder.append(" difference_in_seconds::text || '), vehicle: ' || v_id::text order by trips_early::text), '; ')");
        sqlBuilder.append(" AS trips_early,  \n");
        sqlBuilder.append("		 stop_id,  \n");
        sqlBuilder.append("		 stop_order  \n");
        sqlBuilder.append("	 	FROM trips_early_query_with_time \n");
        sqlBuilder.append("		 GROUP BY stop_id, stop_order ) \n");
        sqlBuilder.append("SELECT      COUNT(CASE WHEN scheduled_time-time > ");
        sqlBuilder.append(allowableEarlyMinutesStr);
        sqlBuilder.append(" THEN 1 ELSE null END) as early, \n");
        sqlBuilder.append("     COUNT(CASE WHEN scheduled_time-time <= ");
        sqlBuilder.append(allowableEarlyMinutesStr);
        sqlBuilder.append(" AND time-scheduled_time <= ");
        sqlBuilder.append(allowableLateMinutesStr);
        sqlBuilder.append(" THEN 1 ELSE null END) AS ontime, \n");
        sqlBuilder.append("     COUNT(CASE WHEN time-scheduled_time > ");
        sqlBuilder.append(allowableLateMinutesStr);
        sqlBuilder.append(" THEN 1 ELSE null END) AS late, \n");
        sqlBuilder.append("     COUNT(*) AS total, \n");
        sqlBuilder.append("     s.name AS stop_name, \n");
        sqlBuilder.append("     ad.direction_id AS direction_id, \n");
        sqlBuilder.append(" 	trips_early_query_v2.trips_early as trips_early, \n");
        sqlBuilder.append(" 	trips_late_query_v2.trips_late as trips_late  \n");
        sqlBuilder.append("FROM arrivals_departures ad INNER JOIN Stops s ON ad.stop_id = s.id \n");
        sqlBuilder.append("LEFT JOIN trips_early_query_v2 ON s.id = trips_early_query_v2.stop_id");
        sqlBuilder.append(" AND ad.stop_order = trips_early_query_v2.stop_order \n");
        sqlBuilder.append("LEFT JOIN trips_late_query_v2 ON s.id = trips_late_query_v2.stop_id AND");
        sqlBuilder.append(" ad.stop_order = trips_late_query_v2.stop_order \n");
        sqlBuilder.append("WHERE ");
        sqlBuilder.append(" ad.config_rev = s.config_rev \n");
        sqlBuilder.append(" AND ad.stop_id = s.id \n");
        sqlBuilder.append(" AND ad.scheduled_time IS NOT NULL \n");
        sqlBuilder.append(SqlUtils.routeClause(route, "ad"));
        sqlBuilder.append("\n");
        sqlBuilder.append(SqlUtils.timeRangeClause(
                "ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate));
        sqlBuilder.append("\n");
        sqlBuilder.append(" GROUP BY direction_id, s.name, s.id, ad.stop_order,");
        sqlBuilder.append(" trips_early_query_v2.trips_early, trips_late_query_v2.trips_late \n");
        sqlBuilder.append(" ORDER BY direction_id, ad.stop_order, s.name");
        // Do the query and return result in JSON format
        return GenericJsonQuery.getJsonString(agencyId, sqlBuilder.toString());
    }

    /* Provides schedule adherence data in JSON format. Provides for
      the specified route the number arrivals/departures that
      are early, number late, number on time, and number total for each
      direction for each stop.
      Request parameters are:
        a - agency ID
        r - route ID or route short name.
        dateRange - in format "xx/xx/xx to yy/yy/yy"
        beginDate - date to begin query. For if dateRange not used.
        numDays - number of days can do query. Limited to 31 days. For if dateRange not used.
        beginTime - for optionally specifying time of day for query for each day
        endTime - for optionally specifying time of day for query for each day
        allowableEarlyMinutes - how early vehicle can be and still be OK.  Decimal format OK.
        allowableLateMinutes - how early vehicle can be and still be OK. Decimal format OK.
    */
    public static String getScheduleAdhByStops_v2(
            String agencyId,
            String route,
            String beginDate,
            String allowableEarly,
            String allowableLate,
            String beginTime,
            String endTime,
            int numDays) {
        if (allowableEarly == null || allowableEarly.isEmpty()) allowableEarly = "1.0";
        String allowableEarlyMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableEarly) + " seconds'";

        if (allowableLate == null || allowableLate.isEmpty()) allowableLate = "4.0";
        String allowableLateMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableLate) + " seconds'";

        String sql = "WITH trips_early_query_with_time AS ( SELECT trip_id AS trips_early, 	"
                + " regexp_replace(CAST(DATE_TRUNC('second', ad.scheduled_time::timestamp) -"
                + " DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR), '^00:', '')"
                + " difference_in_seconds, \n"
                // + "	 abs(((ad.time / 1000) - (ad.scheduled_time / 1000))) AS
                // difference_in_seconds,  \n"
                + "	 s.id AS stop_id, \n"
                + "	 ad.stop_order AS stop_order \n"
                + " 	FROM arrivals_departures ad, stops s  \n"
                + "WHERE "
                // To get stop name
                + " ad.config_rev = s.config_rev \n"
                + " AND ad.stop_id = s.id \n"
                // Only need arrivals/departures that have a schedule time
                + " AND ad.scheduled_time IS NOT NULL \n"
                // Specifies which routes to provide data for
                + SqlUtils.routeClause(route, "ad")
                + "\n"
                + SqlUtils.timeRangeClause("ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate)
                + "\n"
                + " AND scheduled_time-time > "
                + allowableEarlyMinutesStr
                + " \n"
                + "	 ORDER BY direction_id, ad.stop_order, s.name \n"
                + "), \n"
                + "trips_late_query_with_time AS ( SELECT trip_id AS trips_late,  	"
                + " regexp_replace(CAST(DATE_TRUNC('second', ad.time::timestamp) -"
                + " DATE_TRUNC('second', ad.scheduled_time::timestamp) AS VARCHAR), '^00:',"
                + " '') difference_in_seconds, \n"
                // + "	 ((ad.time / 1000) - (ad.scheduled_time / 1000)) AS
                // difference_in_seconds,  \n"
                + "	 s.id AS stop_id, \n"
                + "	 ad.stop_order AS stop_order \n"
                + "	FROM arrivals_departures ad, stops s  \n"
                + "WHERE "
                // To get stop name
                + " ad.config_rev = s.config_rev \n"
                + " AND ad.stop_id = s.id \n"
                // Only need arrivals/departures that have a schedule time
                + " AND ad.scheduled_time IS NOT NULL \n"
                // Specifies which routes to provide data for
                + SqlUtils.routeClause(route, "ad")
                + "\n"
                + SqlUtils.timeRangeClause("ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate)
                + "\n"
                + " AND time-scheduled_time > "
                + allowableLateMinutesStr
                + " \n"
                + "	 ORDER BY direction_id, ad.stop_order, s.name \n"
                + "), \n"
                + "trips_late_query_v2 AS ( 		SELECT"
                + " array_to_string(array_agg(trips_late::text || ' (' ||"
                + " difference_in_seconds::text || ')' order by trips_late::text), '; ') AS"
                + " trips_late,   \n"
                + "		 stop_id,  \n"
                + "		 stop_order  \n"
                + "	 	FROM trips_late_query_with_time \n"
                + "		 GROUP BY stop_id, stop_order \n"
                + "	), \n"
                + "	trips_early_query_v2 AS (  \n"
                + "		SELECT array_to_string(array_agg(trips_early::text || ' (' ||"
                + " difference_in_seconds::text || ')' order by trips_early::text), '; ')"
                + " AS trips_early,  \n"
                + "		 stop_id,  \n"
                + "		 stop_order  \n"
                + "	 	FROM trips_early_query_with_time \n"
                + "		 GROUP BY stop_id, stop_order \n"
                + "	) \n"
                + "SELECT      COUNT(CASE WHEN scheduled_time-time > "
                + allowableEarlyMinutesStr
                + " THEN 1 ELSE null END) as early, \n"
                + "     COUNT(CASE WHEN scheduled_time-time <= "
                + allowableEarlyMinutesStr
                + " AND time-scheduled_time <= "
                + allowableLateMinutesStr
                + " THEN 1 ELSE null END) AS ontime, \n"
                + "     COUNT(CASE WHEN time-scheduled_time > "
                + allowableLateMinutesStr
                + " THEN 1 ELSE null END) AS late, \n"
                + "     COUNT(*) AS total, \n"
                + "     s.name AS stop_name, \n"
                + "     s.id AS stop_id, \n"
                + "     ad.direction_id AS direction_id, \n"
                + " 	trips_early_query_v2.trips_early as trips_early, \n"
                + " 	trips_late_query_v2.trips_late as trips_late  \n"
                + "FROM ArrivalsDepartures ad	INNER JOIN Stops s ON ad.stop_id = s.id \n"
                + "	LEFT JOIN trips_early_query_v2 ON s.id = trips_early_query_v2.stop_id"
                + " AND ad.stop_order = trips_early_query_v2.stop_order \n"
                + "	LEFT JOIN trips_late_query_v2 ON s.id = trips_late_query_v2.stop_id AND"
                + " ad.stop_order = trips_late_query_v2.stop_order \n"
                + "WHERE "
                // To get stop name
                + " ad.config_rev = s.config_rev \n"
                + " AND ad.stop_id = s.id \n"
                // Only need arrivals/departures that have a schedule time
                + " AND ad.scheduled_time IS NOT NULL \n"
                // Specifies which routes to provide data for
                + SqlUtils.routeClause(route, "ad")
                + "\n"
                + SqlUtils.timeRangeClause("ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate)
                + "\n"
                // Grouping and ordering is a bit complicated since might also be looking
                // at old arrival/departure data that doen't have stoporder defined. Also,
                // when configuration changes happen then the stop order can change.
                // Therefore want to group by directionId and stop name. Need to also
                // group by stop order so that can output it, which can be useful for
                // debugging, plus need to order by stop order. For the ORDER BY clause
                // need to order by direction id and stop order, but also the stop name
                // as a backup for if stoporder not defined for data and is therefore
                // always the same and doesn't provide any ordering info.
                + " GROUP BY direction_id, s.name, s.id, ad.stop_order,"
                + " trips_early_query_v2.trips_early, trips_late_query_v2.trips_late \n"
                + " ORDER BY direction_id, ad.stop_order, s.name";

        String sql_trips_early = "SELECT trip_id AS trips_early, 	 regexp_replace(CAST(DATE_TRUNC('second',"
                + " ad.scheduled_time::timestamp) - DATE_TRUNC('second', ad.time::timestamp) AS"
                + " VARCHAR), '^00:', '') difference_in_seconds, \n"
                + "	 s.id AS stop_id, \n"
                + "	 ad.stop_order AS stop_order \n"
                + " 	FROM arrivals_departures ad, stops s  \n"
                + "WHERE "
                // To get stop name
                + " ad.config_rev = s.config_rev \n"
                + " AND ad.stop_id = s.id \n"
                // Only need arrivals/departures that have a schedule time
                + " AND ad.scheduled_time IS NOT NULL \n"
                // Specifies which routes to provide data for
                + SqlUtils.routeClause(route, "ad")
                + "\n"
                + SqlUtils.timeRangeClause("ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate)
                + "\n"
                + " AND scheduled_time-time > "
                + allowableEarlyMinutesStr
                + " \n"
                + "	 ORDER BY direction_id, ad.stop_order, s.name \n";

        String sql_trips_late = "SELECT trip_id AS trips_late,  	 regexp_replace(CAST(DATE_TRUNC('second',"
                + " ad.time::timestamp) - DATE_TRUNC('second', ad.scheduled_time::timestamp) AS"
                + " VARCHAR), '^00:', '') difference_in_seconds, \n"
                // + "	 ((ad.time / 1000) - (ad.scheduled_time / 1000)) AS
                // difference_in_seconds,  \n"
                + "	 s.id AS stop_id, \n"
                + "	 ad.stop_order AS stop_order \n"
                + "	FROM arrivals_departures ad, stops s  \n"
                + "WHERE "
                // To get stop name
                + " ad.config_rev = s.config_rev \n"
                + " AND ad.stop_id = s.id \n"
                // Only need arrivals/departures that have a schedule time
                + " AND ad.scheduled_time IS NOT NULL \n"
                // Specifies which routes to provide data for
                + SqlUtils.routeClause(route, "ad")
                + "\n"
                + SqlUtils.timeRangeClause("ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate)
                + "\n"
                + " AND time-scheduled_time > "
                + allowableLateMinutesStr
                + " \n"
                + "	 ORDER BY direction_id, ad.stop_order, s.name \n";
        // Do the query and return result in JSON format
        String jsonStringTripsEarly = GenericJsonQuery.getJsonString(agencyId, sql_trips_early);
        String jsonStringTripsLate = GenericJsonQuery.getJsonString(agencyId, sql_trips_late);

        JSONObject tripsLateObject = new JSONObject(jsonStringTripsLate);
        JSONArray tripsLateJsonArray = tripsLateObject.getJSONArray("data");
        JSONObject tripsEarlyObject = new JSONObject(jsonStringTripsEarly);
        JSONArray tripsEarlyJsonArray = tripsEarlyObject.getJSONArray("data");

        String jsonString = "";
        jsonString = GenericJsonQuery.getJsonString(agencyId, sql);
        return jsonString;
    }


    /**
     * Queries agency for Stop ID and returns result as a JSON string. Limited to returning
     * MAX_ROWS (50,000) data points.
     *
     * @return Stop reports in JSON format. Can be empty JSON array if no data meets criteria.
     */
    public static String getReportForStopById(String agencyId,
                                              String stop,
                                              String beginDate,
                                              String allowableEarly,
                                              String allowableLate,
                                              String beginTime,
                                              String endTime,
                                              int numDays) {
        if (allowableEarly == null || allowableEarly.isEmpty()) allowableEarly = "1.0";
        String allowableEarlyMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableEarly) + " seconds'";

        if (allowableLate == null || allowableLate.isEmpty()) allowableLate = "4.0";
        String allowableLateMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableLate) + " seconds'";

        //              Specifies which stops to provide data for
        //              Defines time range
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append(" WITH early AS (SELECT time                        AS early,\n");
        sqlBuilder.append("                      s.name                       AS name,\n");
        sqlBuilder.append("                      ad.route_id                  AS route,\n");
        sqlBuilder.append("                      ad.trip_id                   AS trip,\n");
        sqlBuilder.append("                      ad.block_id                  AS block,\n");
        sqlBuilder.append("                      ad.vehicle_id                AS vehicle,\n");
        sqlBuilder.append("                      ad.scheduled_time            AS schedule,\n");
        sqlBuilder.append("                      regexp_replace(CAST(DATE_TRUNC('second', ad.scheduled_time::timestamp) - ");
        sqlBuilder.append("                                          DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR),\n");
        sqlBuilder.append("                              '^00:', ''\n");
        sqlBuilder.append("                      )                            AS difference\n");
        sqlBuilder.append("               FROM arrivals_departures ad,\n");
        sqlBuilder.append("                    stops s\n");
        sqlBuilder.append("               WHERE ad.config_rev = s.config_rev\n");
        sqlBuilder.append("                 AND ad.stop_id = s.id\n");
        sqlBuilder.append("                 AND ad.scheduled_time IS NOT NULL \n");
        sqlBuilder.append(SqlUtils.stopClause(stop, "ad"));
        sqlBuilder.append(" \n");
        sqlBuilder.append(SqlUtils.timeRangeClause(
                "ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate));
        sqlBuilder.append(" AND scheduled_time - time > ");
        sqlBuilder.append(allowableEarlyMinutesStr);
        sqlBuilder.append(" \n");
        sqlBuilder.append(" ORDER BY early),\n");
        sqlBuilder.append("     on_time AS (SELECT time                        AS on_time,\n");
        sqlBuilder.append("                       s.name                       AS name,\n");
        sqlBuilder.append("                       ad.route_id                  AS route,\n");
        sqlBuilder.append("                       ad.trip_id                   AS trip,\n");
        sqlBuilder.append("                       ad.block_id                  AS block,\n");
        sqlBuilder.append("                       ad.vehicle_id                AS vehicle,\n");
        sqlBuilder.append("                       ad.scheduled_time            AS schedule,\n");
        sqlBuilder.append("                       regexp_replace(\n");
        sqlBuilder.append("                               CAST(DATE_TRUNC('second', ad.scheduled_time::timestamp) - ");
        sqlBuilder.append("                                    DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR),\n");
        sqlBuilder.append("                               '^(-)?00:', '\\1'\n");
        sqlBuilder.append("                       )                            AS difference\n");
        sqlBuilder.append("                FROM arrivals_departures ad,\n");
        sqlBuilder.append("                     stops s\n");
        sqlBuilder.append("                WHERE ad.config_rev = s.config_rev\n");
        sqlBuilder.append("                  AND ad.stop_id = s.id\n");
        sqlBuilder.append("                  AND ad.scheduled_time IS NOT NULL \n");
        sqlBuilder.append(SqlUtils.stopClause(stop, "ad"));
        sqlBuilder.append(" \n");
        sqlBuilder.append(SqlUtils.timeRangeClause(
                "ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate));
        sqlBuilder.append(" AND scheduled_time - time <= ");
        sqlBuilder.append(allowableEarlyMinutesStr);
        sqlBuilder.append(" AND time - scheduled_time <= ");
        sqlBuilder.append(allowableLateMinutesStr);
        sqlBuilder.append(" \n");
        sqlBuilder.append(" ORDER BY on_time),\n");
        sqlBuilder.append("     late AS (SELECT time                         AS late,\n");
        sqlBuilder.append("                     s.name                       AS name,\n");
        sqlBuilder.append("                     ad.route_id                  AS route,\n");
        sqlBuilder.append("                     ad.trip_id                   AS trip,\n");
        sqlBuilder.append("                     ad.block_id                  AS block,\n");
        sqlBuilder.append("                     ad.vehicle_id                AS vehicle,\n");
        sqlBuilder.append("                     ad.scheduled_time            AS schedule,\n");
        sqlBuilder.append("                     regexp_replace(\n");
        sqlBuilder.append("                             CAST(DATE_TRUNC('second', ad.scheduled_time::timestamp) - ");
        sqlBuilder.append("                                  DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR),\n");
        sqlBuilder.append("                             '^(-)00:', '\\1'\n");
        sqlBuilder.append("                     )                            AS difference\n");
        sqlBuilder.append("              FROM arrivals_departures ad,\n");
        sqlBuilder.append("                   stops s\n");
        sqlBuilder.append("              WHERE ad.config_rev = s.config_rev\n");
        sqlBuilder.append("                AND ad.stop_id = s.id\n");
        sqlBuilder.append("                AND ad.scheduled_time IS NOT NULL \n");
        sqlBuilder.append(SqlUtils.stopClause(stop, "ad"));
        sqlBuilder.append(" \n");
        sqlBuilder.append(SqlUtils.timeRangeClause(
                "ad.time", MAX_NUM_DAYS, numDays, beginTime, endTime, beginDate));
        sqlBuilder.append(" AND time - scheduled_time > ");
        sqlBuilder.append(allowableLateMinutesStr);
        sqlBuilder.append(" \n");
        sqlBuilder.append(" ORDER BY late) \n");
        sqlBuilder.append(" SELECT * FROM \n");
        sqlBuilder.append("     early\n");
        sqlBuilder.append("         FULL OUTER JOIN\n");
        sqlBuilder.append("     on_time ON early.early = on_time.on_time\n");
        sqlBuilder.append("         FULL OUTER JOIN\n");
        sqlBuilder.append("     late ON on_time.on_time = late.late\n");

        return GenericJsonQuery.getJsonString(agencyId, sqlBuilder
                        .toString())
                .replaceFirst("\\bdata\\b", stop);
    }

    /**
     * Queries agency for AVL data and returns result as a JSON string. Limited to returning
     * MAX_ROWS (50,000) data points.
     *
     * @return Last AVL reports in JSON format. Can be empty JSON array if no data meets criteria.
     */
    public static String getLastAvlJson(String agencyId) {
        WebAgency agency = WebAgency.getCachedWebAgency(agencyId);
        String sql = "";
        if (agency.getDbType().equals("mysql")) {
            sql = "SELECT a.vehicle_id, vC.name, maxTime, lat, lon "
                    + "FROM "
                    + "(SELECT vehicle_id, max(time) AS maxTime "
                    + "FROM avl_reports WHERE time > date_sub(now(), interval 1 day) "
                    + "GROUP BY vehicle_id) a "
                    + "JOIN avl_reports b ON a.vehicle_id=b.vehicle_id AND a.maxTime = b.time "
                    + "JOIN vehicle_configs vC ON a.vehicle_id=vC.id";
        }
        if (agency.getDbType().equals("postgresql")) {
            sql = "select a.vehicle_id as \"vehicleId\", vC.name as \"name\", a.maxTime as"
                    + " \"maxTime\", lat, lon from ( SELECT vehicle_id, max(time) AS maxTime"
                    + " FROM avl_reports WHERE time > now() + '-24 hours' GROUP BY vehicle_id) a"
                    + " JOIN avl_reports b ON a.vehicle_id=b.vehicle_id AND a.maxTime = b.time"
                    + " JOIN vehicle_configs vC ON a.vehicle_id=vC.id";
        }

        String json = null;
        json = GenericJsonQuery.getJsonString(agencyId, sql);
        return json;
    }

    public static String getOnTimePerformance(String agencyId,
                                              boolean isForAllRoutes,
                                              String accuracy,
                                              String beginDate,
                                              String endDate,
                                              String allowableEarly,
                                              String allowableLate) {
        WebAgency agency = WebAgency.getCachedWebAgency(agencyId);

        if (allowableEarly == null || allowableEarly.isEmpty()) allowableEarly = "1.0";
        String allowableEarlySecondsStr = "'" + SqlUtils.convertMinutesToSecs(allowableEarly) + "'";

        if (allowableLate == null || allowableLate.isEmpty()) allowableLate = "3.0";
        String allowableLateSecondsStr = "'" + SqlUtils.convertMinutesToSecs(allowableLate) + "'";

        Date begin = getValidateDate(beginDate);
        Date end = getValidateDate(endDate);
        // 6 months validator
        if ((end.getTime() - begin.getTime()) > (YEAR_IN_MSECS / 2) + DAY_IN_MSECS)
            throw new IllegalArgumentException("The period of time is longer then 6 months");

        StringBuilder sqlBuilder = new StringBuilder("SELECT \n");
        if (isForAllRoutes) sqlBuilder.append("COALESCE(ad.route_short_name, 'ALL_ROUTES') AS route, \n");
        else if (accuracy.equals("day"))
            sqlBuilder.append("COALESCE(TO_CHAR(DATE_TRUNC('day', ad.scheduled_time), 'YYYY-MM-DD'), 'TOTAL') AS day, \n");
        else if (accuracy.equals("week"))
            sqlBuilder.append("COALESCE(TO_CHAR(DATE_TRUNC('week', ad.scheduled_time), 'YYYY-MM-DD'), 'TOTAL') AS week, \n");
        else if (accuracy.equals("month"))
            sqlBuilder.append("COALESCE(TO_CHAR(DATE_TRUNC('month', ad.scheduled_time), 'YYYY-MM-DD'), 'TOTAL') AS month, \n");
        sqlBuilder.append("COUNT(CASE WHEN scheduled_time - time > interval ").append(allowableEarlySecondsStr).append(" THEN 1 END) AS early,\n");
        sqlBuilder.append("    ROUND(\n");
        sqlBuilder.append("        COUNT(CASE WHEN scheduled_time - time > interval ").append(allowableEarlySecondsStr).append(" THEN 1 END) * 100.0 \n");
        sqlBuilder.append("        / NULLIF(COUNT(*), 0), 2\n");
        sqlBuilder.append("    )::text AS early_pct,\n");
        sqlBuilder.append("\n");
        sqlBuilder.append("    COUNT(CASE WHEN scheduled_time - time <= interval ").append(allowableEarlySecondsStr).append("\n");
        sqlBuilder.append("                AND time - scheduled_time <= interval ").append(allowableLateSecondsStr).append(" THEN 1 END) AS ontime,\n");
        sqlBuilder.append("    ROUND(\n");
        sqlBuilder.append("        COUNT(CASE WHEN scheduled_time - time <= interval ").append(allowableEarlySecondsStr).append("\n");
        sqlBuilder.append("                   AND time - scheduled_time <= interval ").append(allowableLateSecondsStr).append(" THEN 1 END) * 100.0 \n");
        sqlBuilder.append("        / NULLIF(COUNT(*), 0), 2\n");
        sqlBuilder.append("    )::text AS ontime_pct,\n");
        sqlBuilder.append("\n");
        sqlBuilder.append("    COUNT(CASE WHEN time - scheduled_time > interval ").append(allowableLateSecondsStr).append(" THEN 1 END) AS late,\n");
        sqlBuilder.append("    ROUND(\n");
        sqlBuilder.append("        COUNT(CASE WHEN time - scheduled_time > interval ").append(allowableLateSecondsStr).append(" THEN 1 END) * 100.0 \n");
        sqlBuilder.append("        / NULLIF(COUNT(*), 0), 2\n");
        sqlBuilder.append("    )::text AS late_pct,\n");
        sqlBuilder.append("\n");
        sqlBuilder.append("    COUNT(*) AS total\n");
        sqlBuilder.append("FROM arrivals_departures ad\n");
        sqlBuilder.append("WHERE ad.scheduled_time IS NOT NULL\n");
        sqlBuilder.append("AND DATE(ad.time) BETWEEN DATE('").append(begin).append("') AND DATE('").append(end).append("')\n");
        sqlBuilder.append("\n");
        if (isForAllRoutes) {
            sqlBuilder.append("GROUP BY GROUPING SETS ((ad.route_short_name), ()) \n");
            sqlBuilder.append("HAVING COUNT(*) > 0 \n");
            sqlBuilder.append("ORDER BY route; \n");
        } else {
            sqlBuilder.append("GROUP BY ROLLUP (DATE_TRUNC('").append(accuracy).append("', ad.scheduled_time)) \n");
            sqlBuilder.append("HAVING COUNT(*) > 0 \n");
            sqlBuilder.append("ORDER BY ").append(accuracy).append(";");
        }

        return GenericJsonQuery.getJsonString(agency.getAgencyId(), sqlBuilder
                .toString());
    }

    public static String getSqlForAllStopsSchedAdh(String agencyId,
                                                   LocalDate date,
                                                   String routeId,
                                                   String allowableEarly,
                                                   String allowableLate) {
        WebAgency agency = WebAgency.getCachedWebAgency(agencyId);

        if (allowableEarly == null || allowableEarly.isEmpty()) allowableEarly = "1.0";
        String allowableEarlyMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableEarly) + " seconds'";

        if (allowableLate == null || allowableLate.isEmpty()) allowableLate = "4.0";
        String allowableLateMinutesStr = "'" + SqlUtils.convertMinutesToSecs(allowableLate) + " seconds'";

        String sql = "";
        StringBuilder sqlBuilder;

        if (agency.getDbType().equals("postgresql")) {
            sqlBuilder = new StringBuilder("WITH early AS (\n");
            sqlBuilder.append("    SELECT time,\n");
            sqlBuilder.append("           s.name AS name,\n");
            sqlBuilder.append("           ad.route_id AS route,\n");
            sqlBuilder.append("           ad.trip_id AS trip,\n");
            sqlBuilder.append("           ad.block_id AS block,\n");
            sqlBuilder.append("           ad.vehicle_id AS vehicle,\n");
            sqlBuilder.append("           ad.scheduled_time AS schedule,\n");
            sqlBuilder.append("           regexp_replace(\n");
            sqlBuilder.append("                   CAST(DATE_TRUNC('second', ad.scheduled_time::timestamp) - DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR),\n");
            sqlBuilder.append("                   '^00:', ''\n");
            sqlBuilder.append("           ) AS difference,\n");
            sqlBuilder.append("           'early' AS category_order  -- Added order indicator\n");
            sqlBuilder.append("    FROM arrivals_departures ad\n");
            sqlBuilder.append("             JOIN stops s ON ad.config_rev = s.config_rev AND ad.stop_id = s.id\n");
            sqlBuilder.append("    WHERE ad.scheduled_time IS NOT NULL\n");
            sqlBuilder.append(SqlUtils.timeRangeClause(date, date));
            sqlBuilder.append("      AND scheduled_time - time > ");
            sqlBuilder.append(allowableEarlyMinutesStr);
            if (routeId != null || !routeId.isEmpty())
                sqlBuilder.append("           AND ad.route_id = '").append(routeId).append("'\n");
            sqlBuilder.append(" \n");
            sqlBuilder.append("),\n");
            sqlBuilder.append("     on_time AS (\n");
            sqlBuilder.append("         SELECT time,\n");
            sqlBuilder.append("                s.name AS name,\n");
            sqlBuilder.append("                ad.route_id AS route,\n");
            sqlBuilder.append("                ad.trip_id AS trip,\n");
            sqlBuilder.append("                ad.block_id AS block,\n");
            sqlBuilder.append("                ad.vehicle_id AS vehicle,\n");
            sqlBuilder.append("                ad.scheduled_time AS schedule,\n");
            sqlBuilder.append("                regexp_replace(\n");
            sqlBuilder.append("                        CAST(DATE_TRUNC('second', ad.scheduled_time::timestamp) - DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR),\n");
            sqlBuilder.append("                        '^(-)?00:', '\\1'\n");
            sqlBuilder.append("                ) AS difference,\n");
            sqlBuilder.append("                'on_time' AS category_order  -- Added order indicator\n");
            sqlBuilder.append("         FROM arrivals_departures ad\n");
            sqlBuilder.append("                  JOIN stops s ON ad.config_rev = s.config_rev AND ad.stop_id = s.id\n");
            sqlBuilder.append("         WHERE ad.scheduled_time IS NOT NULL\n");
            sqlBuilder.append(SqlUtils.timeRangeClause(date, date));
            sqlBuilder.append("           AND scheduled_time - time <= ");
            sqlBuilder.append(allowableEarlyMinutesStr);
            sqlBuilder.append(" \n");
            sqlBuilder.append("           AND time - scheduled_time <= ");
            sqlBuilder.append(allowableLateMinutesStr);
            if (routeId != null || !routeId.isEmpty())
                sqlBuilder.append("           AND ad.route_id = '").append(routeId).append("'\n");
            sqlBuilder.append(" \n");
            sqlBuilder.append("     ),\n");
            sqlBuilder.append("     late AS (\n");
            sqlBuilder.append("         SELECT time ,\n");
            sqlBuilder.append("                s.name AS name,\n");
            sqlBuilder.append("                ad.route_id AS route,\n");
            sqlBuilder.append("                ad.trip_id AS trip,\n");
            sqlBuilder.append("                ad.block_id AS block,\n");
            sqlBuilder.append("                ad.vehicle_id AS vehicle,\n");
            sqlBuilder.append("                ad.scheduled_time AS schedule,\n");
            sqlBuilder.append("                regexp_replace(\n");
            sqlBuilder.append("                        CAST(DATE_TRUNC('second', ad.scheduled_time::timestamp) - DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR),\n");
            sqlBuilder.append("                        '^(-)00:', '\\1'\n");
            sqlBuilder.append("                ) AS difference,\n");
            sqlBuilder.append("                'late' AS category_order\n");
            sqlBuilder.append("         FROM arrivals_departures ad\n");
            sqlBuilder.append("                  JOIN stops s ON ad.config_rev = s.config_rev AND ad.stop_id = s.id\n");
            sqlBuilder.append("         WHERE ad.scheduled_time IS NOT NULL\n");
            sqlBuilder.append(SqlUtils.timeRangeClause(date, date));
            sqlBuilder.append("           AND time - scheduled_time > ");
            sqlBuilder.append(allowableLateMinutesStr);
            if (routeId != null || !routeId.isEmpty())
                sqlBuilder.append("           AND ad.route_id = '").append(routeId).append("'\n");
            sqlBuilder.append(" )\n");
            sqlBuilder.append("SELECT *\n");
            sqlBuilder.append("FROM (\n");
            sqlBuilder.append("         SELECT category_order, time, name, route, trip, block, vehicle, schedule, difference FROM early\n");
            sqlBuilder.append("         UNION ALL\n");
            sqlBuilder.append("         SELECT category_order, time, name, route, trip, block, vehicle, schedule, difference FROM on_time\n");
            sqlBuilder.append("         UNION ALL\n");
            sqlBuilder.append("         SELECT category_order, time, name, route, trip, block, vehicle, schedule, difference FROM late\n");
            sqlBuilder.append("     ) AS combined_results\n");
            if (routeId != null || !routeId.isEmpty()) sqlBuilder.append("ORDER BY category_order, trip, time;\n");
            else sqlBuilder.append("ORDER BY category_order, time;\n");
            sql = sqlBuilder.toString();
        }
        return sql;
    }

    private static Date getValidateDate(String beginDate) {
        Date startdate = null;
        try {
            if (beginDate.charAt(4) != '-') {
                DateFormat defaultDateFormat = new SimpleDateFormat("MM-dd-yyyy");
                startdate = defaultDateFormat.parse(beginDate);
            } else {
                DateFormat altDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                startdate = altDateFormat.parse(beginDate);
            }
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return startdate;
    }
}
