/* (C)2023 */
package org.transitclock.api.reports;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.transitclock.config.data.AgencyConfig;
import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.utils.Time;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * For doing SQL query and generating JSON data for a prediction accuracy chart. This abstract class
 * does the SQL query and puts data into a map. Then a subclass must be used to convert the data to
 * JSON rows and columns for Google chart.
 *
 * <p>TODO: rewrite as hibernate criteria.
 *
 * @author SkiBu Smith
 */
@Slf4j
public abstract class PredictionAccuracyQuery {
    protected static final int MAX_PRED_LENGTH = 900;
    protected static final int PREDICTION_LENGTH_BUCKET_SIZE = 30;

    // Keyed on source (so can show data for multiple sources at
    // once in order to compare prediction accuracy. Contains a array,
    // with an element for each prediction bucket, containing an array
    // of the prediction accuracy values in seconds for that bucket. Each bucket
    // is for
    // a certain prediction range, specified by predictionLengthBucketSize.
    protected final Map<String, List<List<Integer>>> map = new HashMap<>();

    // Defines the output type for the intervals, whether should show
    // standard deviation, percentage, or both.
    // Can iterate over the enumerated type using:
    // for (IntervalsType type : IntervalsType.values()) {}
    public enum IntervalsType {
        PERCENTAGE("PERCENTAGE"),
        STD_DEV("STD_DEV"),
        BOTH("BOTH");

        private final String text;

        IntervalsType(final String text) {
            this.text = text;
        }

