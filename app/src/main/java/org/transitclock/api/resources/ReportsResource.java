package org.transitclock.api.resources;

import com.google.common.base.Strings;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;

import org.transitclock.api.reports.ChartGenericJsonQuery;
import org.transitclock.api.reports.PredAccuracyIntervalQuery;
import org.transitclock.api.reports.PredAccuracyRangeQuery;
import org.transitclock.api.reports.PredictionAccuracyQuery.IntervalsType;
import org.transitclock.api.reports.ScheduleAdherenceController;
import org.transitclock.api.utils.StandardParameters;
import org.transitclock.api.utils.WebUtils;
import org.transitclock.core.reports.Reports;
import org.transitclock.core.reports.SqlUtils;
import org.transitclock.utils.Time;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportsResource extends BaseApiResource implements ReportsApi {

    @Autowired
    ScheduleAdherenceController scheduleAdherenceController;

    @Override
    public ResponseEntity<String> getTripsWithTravelTimes(
            StandardParameters stdParameters,
            String date) {
        try {

            String response = Reports.getTripsWithTravelTimes(stdParameters.getAgencyId(), date);
            return stdParameters.createResponse(response);
        } catch (Exception e) {
            throw WebUtils.badRequestException(e);
        }
    }

    @Override
    public ResponseEntity<String> getAvlReport(
            StandardParameters stdParameters,
            String vehicleId,
            String beginDate,
            int numDays,
            String beginTime,
            String endTime) {
        String response = Reports.getAvlJson(
                stdParameters.getAgencyId(),
                vehicleId, beginDate, String.valueOf(numDays), beginTime, endTime);
        return ResponseEntity.ok(response);
    }


    @Override
    public ResponseEntity<String> getTripWithTravelTimes(
            StandardParameters stdParameters,
            String tripId,
            String date) {

        String response = Reports.getTripWithTravelTimes(stdParameters.getAgencyId(), tripId, date);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<String> getTrips(
            StandardParameters stdParameters,
            String date) {

        String response = Reports.getTripsFromArrivalAndDeparturesByDate(stdParameters.getAgencyId(), date);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<String> scheduleAdhReport(
            StandardParameters stdParameters,
            String routeId,
            String beginDate,
            int numDays,
            String beginTime,
            String endTime,
            String allowableEarly,
            String allowableLate) {
        String response = Reports.getScheduleAdhByStops(
                stdParameters.getAgencyId(),
                routeId,
                beginDate,
                allowableEarly,
                allowableLate,
                beginTime,
                endTime,
                numDays);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<String> reportForStopById(
            StandardParameters stdParameters,
            String stopId,
            String beginDate,
            int numDays,
            String beginTime,
            String endTime,
            String allowableEarly,
            String allowableLate) {
        String response = Reports.getReportForStopById(
                stdParameters.getAgencyId(),
                stopId,
                beginDate,
                allowableEarly,
                allowableLate,
                beginTime,
                endTime,
                numDays);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<String> getLastAvlJsonData(StandardParameters stdParameters) {
        String response = Reports.getLastAvlJson(stdParameters.getAgencyId());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<String> predAccuracyIntervalsData(HttpServletRequest request) throws SQLException, ParseException {
        // Get params from the query string
        String agencyId = request.getParameter("a");
        String beginDate = request.getParameter("beginDate");
        String numDays = request.getParameter("numDays");
        String beginTime = request.getParameter("beginTime");
        String endTime = request.getParameter("endTime");

        String[] routeIds = request.getParameterValues("r");
        // source can be "" (for all), "Transitime", or "Other";
        String source = request.getParameter("source");

        String predictionType = request.getParameter("predictionType");

        IntervalsType intervalsType = IntervalsType
                .createIntervalsType(request.getParameter("intervalsType"));

        double intervalPercentage1 = 0.68; // Default value
        String intervalPercentage1Str = request.getParameter("intervalPercentage1");
        try {
            if (intervalPercentage1Str != null && !intervalPercentage1Str.isEmpty())
                intervalPercentage1 = Double.parseDouble(intervalPercentage1Str);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .body("Could not parse Interval Percentage 1 of " + intervalPercentage1Str);
        }

        double intervalPercentage2 = Double.NaN; // Default value
        String intervalPercentage2Str = request.getParameter("intervalPercentage2");
        try {
            if (intervalPercentage2Str != null && !intervalPercentage2Str.isEmpty())
                intervalPercentage2 = Double.parseDouble(intervalPercentage2Str);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .body("Could not parse Interval Percentage 2 of " + intervalPercentage2Str);
        }

        if (agencyId == null || beginDate == null || numDays == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("For predAccuracyIntervalsData.jsp must "
                                  + "specify parameters 'a' (agencyId), 'beginDate', "
                                  + "and 'numDays'.");
        }

        // Perform the query and convert results of query to a JSON string
        PredAccuracyIntervalQuery query = new PredAccuracyIntervalQuery(agencyId);
        String jsonString = query
                .getJson(beginDate, numDays, beginTime, endTime,
                         routeIds, source, predictionType,
                         intervalsType, intervalPercentage1,
                         intervalPercentage2);

        // If no data then return error status with an error message
        if (jsonString == null || jsonString.isEmpty()) {
            String message = "No data for beginDate=" + beginDate
                    + " numDays=" + numDays
                    + " beginTime=" + beginTime
                    + " endTime=" + endTime
                    + " routeIds=" + Arrays.asList(routeIds)
                    + " source=" + source
                    + " predictionType=" + predictionType
                    + " intervalsType=" + intervalsType;
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .body(message);
        }

        // Respond with the JSON string
        return ResponseEntity.status(HttpStatus.OK)
                .body(jsonString);
    }

    @Override
    public ResponseEntity<String> predAccuracyRangeData(HttpServletRequest request) throws SQLException, ParseException {
        // Get params from the query string
        String agencyId = request.getParameter("a");

        String beginDate = request.getParameter("beginDate");
        String numDays = request.getParameter("numDays");
        String beginTime = request.getParameter("beginTime");
        String endTime = request.getParameter("endTime");

        String[] routeIds = request.getParameterValues("r");
        // source can be "" (for all), "Transitime", or "Other";
        String source = request.getParameter("source");

        String predictionType = request.getParameter("predictionType");

        int allowableEarlySec = Time.SEC_PER_MIN; // Default value
        String allowableEarlyStr = request.getParameter("allowableEarly");
        try {
            if (allowableEarlyStr != null && !allowableEarlyStr.isEmpty()) {
                allowableEarlySec = (int) Double.parseDouble(allowableEarlyStr) * Time.SEC_PER_MIN;
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .body("Could not parse Allowable Early value of " + allowableEarlyStr);
        }

        int allowableLateSec = (int) 4.0 * Time.SEC_PER_MIN; // Default value
        String allowableLateStr = request.getParameter("allowableLate");
        try {
            if (allowableLateStr != null && !allowableLateStr.isEmpty()) {
                allowableLateSec = (int) Double.parseDouble(allowableLateStr) * Time.SEC_PER_MIN;
            }
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .body("Could not parse Allowable Late value of " + allowableLateStr);
        }

        if (agencyId == null || beginDate == null || numDays == null) {
            return ResponseEntity.badRequest()
                    .body("For predAccuracyRangeData.jsp must specify parameters 'a' (agencyId), 'beginDate', and 'numDays'.");
        }

        // Make sure not trying to get data for too long of a time span since
        // that could bog down the database.
        if (Integer.parseInt(numDays) > 31) {
            throw new ParseException("Number of days of " + numDays + " spans more than a month", 0);
        }

        // Perform the query.
        PredAccuracyRangeQuery query = new PredAccuracyRangeQuery(agencyId);

        // Convert results of query to a JSON string
        String jsonString = query.getJson(beginDate, numDays, beginTime, endTime,
                                          routeIds, source, predictionType,
                                          allowableEarlySec, allowableLateSec);

        // If no data then return error status with an error message
        if (jsonString == null || jsonString.isEmpty()) {
            String message = "No data for beginDate=" + beginDate
                    + " numDays=" + numDays
                    + " beginTime=" + beginTime
                    + " endTime=" + endTime
                    + " routeIds=" + Arrays.asList(routeIds)
                    + " source=" + source
                    + " allowableEarlyMsec=" + allowableEarlySec
                    + " allowableLateMsec=" + allowableLateSec;
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .body(message);
        }

        return ResponseEntity.ok(jsonString);
    }

    @Override
    public ResponseEntity<Map<String, String>> summaryScheduleAdherence(HttpServletRequest request) throws ParseException {
        String startDateStr = request.getParameter("beginDate");
        String numDaysStr = request.getParameter("numDays");
        String startTime = request.getParameter("beginTime");
        String endTime = request.getParameter("endTime");
        String earlyLimitStr = request.getParameter("allowableEarly");
        String lateLimitStr = request.getParameter("allowableLate");
        double earlyLimit = -60.0;
        double lateLimit = 60.0;

        if (!StringUtils.hasText(startTime)) {
            startTime = "00:00:00";
        } else if (startTime.length() < 6) {
            startTime += ":00";
        }

        if (!StringUtils.hasText(endTime)) {
            endTime = "23:59:59";
        } else if (endTime.length() < 6) {
            endTime += ":00";
        }

        if (StringUtils.hasText(earlyLimitStr)) {
            earlyLimit = Double.parseDouble(earlyLimitStr) * -60;
        }
        if (StringUtils.hasText(lateLimitStr)) {
            lateLimit = Double.parseDouble(lateLimitStr) * 60;
        }

        String routeIdList = request.getParameter("r");
        List<String> routeIds = routeIdList == null ? null : Arrays.asList(routeIdList.split(","));

        Date beginDate;
        try {
            DateFormat defaultDateFormat = new SimpleDateFormat("MM-dd-yyyy");
            DateFormat altDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            beginDate = (startDateStr.charAt(4) != '-') ? defaultDateFormat.parse(startDateStr) : altDateFormat.parse(startDateStr);
        } catch (ParseException e) {
            throw e;
        }

        Map<String, String> results = scheduleAdherenceController.routeScheduleAdherenceSummary(beginDate,
                                                                                                Integer.parseInt(numDaysStr),
                                                                                                startTime, endTime,
                                                                                                earlyLimit, lateLimit,
                                                                                                routeIds);
        return ResponseEntity.ok(results);
    }

    @Override
    public ResponseEntity<String> predAccuracyScatterData(HttpServletRequest request) throws ParseException, SQLException {
        String agencyId = request.getParameter("a");
        String beginDate = request.getParameter("beginDate");
        String numDays = request.getParameter("numDays");
        String beginTime = request.getParameter("beginTime");
        String endTime = request.getParameter("endTime");
        String routeId = request.getParameter("r");
        String source = request.getParameter("source");
        String predictionType = request.getParameter("predictionType");

        boolean showTooltips = true;
        String showTooltipsStr = request.getParameter("tooltips");
        if (showTooltipsStr != null && showTooltipsStr.equalsIgnoreCase("false"))
            showTooltips = false;

        if (agencyId == null || beginDate == null || numDays == null) {
            return ResponseEntity.badRequest()
                    .body("For predAccuracyScatterData.jsp must specify parameters 'a' (agencyId), 'beginDate', and 'numDays'.");
        }

        // Make sure not trying to get data for too long of a time span since
        // that could bog down the database.
        if (Integer.parseInt(numDays) > 31) {
            throw new ParseException("Number of days of " + numDays + " spans more than a month", 0);
        }

        // Determine route portion of SQL. Default is to provide info for all routes.
        String routeSql = "";
        if (routeId != null && !routeId.trim().isEmpty()) {
            routeSql = "  AND route_id='" + routeId + "' ";
        }

        // Determine the source portion of the SQL. Default is to provide predictions for all sources
        String sourceSql = "";
        if (!Strings.isNullOrEmpty(source)) {
            if (source.equals("Transitime")) {
                // Only "Transitime" predictions
                sourceSql = " AND prediction_source = 'Transitime'";
            } else {
                // Anything but "Transitime"
                sourceSql = " AND prediction_source <> 'Transitime'";
            }
        }

        // Determine SQL for prediction type
        String predTypeSql = "";
        if (!Strings.isNullOrEmpty(predictionType)) {
            if (Objects.equals(source, "AffectedByWaitStop")) {
                // Only "AffectedByLayover" predictions
                predTypeSql = " AND affected_by_wait_stop = true ";
            } else {
                // Only "NotAffectedByLayover" predictions
                predTypeSql = " AND affected_by_wait_stop = false ";
            }
        }

        String tooltipsSql = "";
        if (showTooltips)
            tooltipsSql =
                    ", format(E'predAccuracy= %s\\n"
                            + "prediction=%s\\n"
                            + "stopId=%s\\n"
                            + "routeId=%s\\n"
                            + "tripId=%s\\n"
                            + "arrDepTime=%s\\n"
                            + "predTime=%s\\n"
                            + "predReadTime=%s\\n"
                            + "vehicleId=%s\\n"
                            + "source=%s\\n"
                            + "affectedByLayover=%s', "
                            + "   CAST(prediction_accuracy_msecs || ' msec' AS INTERVAL), predicted_time-prediction_read_time,"
                            + "   stop_id, route_id, trip_id, "
                            + "   to_char(arrival_departure_time, 'HH24:MI:SS.MS MM/DD/YYYY'),"
                            + "   to_char(predicted_time, 'HH24:MI:SS.MS'),"
                            + "   to_char(prediction_read_time, 'HH24:MI:SS.MS'),"
                            + "   vehicle_id,"
                            + "   prediction_Source,"
                            + "   CASE WHEN affected_by_wait_stop THEN 'True' ELSE 'False' END) AS tooltip ";

        String predLengthSql = "     to_char(predicted_time-prediction_read_time, 'SSSS')::integer ";
        String predAccuracySql = "     prediction_accuracy_msecs/1000 as predAccuracy ";

        // Filter out MBTA_seconds source since it is isn't significantly different from MBTA_epoch.
        // TODO should clean this up by not having MBTA_seconds source at all
        // in the prediction accuracy module for MBTA.

        String sql = "SELECT %s as predLength,%s%s FROM prediction_accuracy WHERE 1=1 %s  AND %s < 900 %s%s%s  AND prediction_source <> 'MBTA_seconds'".formatted(
                predLengthSql,
                predAccuracySql,
                tooltipsSql,
                SqlUtils.timeRangeClause(request, "arrival_departure_time", 30),
                predLengthSql,
                routeSql,
                sourceSql,
                predTypeSql);

        // Determine the json data by running the query
        String jsonString = ChartGenericJsonQuery.getJsonString(agencyId, sql);


        // If no data then return error status with an error message
        if (jsonString == null || jsonString.isEmpty()) {
            String message = "No data for beginDate=" + beginDate
                    + " numDays=" + numDays
                    + " beginTime=" + beginTime
                    + " endTime=" + endTime
                    + " routeId=" + routeId
                    + " source=" + source
                    + " predictionType=" + predictionType;
            return ResponseEntity.badRequest().body(message);
        }

        // Return the JSON data
        return ResponseEntity.ok(jsonString);
    }
}
