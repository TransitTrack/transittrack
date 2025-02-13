package org.transitclock.core.reports;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.utils.IntervalTimer;

import java.io.FileWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.transitclock.domain.structs.ExportTable.updateStatus;

@Slf4j
public class ScheduleAdhStopsReport {

    private final static DateTimeFormatter currentFormat = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private final static DateTimeFormatter requiredFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final List<String[]> fullExport = new ArrayList<>(300000);

    private static String getSafeString(ResultSet rs, int columnIndex) {
        try {
            return rs.getString(columnIndex);
        } catch (SQLException e) {
            return "";
        }
    }

    private static LocalDate validate(String date) {
        try {
            if (date.charAt(4) != '-') {
                return LocalDate.parse(date, currentFormat);
            } else {
                return LocalDate.parse(date, requiredFormat);
            }
        } catch (DateTimeParseException e) {
            logger.warn("Exception happened while processing parse date: ", e);
            throw new IllegalArgumentException("Invalid date: " + date);
        }
    }

    public void createScheduleAdhCSVReportForStops(String agencyId,
                                                   String beginDate,
                                                   int numDays,
                                                   String allowableEarly,
                                                   String allowableLate,
                                                   String fileName,
                                                   String hostUrl
    ) {
        fullExport.add(new String[]{"category", "time", "stop_name", "route", "trip", "block", "vehicle", "schedule", "difference"});
        IntervalTimer timer = new IntervalTimer();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("csv-report");
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

                try (var connection = getConnection(agencyId)) {
                    LocalDate date = validate(beginDate);

                    for (var i = 1; i < numDays; i++) {
                        fullExport.addAll(getListOfRows(connection,
                                Reports.getSqlForAllStopsSchedAdh(agencyId, date, allowableEarly, allowableLate)));

                        date = date.plusDays(1);
                        Thread.sleep(300);
                        fullExport.add(
                                new String[]{
                                        "- - - - -", date.toString(), "", "", "", "", "", date.toString(), "- - - - -"});
                    }
                } catch (Exception ex) {
                    logger.warn(ex.getMessage());
                }

                try (CSVWriter writer = new CSVWriter(new FileWriter("/Users/timur/Desktop/csv/" + fileName));
                     Session session = HibernateUtils.getSession();
                ) {
                    writer.writeAll(fullExport);
                    updateStatus(session, fileName, hostUrl+fileName);
                    logger.info("Created CSV of {} rows and written to {} in {} sec", fullExport.size(), fileName, timer.elapsedMsec() / 1000);
                } catch (Exception e) {
                    logger.warn(e.getMessage());
                }
            }
        }).start();
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
            logger.info("Query took {} msec, rows={}", timer.elapsedMsec(), rows.size());
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