        /**
         * For converting from a string to an IntervalsType
         *
         * @param text String to be converted
         * @return The corresponding IntervalsType, or IntervalsType.PERCENTAGE as the default if
         *     text doesn't match a type.
         */
        public static IntervalsType createIntervalsType(String text) {
            for (IntervalsType type : IntervalsType.values()) {
                if (type.toString().equals(text)) {
                    return type;
                }
            }

            // If a bad non-null value was specified then log the error
            if (text != null) logger.error("\"{}\" is not a valid IntervalsType", text);

            // Couldn't match so use default value
            return IntervalsType.PERCENTAGE;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Determines which prediction bucket in the map to use. Want to have each bucket to be for an
     * easily understood value, such as 1 minute. Best way to do this is then have the predictions
     * for that bucket be 45 seconds to 75 seconds so that the indicator for the bucket (1 minute)
     * is in the middle of the range.
     *
     * @param predLength
     * @return
     */
    private static int index(int predLength) {
        return (predLength + PREDICTION_LENGTH_BUCKET_SIZE / 2) / PREDICTION_LENGTH_BUCKET_SIZE;
    }

    /**
     * Puts the data from the query into the map so it can be further processed later.
     *
     * @param predLength
     * @param predAccuracy
     * @param source
     */
    private void addDataToMap(int predLength, int predAccuracy, String source) {
        // Get the prediction buckets for the specified source
        List<List<Integer>> predictionBuckets = map.computeIfAbsent(source, k -> new ArrayList<>());

        // Determine the index of the appropriate prediction bucket
        int predictionBucketIndex = index(predLength);

        while (predictionBuckets.size() < predictionBucketIndex + 1) predictionBuckets.add(new ArrayList<>());
        if (predictionBucketIndex < predictionBuckets.size() && predictionBucketIndex >= 0) {
            List<Integer> predictionAccuracies = predictionBuckets.get(predictionBucketIndex);
            // Add the prediction accuracy to the bucket.
            predictionAccuracies.add(predAccuracy);
        } else {
            // some prediction streams supply predictions in the past -- ignore those
            logger.error(
                    "predictionLength {} has illegal index {} for predAccuracy {} and source {}",
                    predLength,
                    predictionBucketIndex,
                    predAccuracy,
                    source);
        }
    }

    /**
     * Performs the SQL query and puts the resulting data into the map.
     *
     * @param beginDateStr Begin date for date range of data to use.
     * @param numDaysStr How many days to do the query for
     * @param beginTimeStr For specifying time of day between the begin and end date to use data
     *     for. Can thereby specify a date range of a week but then just look at data for particular
     *     time of day, such as 7am to 9am, for those days. Set to null or empty string to use data
     *     for entire day.
     * @param endTimeStr For specifying time of day between the begin and end date to use data for.
     *     Can thereby specify a date range of a week but then just look at data for particular time
     *     of day, such as 7am to 9am, for those days. Set to null or empty string to use data for
     *     entire day.
     * @param routeIds Array of IDs of routes to get data for
     * @param predSource The source of the predictions. Can be null or "" (for all), "Transitime",
     *     or "Other"
     * @param predType Whether predictions are affected by wait stop. Can be "" (for all),
     *     "AffectedByWaitStop", or "NotAffectedByWaitStop".
     * @throws SQLException
     * @throws ParseException
     */
    protected void doQuery(
            String beginDateStr,
            String numDaysStr,
            String beginTimeStr,
            String endTimeStr,
            String[] routeIds,
            String predSource,
            String predType)
            throws SQLException, ParseException {
        // Make sure not trying to get data for too long of a time span since
        // that could bog down the database.
        int numDays = Integer.parseInt(numDaysStr);
        if (numDays > 31) {
            throw new ParseException(
                    "Begin date to end date spans more than a month for endDate="
                            + " startDate="
                            + Time.parseDate(beginDateStr)
                            + " Number of days of "
                            + numDays
                            + " spans more than a month",
                    0);
        }

        java.sql.Time beginTime = null;
        java.sql.Time endTime = null;
        Timestamp beginDate = null;

        try {
            DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd");
            DateFormat df2 = new SimpleDateFormat("MM-dd-yyyy");
            try {
                beginDate = new Timestamp(df1.parse(beginDateStr).getTime());
            } catch (ParseException e1) {
                beginDate = new Timestamp(df2.parse(beginDateStr).getTime());
            }
        } catch (ParseException e) {
            logger.warn("Date is not valid. Please use formats: MM-dd-yyyy or yyyy-MM-dd. {}", e.getMessage());
        }

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
        if (Strings.isNullOrEmpty(beginTimeStr)) beginTimeStr = "00:00:00";
        if (Strings.isNullOrEmpty(endTimeStr)) endTimeStr = "23:59:58";

        try {
            beginTime = new java.sql.Time(timeFormat.parse(beginTimeStr).getTime());
            endTime = new java.sql.Time(timeFormat.parse(endTimeStr).getTime());
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid time format, must be HH:mm", e);
        }

        StringBuilder routeSql = new StringBuilder();
        if (routeIds != null && routeIds.length > 0) {
            boolean first = true;
            for (String routeId : routeIds) {
                if (!Strings.isNullOrEmpty(routeId.trim())) {
                    if (first) {
                        routeSql.append(" AND (");
                        first = false;
                    } else {
                        routeSql.append(" OR ");
                    }
                    routeSql.append("route_id=? OR route_short_name=?");
                }
            }
            if (!first) routeSql.append(")");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT to_char(predicted_time-prediction_read_time, 'SSSS')::integer as predLength, " +
                        "prediction_accuracy_msecs/1000 as predAccuracy, prediction_source as source " +
                        "FROM prediction_accuracy WHERE " +
                        "arrival_departure_time BETWEEN ? AND TIMESTAMP '" + beginDate + "' + INTERVAL '" + numDays + " day' " +
                        "AND arrival_departure_time::time BETWEEN ? AND ? " +
                        "AND predicted_time - prediction_read_time < '00:15:00' "
        );

        sql.append(routeSql);

        if (!Strings.isNullOrEmpty(predType)) {
            if (predType.equals("AffectedByWaitStop")) {
                sql.append(" AND affected_by_wait_stop = true ");
            } else {
                sql.append(" AND affected_by_wait_stop = false ");
            }
        }

        if (!Strings.isNullOrEmpty(predSource)) {
            sql.append(" AND prediction_source = ? ");
        } else sql.append(" AND prediction_source = 'TransitClock' ");

        try (var connection = HibernateUtils
                .getSessionFactory(AgencyConfig.getAgencyId())
                .getSessionFactoryOptions()
                .getServiceRegistry()
                .getService(ConnectionProvider.class)
                .getConnection();
             var statement = connection.prepareStatement(sql.toString())) {

            connection.setReadOnly(true);
            logger.debug("SQL: {}", sql);

            logger.debug("beginDate {} beginDateStr {} numDays {} beginTime {} beginTimeStr {} endTime {} endTimeStr {}",
                    beginDate, beginDateStr, numDays, beginTime, beginTimeStr, endTime, endTimeStr);

            int i = 1;
            statement.setTimestamp(i++, beginDate);
            statement.setTime(i++, beginTime);
            statement.setTime(i++, endTime);

            if (routeIds != null) {
                for (String routeId : routeIds) {
                    if (!Strings.isNullOrEmpty(routeId.trim())) {
                        statement.setString(i++, routeId);
                        statement.setString(i++, routeId);
                    }
                }
            }

            if (!Strings.isNullOrEmpty(predSource)) {
                statement.setString(i++, predSource);
            }

            // Actually execute the query
            ResultSet rs = statement.executeQuery();

            // Process results of query
            while (rs.next()) {
                int predLength = rs.getInt("predLength");
                int predAccuracy = rs.getInt("predAccuracy");
                String sourceResult = rs.getString("source");

                addDataToMap(predLength, predAccuracy, sourceResult);
                logger.debug("predLength={} predAccuracy={} source={}", predLength, predAccuracy, sourceResult);
            }
            rs.close();
        } catch (SQLException e) {
            throw e;
        }
    }
}
