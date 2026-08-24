/* (C)2023 */
package org.transitclock.core.reports;

import lombok.Getter;
import org.transitclock.domain.GenericQuery;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Report of executed trips for a route over a date range, with stop-level schedule adherence.
 * Stop selection uses timepoints when present for a given trip execution; otherwise first,
 * middle, and last stop.
 */
public class RouteTripsStopAdherenceReport {

    /** Same limit as {@link Reports} SQL reports. */
    private static final int MAX_ROWS = 200_000;

    private static final int MAX_DAYS = 92;

    private RouteTripsStopAdherenceReport() {}

    public static Result run(String agencyId, String beginDate, String endDate, String routeId) {
        LocalDate begin = parseDate(beginDate);
        LocalDate end = parseDate(endDate);
        validateDateRange(begin, end);
        SqlUtils.throwOnSqlInjection(routeId);
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId is required");
        }

        String cteSql = buildCteSql(routeId, begin, end);
        long rowCount = queryRowCount(agencyId, cteSql + "SELECT COUNT(*) FROM filtered");
        if (rowCount > MAX_ROWS) {
            throw new IllegalArgumentException("Report would return " + rowCount + " stop rows, exceeding the"
                    + " limit of " + MAX_ROWS + ". Please narrow the date range.");
        }

