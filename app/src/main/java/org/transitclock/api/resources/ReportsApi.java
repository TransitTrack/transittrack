package org.transitclock.api.resources;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Map;

import org.springframework.boot.context.properties.bind.DefaultValue;

import org.transitclock.api.utils.StandardParameters;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1"+"${transitclock.api.old-version-path}"+"/agency/{agency}")
public interface ReportsApi {
    /**
     * Handles the "tripsWithTravelTimes" command which outputs arrival and departures data for the
     * specified trip by date.
     *
     * @param stdParameters
     * @param date
     *
     * @return
     *
     * @
     */
    @GetMapping(value = "/reports/tripsWithTravelTimes",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(
            summary = "Gets the arrivals and departures data of a trips.",
            description = "Gets the arrivals and departures data of a trips.",
            tags = {"base data", "trips"})
    ResponseEntity<String> getTripsWithTravelTimes(
            StandardParameters stdParameters,
            @Parameter(description = "Begin date(YYYY-MM-DD).") @RequestParam(value = "date") String date);

    @Operation(
            summary = "Returns avl report.",
            description = "Returns avl report.",
            tags = {"report", "vehicle"})

    @GetMapping(value = "/reports/avlReport",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    ResponseEntity<String> getAvlReport(
            StandardParameters stdParameters,
            @Parameter(description = "Vehicle id") @RequestParam(value = "v", required = false) String vehicleId,
            @Parameter(description = "Begin date(MM-DD-YYYY or YYYY-MM-DD") @RequestParam(value = "beginDate") String beginDate,
            @Parameter(description = "Num days.", required = false) @RequestParam(value = "numDays", defaultValue = "1", required = false) int numDays,
            @Parameter(description = "Begin time(HH:MM)", required = false) @RequestParam(value = "beginTime", required = false) String beginTime,
            @Parameter(description = "End time(HH:MM)", required = false) @RequestParam(value = "endTime", required = false) String endTime,
            @Parameter(description = "For Screenshot", required = false) @RequestParam(value = "screenShot") boolean isForScreenShot);

    /**
     * Handles the "tripWithTravelTimes" command which outputs arrival and departures data for the
     * specified trip by date.
     *
     * @param stdParameters
     * @param tripId
     * @param date
     *
     * @return
     */
    @GetMapping(value = "/reports/tripWithTravelTimes",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(
            summary = "Gets the arrivals and departures data of a trip.",
            description = "Gets the arrivals and departures data of a trip.",
            tags = {"base data", "trip"})
    ResponseEntity<String> getTripWithTravelTimes(
            StandardParameters stdParameters,
            @Parameter(description = "Trip id", required = true) @RequestParam(value = "tripId") String tripId,
            @Parameter(description = "Begin date(YYYY-MM-DD).", required = true) @RequestParam(value = "date") String date);

    /**
     * Handles the "trips" report which outputs trips by date which contains arrival and departures
     * data.
     *
     * @param stdParameters
     * @param date
     *
     * @return
     *
     * @
     */
    @GetMapping(value = "/reports/tripsByDate",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(
            summary = "Gets the trips by date.",
            description = "Gets the trips by date.",
            tags = {"base data", "trip"})
    ResponseEntity<String> getTrips(
            StandardParameters stdParameters,
            @Parameter(description = "Date(YYYY-MM-DD).", required = true)
            @RequestParam(value = "date") String date);

    @Operation(
            summary = "Returns schedule adherence report.",
            description = "Returns schedule adherence report.",
            tags = {"report", "route", "schedule adherence"})
    @GetMapping(value = "/reports/scheduleAdh",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    ResponseEntity<String> scheduleAdhReport(
            StandardParameters stdParameters,
            @Parameter(description = "Route id") @RequestParam(value = "r") String routeId,
            @Parameter(description = "Begin date(MM-DD-YYYY or YYYY-MM-DD") @RequestParam(value = "beginDate") String beginDate,
            @Parameter(description = "Num days.", required = false) @RequestParam(value = "numDays", defaultValue = "1", required = false) int numDays,
            @Parameter(description = "Begin time(HH:MM)") @RequestParam(value = "beginTime") String beginTime,
            @Parameter(description = "End time(HH:MM)") @RequestParam(value = "endTime") String endTime,
            @Parameter(description = "Allowable early in mins(default 1.0)", required = false)
            @RequestParam(value = "allowableEarly", required = false, defaultValue = "1.0") String allowableEarly,
            @Parameter(description = "Allowable late in mins(default 4.0", required = false)
            @RequestParam(value = "allowableLate", required = false, defaultValue = "4.0") String allowableLate);

    @Operation(
            summary = "Returns schedule adherence report for single stop.",
            description = "Returns schedule adherence report for single stop.",
            tags = {"report", "stop", "schedule adherence"})
    @GetMapping(value = "/reports/schedStopAdh",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    ResponseEntity<String> reportForStopById(
            StandardParameters stdParameters,
            @Parameter(description = "Stop id") @RequestParam(value = "id") String stopId,
            @Parameter(description = "Begin date(MM-DD-YYYY or YYYY-MM-DD") @RequestParam(value = "beginDate") String beginDate,
            @Parameter(description = "Num days.") @RequestParam(value = "numDays", defaultValue = "1", required = false) int numDays,
            @Parameter(description = "Begin time(HH:MM)") @RequestParam(value = "beginTime", required = false) String beginTime,
            @Parameter(description = "End time(HH:MM)") @RequestParam(value = "endTime", required = false) String endTime,
            @Parameter(description = "Allowable early in mins(default 1.0)")
            @RequestParam(value = "allowableEarly", required = false, defaultValue = "1.0") String allowableEarly,
            @Parameter(description = "Allowable late in mins(default 4.0")
            @RequestParam(value = "allowableLate", required = false, defaultValue = "4.0") String allowableLate);

    @Operation(
            summary = "Returns on-time performance report.",
            description = "Returns on-time performance report as summary for all routes.",
            tags = {"report", "schedule adherence"})
    @GetMapping(value ="/reports/avgSpeedByRoute",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    ResponseEntity<String> avgSpeedPerRoute( StandardParameters stdParameters,
            @Parameter(description = "Begin date(MM-DD-YYYY or YYYY-MM-DD)", required = true) @RequestParam (value = "beginDate") String beginDate,
            @Parameter(description = "End date(MM-DD-YYYY or YYYY-MM-DD)", required = true) @RequestParam (value = "endDate") String endDate,
            @Parameter(description = "Route short name") @RequestParam String routeName,
            @Parameter(description = "Route ID") @RequestParam String routeId,
            @Parameter(description = "Direction ID '0' or '1'") @DefaultValue("0") @RequestParam (value = "dir") String directionId,
            @Parameter(description = "Begin time(HH:MM)") @RequestParam(value = "beginTime") String beginTime,
            @Parameter(description = "End time(HH:MM)") @RequestParam(value = "endTime") String endTime);

    @Operation(
            summary = "Returns on-time performance report.",
            description = "Returns on-time performance report is partitioned by chosen options.",
            tags = {"report", "schedule adherence"})
    @GetMapping(value = "/reports/onTimePerformance",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    ResponseEntity<String> onTimePerformance(StandardParameters stdParameters,
            @Parameter(description = "Begin date(MM-DD-YYYY or YYYY-MM-DD)") @RequestParam (value = "beginDate") String beginDate,
            @Parameter(description = "End date(MM-DD-YYYY or YYYY-MM-DD)") @RequestParam (value = "endDate") String endDate,
            @Parameter(description = "Partition accuracies: 'day', 'week', 'month'", required = true) @DefaultValue("day") @RequestParam (value = "accuracy") String accuracy,
            @Parameter(description = "Allowable early in mins(default 1.0)") @RequestParam (value = "allowableEarly") String allowableEarly,
            @Parameter(description = "Allowable late in mins(default 3.0)") @RequestParam (value = "allowableLate") String allowableLate);

    @Operation(
            summary = "Returns on-time performance report.",
            description = "Returns on-time performance report as summary for all routes.",
            tags = {"report", "schedule adherence"})
    @GetMapping(value = "/reports/onTimePerformanceAllRoutes",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    ResponseEntity<String> onTimePerformanceAllRoutes( StandardParameters stdParameters,
            @Parameter(description = "Begin date(MM-DD-YYYY or YYYY-MM-DD)") @RequestParam (value = "beginDate") String beginDate,
            @Parameter(description = "End date(MM-DD-YYYY or YYYY-MM-DD)") @RequestParam (value = "endDate") String endDate,
            @Parameter(description = "Allowable early in mins(default 1.0)") @RequestParam (value = "allowableEarly") String allowableEarly,
            @Parameter(description = "Allowable late in mins(default 3.0)") @RequestParam (value = "allowableLate") String allowableLate);

    @Operation(
            summary = "Gets occupancies by date and specific trip.",
            description = "Gets occupancies by date and trip with addition results if count of passengers changes between stops.",
            tags = {"report", "trip"})
    @GetMapping(value = "/reports/occupanciesByTripWithContinuePickUp",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    ResponseEntity<String> getMaxOccupancyByRouteDate(StandardParameters stdParameters,
            @Parameter(description = "Date(YYYY-MM-DD or YYYY-MM-DD).", required = true)
            @RequestParam(value = "date") String date,
            @Parameter(description = "Specific trip ID.")
            @RequestParam(value = "tripId") String tripId);

    @Operation(
            summary = "Gets max sum occupancies by specific route for single day.",
            description = "Gets max sum of occupancies by single day and route if setting ID or short name.",
            tags = {"report", "route"})
    @GetMapping(value = "/reports/maxOccupanciesByRoute",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    ResponseEntity<String> getMaxOccupancyByTripDate(StandardParameters stdParameters,
            @Parameter(description = "Date(YYYY-MM-DD or YYYY-MM-DD).", required = true)
            @RequestParam(value = "date") String date,
            @Parameter(description = "Specific rout ID.")
            @RequestParam(value = "routeId") String routeId,
            @Parameter(description = "Route short name.")
            @RequestParam(value = "routeName") String routeShortName);

    @GetMapping(value = "/reports/lastAvlJsonData",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    @Operation(
            summary = "Returns AVL Json data for last 24 hours.",
            description = "Returns AVL Json data for last 24 hours.",
            tags = {"report", "avl", "vehicle"})
    ResponseEntity<String> getLastAvlJsonData(StandardParameters stdParameters);

    @GetMapping(value = "/reports/predAccuracyIntervalsData.jsp")
    ResponseEntity<String> predAccuracyIntervalsData(HttpServletRequest request) throws SQLException, ParseException;

    @GetMapping(value = "/reports/predAccuracyRangeData.jsp")
    ResponseEntity<String> predAccuracyRangeData(HttpServletRequest request) throws SQLException, ParseException;

    @GetMapping(value = "/reports/data/summaryScheduleAdherence.jsp")
    ResponseEntity<Map<String, String>> summaryScheduleAdherence(HttpServletRequest request) throws ParseException;

    @GetMapping(value = "/reports/predAccuracyScatterData.jsp")
    ResponseEntity<String> predAccuracyScatterData(HttpServletRequest request) throws ParseException, SQLException;
}
