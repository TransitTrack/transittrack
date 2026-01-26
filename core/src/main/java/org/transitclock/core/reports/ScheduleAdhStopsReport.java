package org.transitclock.core.reports;

import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.transitclock.Core;
import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.ExportTable;
import org.transitclock.domain.structs.Route;
import org.transitclock.repository.ExportTableRepository;
import org.transitclock.service.contract.RepositoryInterface;
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


@Slf4j
public class ScheduleAdhStopsReport {

    private final List<String[]> fullExport = new ArrayList<>(300000);
    private final RepositoryInterface<ExportTable> exportRepo = new ExportTableRepository();

    private Connection connection;

    private static String getSafeString(ResultSet rs, int columnIndex) {
        try {
            return rs.getString(columnIndex);
        } catch (SQLException e) {
            return "";
        }
    }

    static LocalDate validateParseToLocalDate(String date) {
        DateTimeFormatter currentFormat = DateTimeFormatter.ofPattern("MM-dd-yyyy");
        DateTimeFormatter requiredFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
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

    private long getNumDays(LocalDate date1, LocalDate date2) {
        long numDays = ChronoUnit.DAYS.between(date1, date2);
        if (numDays > 30 || numDays < 0) {
            throw new IllegalArgumentException(String.format("%s - %s: more then 31 days or less then 1.", date1, date2));
        }
        return numDays;
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
        LocalDate date1 = validateParseToLocalDate(beginDate);
        LocalDate date2 = validateParseToLocalDate(endDate);
        // Validate date range
        long numDays = getNumDays(date1, date2);

        exportRepo.save(new ExportTable(new Date(), 2, 2, FILE_NAME));
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("stops-report");
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

                try (var connection = getConnection(agencyId)) {
                    var date = date1;
                    for (var i = -1; i < numDays; i++) {
                        fullExport.add(
                                new String[]{
                                        "- - - - -", date.toString(), "", "", "", "", "", date.toString(), "- - - - -"});

                        fullExport.addAll(getListOfRows(connection,
                                Reports.getSqlForAllStopsSchedAdh(agencyId, date, null, allowableEarly, allowableLate)));

                        date = date.plusDays(1);
                        Thread.sleep(300);
                    }
                } catch (Exception ex) {
                    deleteIfException(FILE_NAME);
                    logger.warn(ex.getMessage());
                    throw new RuntimeException(ex);
                }

                writeToCSVFile(FILE_NAME, HOST, timer);
            }
        }).start();
    }

    private void deleteIfException(String fileName) {
        Session session = HibernateUtils.getSession();
        Transaction tx = session.beginTransaction();
        try {
//          begin transaction
            exportRepo.deleteByName(session, fileName);
            tx.commit();
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            session.close();
        }
    }

    public void createDailyScheduleAdhCSVReportForStops(String agencyId,
                                                        String beginDate,
                                                        String allowableEarly,
                                                        String allowableLate,
                                                        String hostUrl
    ) {
        fullExport.add(new String[]{"category", "time", "stop_name", "route", "trip", "block", "vehicle", "schedule", "difference"});
        IntervalTimer timer = new IntervalTimer();

        final String FILE_NAME = String.format("daily_stops_adh_for_%s.csv", beginDate);
        final String HOST = hostUrl;

        LocalDate date1 = validateParseToLocalDate(beginDate);
        List<Route> routes = Core.getInstance().getDbConfig().getRoutes();
        exportRepo.save(new ExportTable(new Date(), 3, 2, FILE_NAME));

        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("daily-report");
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

                try (var connection = getConnection(agencyId)) {
                    var date = date1;
                    for (Route route : routes) {
                        fullExport.add(
                                new String[]{
                                        "- - - - -", date.toString(), "", route.getName(), "", "", "", date.toString(), "- - - - -"});

                        fullExport.addAll(getListOfRows(connection,
                                Reports.getSqlForAllStopsSchedAdh(agencyId, date, route.getId(), allowableEarly, allowableLate)));

                        Thread.sleep(300);
                    }
                } catch (Exception ex) {
                    deleteIfException(FILE_NAME);
                    logger.warn(ex.getMessage());
                    throw new RuntimeException(ex);
                }

                writeToCSVFile(FILE_NAME, HOST, timer);
            }
        }).start();
    }

    private void writeToCSVFile(String fileName, String host, IntervalTimer timer) {
        Transaction tx = null;
        try (CSVWriter writer = new CSVWriter(new FileWriter("/Users/timur/IdeaProjects/transittrack-main/tmp/csv/" + fileName, StandardCharsets.UTF_8));
             Session session = HibernateUtils.getSession();
        ) {
            writer.writeAll(fullExport);
//          begin transaction
            tx = session.beginTransaction();
            exportRepo.update(session, fileName, (host + fileName).getBytes());
            tx.commit();
            logger.info("Created CSV of {} rows and written to {} in {} sec", fullExport.size(), fileName, timer.elapsedMsec() / 1000);
        } catch (Exception ex) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            logger.warn("Creating report to CSV of name '{}' is failed! Cause: {}", fileName, ex.getMessage());
            deleteIfException(fileName);
            logger.warn(ex.getMessage());
        }
    }

    private List<String[]> getListOfRows(Connection connection, String sql) throws SQLException {
        IntervalTimer timer = new IntervalTimer();
        try (PreparedStatement statement = connection.prepareStatement(sql);
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
        SessionFactory sessionFactory = HibernateUtils.getSessionFactory(agencyId);
        Session session = sessionFactory.openSession();
        session.doWork(conn -> {
            this.connection = conn;
        });

        connection.setReadOnly(true);
        logger.debug("Create connection = {}", connection.getCatalog());
        return connection;
    }
}