        List<FlatRow> rows = queryRows(agencyId, cteSql + buildSelectSql());
        return assemble(rows);
    }

    private static void validateDateRange(LocalDate begin, LocalDate end) {
        if (end.isBefore(begin)) {
            throw new IllegalArgumentException("endDate must be on or after beginDate");
        }
        long days = ChronoUnit.DAYS.between(begin, end) + 1;
        if (days > MAX_DAYS) {
            throw new IllegalArgumentException("Date range is limited to " + MAX_DAYS + " days (3 months)");
        }
    }

    private static LocalDate parseDate(String date) {
        DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter us = DateTimeFormatter.ofPattern("MM-dd-yyyy");
        try {
            if (date.charAt(4) != '-') {
                return LocalDate.parse(date, us);
            }
            return LocalDate.parse(date, iso);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + date);
        }
    }

    private static String buildCteSql(String routeId, LocalDate begin, LocalDate end) {
        String beginStr = begin.toString();
        String endStr = end.toString();

        StringBuilder sql = new StringBuilder();
        sql.append("WITH trip_gtfs_timepoints AS (\n");
        sql.append("    SELECT t.trip_id,\n");
        sql.append("           t.config_rev,\n");
        sql.append("           BOOL_OR(COALESCE(s.time_point_stop, FALSE)) AS has_timepoints\n");
        sql.append("    FROM trips t\n");
        sql.append("             INNER JOIN trip_pattern_to_path tpp\n");
        sql.append("                        ON tpp.trip_pattern_id = t.trippattern_id\n");
        sql.append("                            AND tpp.trip_pattern_config_rev = t.trippattern_config_rev\n");
        sql.append("             INNER JOIN stop_paths sp\n");
        sql.append("                        ON sp.trip_pattern_id = tpp.stop_path_trip_pattern_id\n");
        sql.append("                            AND sp.stop_path_id = tpp.stop_path_stop_path_id\n");
        sql.append("                            AND sp.config_rev = tpp.stop_path_config_rev\n");
        sql.append("             INNER JOIN stops s ON s.id = sp.stop_id AND s.config_rev = sp.config_rev\n");
        sql.append("    GROUP BY t.trip_id, t.config_rev\n");
        sql.append("),\n");
        sql.append("base AS (\n");
        sql.append("    SELECT DATE(ad.avl_time) AS trip_date,\n");
        sql.append("           ad.trip_id AS trip_id,\n");
        sql.append("           ad.vehicle_id AS vehicle_id,\n");
        sql.append("           ad.block_id AS block_id,\n");
        sql.append("           ad.trip_index AS trip_index,\n");
        sql.append("           ad.freq_start_time AS freq_start_time,\n");
        sql.append("           ad.direction_id AS direction_id,\n");
        sql.append("           ad.stop_id AS stop_id,\n");
        sql.append("           stops.code AS stop_code,\n");
        sql.append("           stops.name AS stop_name,\n");
        sql.append("           ad.stop_order AS stop_order,\n");
        sql.append("           ad.gtfs_stop_seq AS gtfs_stop_seq,\n");
        sql.append("           ad.time AS actual_arrival,\n");
        sql.append("           ADDeparture.time AS actual_departure,\n");
        sql.append("           CASE WHEN ad.scheduled_time IS NULL\n");
        sql.append("                THEN DATE(ad.avl_time) + trip_scheduled_times_list.arrival_time * interval '1 second'\n");
        sql.append("                ELSE ad.scheduled_time END AS scheduled_arrival,\n");
        sql.append("           CASE WHEN ADDeparture.scheduled_time IS NULL\n");
        sql.append("                THEN DATE(ad.avl_time) + trip_scheduled_times_list.departure_time * interval '1 second'\n");
        sql.append("                ELSE ADDeparture.scheduled_time END AS scheduled_departure,\n");
        sql.append("           CASE\n");
        sql.append("               WHEN ADDeparture.scheduled_time IS NULL THEN regexp_replace(\n");
        sql.append("                   CAST(DATE_TRUNC('second', DATE(ad.avl_time) +\n");
        sql.append("                        trip_scheduled_times_list.arrival_time * interval '1 second') -\n");
        sql.append("                        DATE_TRUNC('second', ad.time::timestamp) AS VARCHAR), '^00:', '')\n");
        sql.append("               ELSE regexp_replace(\n");
        sql.append("                   CAST(DATE_TRUNC('second', ADDeparture.scheduled_time::timestamp) -\n");
        sql.append("                        DATE_TRUNC('second', ADDeparture.time::timestamp) AS VARCHAR), '^00:', '')\n");
        sql.append("           END AS deviation,\n");
        sql.append("           COALESCE(stops.time_point_stop, FALSE) AS is_timepoint,\n");
        sql.append("           vehicle_configs.name AS vehicle_name,\n");
        sql.append("           COALESCE(tgt.has_timepoints, FALSE) AS trip_has_timepoints,\n");
        sql.append("           ROW_NUMBER() OVER (\n");
        sql.append("               PARTITION BY ad.trip_id, DATE(ad.avl_time), ad.vehicle_id, ad.block_id,\n");
        sql.append("                            ad.trip_index, ad.freq_start_time\n");
        sql.append("               ORDER BY ad.stop_order\n");
        sql.append("           ) AS rn,\n");
        sql.append("           COUNT(*) OVER (\n");
        sql.append("               PARTITION BY ad.trip_id, DATE(ad.avl_time), ad.vehicle_id, ad.block_id,\n");
        sql.append("                            ad.trip_index, ad.freq_start_time\n");
        sql.append("           ) AS stop_cnt\n");
        sql.append("    FROM arrivals_departures ad\n");
        sql.append("         LEFT JOIN trip_gtfs_timepoints tgt\n");
        sql.append("                   ON tgt.trip_id = ad.trip_id AND tgt.config_rev = ad.config_rev\n");
        sql.append("         LEFT JOIN stops ON stops.id = ad.stop_id AND stops.config_rev = ad.config_rev\n");
        sql.append("         LEFT JOIN vehicle_configs ON vehicle_configs.id = ad.vehicle_id\n");
        sql.append("         LEFT JOIN LATERAL (SELECT *\n");
        sql.append("                            FROM arrivals_departures dep\n");
        sql.append("                            WHERE dep.trip_id = ad.trip_id\n");
        sql.append("                              AND dep.direction_id = ad.direction_id\n");
        sql.append("                              AND dep.stop_id = ad.stop_id\n");
        sql.append("                              AND LOWER(dep.type) = LOWER('DEPARTURE')\n");
        sql.append("                              AND DATE(dep.avl_time) = DATE(ad.avl_time)\n");
        sql.append("                              AND dep.time >= ad.time\n");
        sql.append("                            ORDER BY dep.time ASC\n");
        sql.append("                            LIMIT 1) ADDeparture ON TRUE\n");
        sql.append("         LEFT JOIN trip_scheduled_times_list\n");
        sql.append("                   ON trip_scheduled_times_list.trip_trip_id = ad.trip_id\n");
        sql.append("                       AND trip_scheduled_times_list.trip_config_rev = ad.config_rev\n");
        sql.append("                       AND trip_scheduled_times_list.list_index = ad.stop_order\n");
        sql.append("    WHERE ad.is_arrival = TRUE\n");
        sql.append("      AND ad.route_id = '").append(routeId).append("'\n");
        sql.append("      AND ad.time >= DATE('").append(beginStr).append("')\n");
        sql.append("      AND ad.time < DATE('").append(endStr).append("') + INTERVAL '1 day'\n");
        sql.append("),\n");
        sql.append("filtered AS (\n");
        sql.append("    SELECT *\n");
        sql.append("    FROM base\n");
        sql.append("    WHERE (trip_has_timepoints AND is_timepoint = TRUE)\n");
        sql.append("       OR (NOT trip_has_timepoints AND (\n");
        sql.append("              rn = 1 OR rn = stop_cnt OR rn = CEIL(stop_cnt::numeric / 2)\n");
        sql.append("          ))\n");
        sql.append(")\n");

        return sql.toString();
    }

    private static String buildSelectSql() {
        return "SELECT trip_date, trip_id, vehicle_id, vehicle_name, block_id, trip_index, freq_start_time,\n"
                + "       direction_id, stop_id, stop_code, stop_name, stop_order, gtfs_stop_seq,\n"
                + "       actual_arrival, actual_departure, scheduled_arrival, scheduled_departure,\n"
                + "       deviation, is_timepoint\n"
                + "FROM filtered\n"
                + "ORDER BY trip_date, trip_id, vehicle_id, block_id, trip_index, freq_start_time, stop_order\n"
                + "LIMIT "
                + MAX_ROWS
                + "\n";
    }

    private static long queryRowCount(String agencyId, String sql) {
        try {
            return CountQuery.run(agencyId, sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<FlatRow> queryRows(String agencyId, String sql) {
        try {
            return FlatRowQuery.run(agencyId, sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Result assemble(List<FlatRow> rows) {
        Map<LocalDate, Map<TripExecutionKey, TripExecution>> byDate = new LinkedHashMap<>();

        for (FlatRow row : rows) {
            byDate
                    .computeIfAbsent(row.tripDate(), d -> new LinkedHashMap<>())
                    .computeIfAbsent(row.tripKey(), k -> new TripExecution(
                            row.tripId(),
                            row.vehicleId(),
                            row.vehicleName(),
                            row.blockId(),
                            row.directionId()))
                    .getStops()
                    .add(row.toStop());
        }

        List<DateGroup> dates = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<TripExecutionKey, TripExecution>> dateEntry : byDate.entrySet()) {
            List<TripExecution> trips = new ArrayList<>(dateEntry.getValue().values());
            dates.add(new DateGroup(dateEntry.getKey().toString(), trips));
        }
        return new Result(dates);
    }

    private record TripExecutionKey(
            String tripId, String vehicleId, String blockId, Integer tripIndex, Timestamp freqStartTime) {
        static TripExecutionKey of(FlatRow row) {
            return new TripExecutionKey(
                    row.tripId(), row.vehicleId(), row.blockId(), row.tripIndex(), row.freqStartTime());
        }
    }

    private static final class CountQuery extends GenericQuery {
        private long count;

        private CountQuery(String agencyId) throws SQLException {
            super(agencyId);
        }

        static long run(String agencyId, String sql) throws SQLException {
            CountQuery query = new CountQuery(agencyId);
            query.doQuery(sql);
            return query.count;
        }

        @Override
        protected void addRow(List<Object> values) {
            if (!values.isEmpty() && values.get(0) instanceof Number number) {
                count = number.longValue();
            }
        }
    }

    private static final class FlatRowQuery extends GenericQuery {
        private final List<FlatRow> rows = new ArrayList<>();
        private final List<String> columnNames = new ArrayList<>();

        private FlatRowQuery(String agencyId) throws SQLException {
            super(agencyId);
        }

        static List<FlatRow> run(String agencyId, String sql) throws SQLException {
            FlatRowQuery query = new FlatRowQuery(agencyId);
            query.doQuery(sql);
            return query.rows;
        }

        @Override
        protected void addColumn(String columnName, int type) {
            columnNames.add(columnName);
        }

        @Override
        protected void addRow(List<Object> values) {
            rows.add(FlatRow.from(columnNames, values));
        }
    }

    private record FlatRow(
            LocalDate tripDate,
            String tripId,
            String vehicleId,
            String vehicleName,
            String blockId,
            Integer tripIndex,
            Timestamp freqStartTime,
            String directionId,
            String stopId,
            Integer stopCode,
            String stopName,
            Integer stopOrder,
            Integer gtfsStopSeq,
            Timestamp actualArrival,
            Timestamp actualDeparture,
            Timestamp scheduledArrival,
            Timestamp scheduledDeparture,
            String deviation,
            boolean isTimepoint) {

        TripExecutionKey tripKey() {
            return TripExecutionKey.of(this);
        }

        Stop toStop() {
            return new Stop(
                    stopId,
                    stopCode,
                    stopName,
                    stopOrder,
                    gtfsStopSeq,
                    vehicleId,
                    vehicleName,
                    actualArrival,
                    actualDeparture,
                    scheduledArrival,
                    scheduledDeparture,
                    deviation,
                    directionId,
                    isTimepoint);
        }

        static FlatRow from(List<String> columns, List<Object> values) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                map.put(columns.get(i), values.get(i));
            }
            LocalDate tripDate = toLocalDate(map.get("trip_date"));
            return new FlatRow(
                    tripDate,
                    stringVal(map.get("trip_id")),
                    stringVal(map.get("vehicle_id")),
                    stringVal(map.get("vehicle_name")),
                    stringVal(map.get("block_id")),
                    intVal(map.get("trip_index")),
                    timestampVal(map.get("freq_start_time")),
                    stringVal(map.get("direction_id")),
                    stringVal(map.get("stop_id")),
                    intVal(map.get("stop_code")),
                    stringVal(map.get("stop_name")),
                    intVal(map.get("stop_order")),
                    intVal(map.get("gtfs_stop_seq")),
                    timestampVal(map.get("actual_arrival")),
                    timestampVal(map.get("actual_departure")),
                    timestampVal(map.get("scheduled_arrival")),
                    timestampVal(map.get("scheduled_departure")),
                    stringVal(map.get("deviation")),
                    booleanVal(map.get("is_timepoint")));
        }

        private static LocalDate toLocalDate(Object value) {
            if (value instanceof Date d) {
                return d.toLocalDate();
            }
            if (value instanceof Timestamp ts) {
                return ts.toLocalDateTime().toLocalDate();
            }
            if (value instanceof java.util.Date d) {
                return new Timestamp(d.getTime()).toLocalDateTime().toLocalDate();
            }
            return LocalDate.parse(value.toString());
        }

        private static String stringVal(Object value) {
            return value == null ? null : value.toString();
        }

        private static Integer intVal(Object value) {
            if (value == null) return null;
            if (value instanceof Number n) return n.intValue();
            return Integer.parseInt(value.toString());
        }

        private static Timestamp timestampVal(Object value) {
            if (value == null) return null;
            if (value instanceof Timestamp ts) return ts;
            if (value instanceof java.util.Date d) return new Timestamp(d.getTime());
            return Timestamp.valueOf(value.toString());
        }

        private static boolean booleanVal(Object value) {
            if (value == null) return false;
            if (value instanceof Boolean b) return b;
            return Boolean.parseBoolean(value.toString());
        }
    }

    @Getter
    public static final class Result {
        private final List<DateGroup> dates;

        public Result(List<DateGroup> dates) {
            this.dates = dates;
        }
    }

    @Getter
    public static final class DateGroup {
        private final String date;
        private final List<TripExecution> trips;

        public DateGroup(String date, List<TripExecution> trips) {
            this.date = date;
            this.trips = trips;
        }
    }

    @Getter
    public static final class TripExecution {
        private final String tripId;
        private final String vehicleId;
        private final String vehicleName;
        private final String blockId;
        private final String directionId;
        private final List<Stop> stops = new ArrayList<>();

        public TripExecution(
                String tripId, String vehicleId, String vehicleName, String blockId, String directionId) {
            this.tripId = tripId;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.blockId = blockId;
            this.directionId = directionId;
        }
    }

    @Getter
    public static final class Stop {
        private final String stopId;
        private final Integer stopCode;
        private final String stopName;
        private final Integer stopSequence;
        private final Integer gtfsStopSeq;
        private final String vehicleId;
        private final String vehicleName;
        private final Timestamp actualArrival;
        private final Timestamp actualDeparture;
        private final Timestamp scheduledArrival;
        private final Timestamp scheduledDeparture;
        private final String deviation;
        private final String directionId;
        private final boolean isTimepoint;

        public Stop(
                String stopId,
                Integer stopCode,
                String stopName,
                Integer stopSequence,
                Integer gtfsStopSeq,
                String vehicleId,
                String vehicleName,
                Timestamp actualArrival,
                Timestamp actualDeparture,
                Timestamp scheduledArrival,
                Timestamp scheduledDeparture,
                String deviation,
                String directionId,
                boolean isTimepoint) {
            this.stopId = stopId;
            this.stopCode = stopCode;
            this.stopName = stopName;
            this.stopSequence = stopSequence;
            this.gtfsStopSeq = gtfsStopSeq;
            this.vehicleId = vehicleId;
            this.vehicleName = vehicleName;
            this.actualArrival = actualArrival;
            this.actualDeparture = actualDeparture;
            this.scheduledArrival = scheduledArrival;
            this.scheduledDeparture = scheduledDeparture;
            this.deviation = deviation;
            this.directionId = directionId;
            this.isTimepoint = isTimepoint;
        }
    }
}
