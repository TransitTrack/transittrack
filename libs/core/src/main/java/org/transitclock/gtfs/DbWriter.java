/* (C)2023 */
package org.transitclock.gtfs;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;

import org.transitclock.domain.repository.AgencyRepository;
import org.transitclock.domain.repository.BlockRepository;
import org.transitclock.domain.repository.CalendarDateRepository;
import org.transitclock.domain.repository.CalendarRepository;
import org.transitclock.domain.repository.FareAttributeRepository;
import org.transitclock.domain.repository.FareRuleRepository;
import org.transitclock.domain.repository.FrequencyRepository;
import org.transitclock.domain.repository.RouteRepository;
import org.transitclock.domain.repository.StopRepository;
import org.transitclock.domain.repository.TransferRepository;
import org.transitclock.domain.repository.TravelTimesForTripRepository;
import org.transitclock.domain.repository.TripPatternRepository;
import org.transitclock.domain.repository.TripRepository;
import org.transitclock.domain.structs.*;
import org.transitclock.utils.IntervalTimer;

/**
 * Writes the GTFS data contained in a GtfsData object to the database.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class DbWriter {
    private static final int BATCH_SIZE = 2_000;
    private final GtfsData gtfsData;
    private int counter = 0;

    public DbWriter(GtfsData gtfsData) {
        this.gtfsData = gtfsData;
    }

    private void writeObject(Session session, Object object) {
        writeObject(session, object, true);
    }

    private void writeObject(Session session, Object object, boolean checkForUpdate) {
        if (checkForUpdate) {
            session.merge(object);
        } else {
            session.persist(object);
        }

        // Since can writing large amount of data should use Hibernate
        // batching to make sure don't run out memory.
        counter++;
        if (counter % BATCH_SIZE == 0) {
            logger.info("flushing with {} % {}", counter, BATCH_SIZE);
            session.flush();
            session.clear();
            logger.info("flushed with {} % {}", counter, BATCH_SIZE);
        }
    }

    /**
     * Goes through the collections in GtfsData and writes the objects to the database.
     *
     * @param configRev
     */
    private void actuallyWriteData(Session session, int configRev, boolean cleanupRevs) {
        if (cleanupRevs) {
            // Get rid of old data. Getting rid of trips, trip patterns, and blocks
            // is a bit complicated. Need to delete them in proper order because
            // of the foreign keys. Because appear to need to use plain SQL
            // to do so successfully (without reading in objects and then
            // deleting them, which takes too much time and memory). Therefore
            // deleting of this data is done here before writing the data.
            logger.info("Deleting old blocks and associated trips from rev {} of " + "database...", configRev);
            BlockRepository.deleteFromRev(session, configRev);

            logger.info("Deleting old trips from rev {} of database...", configRev);
            TripRepository.deleteFromRev(session, configRev);

            logger.info("Deleting old trip patterns from rev {} of database...", configRev);
            TripPatternRepository.deleteFromRev(session, configRev);

            // Get rid of travel times that are associated with the rev being
            // deleted
            logger.info("Deleting old travel times from rev {} of database...", configRev);
            TravelTimesForTripRepository.deleteFromRev(session, configRev);
        }

        // Now write the data to the database.
        // First write the Blocks. This will also write the Trips, TripPatterns,
        // Paths, and TravelTimes since those all have been configured to be
        // cascade=CascadeType.SAVE_UPDATE .
        logger.info(
                "Saving {} blocks (plus associated trips) to database...",
                gtfsData.getBlocks().size());
        int c = 0;
        long startTime = System.currentTimeMillis();
        for (Block block : gtfsData.getBlocks()) {
            logger.info(
                    "Saving block #{} with blockId={} serviceId={} blockId={}",
                    ++c,
                    block.getId(),
                    block.getServiceId(),
                    block.getId());
            writeObject(session, block, false);
            if (c % 1000 == 0) {
                logger.info("wrote {} blocks in {}s", c, (System.currentTimeMillis() - startTime) / 1000);
            }
        }

        logger.info("Saving routes to database...");
        RouteRepository.deleteFromRev(session, configRev);
        for (Route route : gtfsData.getRoutesMap().values()) {
            writeObject(session, route);
        }

        logger.info("Saving stops to database...");
        StopRepository.deleteFromRev(session, configRev);
        for (Stop stop : gtfsData.getStops()) {
            writeObject(session, stop);
        }

        logger.info("Saving agencies to database...");
        AgencyRepository.deleteFromRev(session, configRev);
        for (Agency agency : gtfsData.getAgencies()) {
            writeObject(session, agency);
        }

        logger.info("Saving calendars to database...");
        CalendarRepository.deleteFromRev(session, configRev);
        for (Calendar calendar : gtfsData.getCalendars()) {
            writeObject(session, calendar);
        }

        logger.info("Saving calendar dates to database...");
        CalendarDateRepository.deleteFromRev(session, configRev);
        for (CalendarDate calendarDate : gtfsData.getCalendarDates()) {
            writeObject(session, calendarDate);
        }

        logger.info("Saving fare rules to database...");
        FareRuleRepository.deleteFromRev(session, configRev);
        for (FareRule fareRule : gtfsData.getFareRules()) {
            writeObject(session, fareRule);
        }

        logger.info("Saving fare attributes to database...");
        FareAttributeRepository.deleteFromRev(session, configRev);
        for (FareAttribute fareAttribute : gtfsData.getFareAttributes()) {
            writeObject(session, fareAttribute);
        }

        logger.info("Saving frequencies to database...");
        FrequencyRepository.deleteFromRev(session, configRev);
        for (Frequency frequency : gtfsData.getFrequencies()) {
            writeObject(session, frequency);
        }

        logger.info("Saving transfers to database...");
        TransferRepository.deleteFromRev(session, configRev);
        for (Transfer transfer : gtfsData.getTransfers()) {
            writeObject(session, transfer);
        }

        // Write out the ConfigRevision data
        writeObject(session, gtfsData.getConfigRevision());
    }

    /**
     * Writes the data for the collections that are part of the GtfsData object passed in to the
     * constructor.
     *
     * @param session
     * @param configRev So can delete old data for the rev
     * @throws HibernateException when problem with database
     */
    public void write(Session session, int configRev) throws HibernateException {
        write(session, configRev, true);
    }

    public void write(Session session, int configRev, boolean cleanupRevs) throws HibernateException {
        // For logging how long things take
        IntervalTimer timer = new IntervalTimer();

        // Let user know what is going on
        logger.info("Writing GTFS data to database...");

        Transaction tx = session.beginTransaction();

        // Do the low-level processing
        try {
            actuallyWriteData(session, configRev, cleanupRevs);

            // Done writing data so commit it
            tx.commit();
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Error writing GTFS configuration data to db.", e);

            throw e;
        }

        // Let user know what is going on
        logger.info("Finished writing GTFS data to database . Took {} msec.", timer.elapsedMsec());
    }
}
