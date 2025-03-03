package org.transitclock.core.reports;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.ExportTable;
import org.transitclock.utils.IntervalTimer;

import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static org.transitclock.domain.structs.ExportTable.deleteExportTableRecord;
import static org.transitclock.domain.structs.ExportTable.updateStatus;

@Slf4j
public class ScheduleAdhStopsReport {

    private final DateTimeFormatter currentFormat = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private final DateTimeFormatter requiredFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final List<String[]> fullExport = new ArrayList<>(300000);

    private static String getSafeString(ResultSet rs, int columnIndex) {
        try {
            return rs.getString(columnIndex);
        } catch (SQLException e) {
            return "";
        }
    }

    public void createScheduleAdhCSVReportForStops(String agencyId,
                                                   String beginDate,
                                                   String endDate,
                                                   String allowableEarly,
                                                   String allowableLate,
                                                   String hostUrl
    ) {
        fullExport.add(new String[]{"category", "time", "stop_name", "route", "trip", "block", "vehicle", "schedule", "difference"});
        IntervalTimer timer = new IntervalTimer();

        final String FILE_NAME = String.format("stops_adh_%s_%s.csv", beginDate, endDate);
        final String HOST = hostUrl;

        // Validate date
        LocalDate date1 = validate(beginDate);
        LocalDate date2 = validate(endDate);
        long numDays = ChronoUnit.DAYS.between(date1, date2);
        // Validate date range
        if (numDays > 30 || numDays < 0) {
            throw new IllegalArgumentException(beginDate + " - " + endDate + ": more then 31 days or less then 1.");
        }

        ExportTable.create(new ExportTable(new Date(), 2, 2, FILE_NAME));
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("csv-report");
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

                try (var connection = getConnection(agencyId)) {
                    var date = date1;
                    for (var i = -1; i < numDays; i++) {
                        fullExport.add(
                                new String[]{
                                        "- - - - -", date.toString(), "", "", "", "", "", date.toString(), "- - - - -"});

                        fullExport.addAll(getListOfRows(connection,
                                Reports.getSqlForAllStopsSchedAdh(agencyId, date, allowableEarly, allowableLate)));

                        date = date.plusDays(1);
                        Thread.sleep(300);
                    }
                } catch (Exception ex)
                {
                    Session session = HibernateUtils.getSession();
                    deleteExportTableRecord(FILE_NAME, session);
                    session.close();
                    logger.warn(ex.getMessage());
                    throw new RuntimeException(ex);
                }

                try (CSVWriter writer = new CSVWriter(new FileWriter("/tmp/csv/" + FILE_NAME, StandardCharsets.UTF_8));
                     Session session = HibernateUtils.getSession();
                ) {
                    writer.writeAll(fullExport);
                    updateStatus(session, FILE_NAME, HOST + FILE_NAME);
                    logger.info("Created CSV of {} rows and written to {} in {} sec", fullExport.size(), FILE_NAME, timer.elapsedMsec() / 1000);
                } catch (Exception ex)
                {
                    Session session = HibernateUtils.getSession();
                    deleteExportTableRecord(FILE_NAME, session);
                    session.close();
                    logger.warn(ex.getMessage());
                }
            }
        }).start();
    }

    private LocalDate validate(String date) {
        try {
            if (date.charAt(4) != '-') {
                return LocalDate.parse(date, currentFormat);
            } else {
                return LocalDate.parse(date, requiredFormat);
            }
        } catch (DateTimeParseException e) {
            logger.debug("Exception happened while processing parse date: ", e);
            throw new IllegalArgumentException("Invalid date: " + date);
        }
    }

    private List<String[]> getListOfRows(Connection connection, String sql) throws SQLException {
        IntervalTimer timer = new IntervalTimer();
        try (
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<String[]> rows = new ArrayList<>();

            while (rs.next()) {
                String[] row = IntStream.range(1, columnCount + 1)
                        .mapToObj(i -> getSafeString(rs, i))
                        .toArray(String[]::new);
                rows.add(row);
            }
            logger.debug("Query took {} msec, rows={}", timer.elapsedMsec(), rows.size());
            return rows;
        } catch (SQLException ex) {
            logger.error("SQL Exception: {}", ex.getMessage());
            throw ex;
        }
    }

    private Connection getConnection(String agencyId) throws SQLException {
        var connection = HibernateUtils.getSessionFactory(agencyId)
                .getSessionFactoryOptions()
                .getServiceRegistry()
                .getService(ConnectionProvider.class)
                .getConnection();
        connection.setReadOnly(true);
        logger.debug("Create connection = {}", connection.getCatalog());
        return connection;
    }
}
