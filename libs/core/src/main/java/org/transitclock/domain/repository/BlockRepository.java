package org.transitclock.domain.repository;

import java.util.List;

import org.transitclock.domain.structs.Block;
import org.transitclock.domain.structs.Trip;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.CoreProperties;
import org.transitclock.utils.Time;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BlockRepository extends BaseRepository<Block> {
    private static CoreProperties coreProperties;
    // For making sure only lazy load trips collection via one thread
    // at a time.
    @Getter
    public static final Object lazyLoadingSyncObject = new Object();

    public BlockRepository(CoreProperties coreProperties) {
        BlockRepository.coreProperties = coreProperties;
    }

    /**
     * Returns list of Block objects for the specified configRev
     *
     * @param session
     * @param configRev
     * @return List of Block objects
     * @throws HibernateException
     */
    public static List<Block> getBlocks(Session session, int configRev) throws HibernateException {
        try {
            logger.warn("caching blocks....");
            if (coreProperties.isAgressiveBlockLoading()) {
                return getBlocksAgressively(session, configRev);
            }
            return getBlocksPassive(session, configRev);
        } finally {
            logger.warn("caching complete");
        }
    }

    private static List<Block> getBlocksPassive(Session session, int configRev) throws HibernateException {
        var query = session
                .createQuery("FROM Block b WHERE b.configRev = :configRev", Block.class)
                .setParameter("configRev", configRev);
        return query.list();
    }

    private static List<Block> getBlocksAgressively(Session session, int configRev) throws HibernateException {
        var query = session.createQuery("FROM Block b "
                        + "join fetch b.trips t "
                        + "join fetch t.travelTimes "
                        + "join fetch t.tripPattern tp "
                        + "join fetch tp.stopPaths sp "
                        /*+ "join fetch sp.locations "*/
                        // this makes the resultset REALLY big
                        + "WHERE b.configRev = :configRev", Block.class)
                .setParameter("configRev", configRev);
        return query.list();
    }

    /**
     * Deletes rev from the Blocks, Trips, and Block_to_Trip_joinTable
     */
    public static int deleteFromRev(Session session, int configRev) throws HibernateException {
        // In a perfect Hibernate world one would simply call on session.delete()
        // for each block and the block to trip join table and the associated
        // trips would be automatically deleted by using the magic of Hibernate.
        // But this means that would have to read in all the Blocks and sub-objects
        // first, which of course takes lots of time and memory, often causing
        // program to crash due to out of memory issue. And since reading in the
        // Trips is supposed to automatically read in associated travel times
        // we would be reading in data that isn't even needed for deletion since
        // don't want to delete travel times (want to reuse them!). Therefore
        // using the much, much faster solution of direct SQL calls. Can't use
        // HQL on the join table since it is not a regularly defined table.
        //
        // Note: Would be great to see if can actually use HQL and delete the
        // appropriate Blocks and have the join table and the trips table
        // be automatically updated. I doubt this would work but would be
        // interesting to try if had the time.
        int totalRowsUpdated = 0;

        // Delete configRev data from Block_to_Trip_joinTable
        int rowsUpdated = session
                .createNativeQuery("DELETE FROM block_to_trip WHERE block_config_rev=" + configRev, Void.class)
                .executeUpdate();
        logger.info("Deleted {} rows from Block_to_Trip_joinTable for " + "configRev={}", rowsUpdated, configRev);
        totalRowsUpdated += rowsUpdated;

        // Delete configRev data from Trip_ScheduledTimeslist
        rowsUpdated = session
                .createNativeQuery("DELETE FROM trip_scheduled_times_list WHERE trip_config_rev=" + configRev, Void.class)
                .executeUpdate();
        logger.info("Deleted {} rows from Trip_ScheduledTimeslist for configRev={}", rowsUpdated, configRev);
        totalRowsUpdated += rowsUpdated;

        // Delete configRev data from Trips
        rowsUpdated = session
                .createMutationQuery("DELETE FROM Trip WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
        logger.info("Deleted {} rows from Trips for configRev={}", rowsUpdated, configRev);
        totalRowsUpdated += rowsUpdated;

        // Delete configRev data from Blocks
        rowsUpdated = session
                .createMutationQuery("DELETE FROM Block WHERE configRev=:configRev")
                .setParameter("configRev", configRev)
                .executeUpdate();
        logger.info("Deleted {} rows from Blocks for configRev={}", rowsUpdated, configRev);
        totalRowsUpdated += rowsUpdated;

        return totalRowsUpdated;
    }

    /**
     * If the trip is active at the secsInDayForAvlReport then it is added to the tripsThatMatchTime
     * list. Trip is considered active if it is within start time of trip minus
     * CoreConfig.getAllowableEarlyForLayoverSeconds() and within the end time of the trip. No
     * leniency is made for the end time since once a trip is over really don't want to assign
     * vehicle to that trip. Yes, vehicles often run late, but that should only be taken account
     * when matching to already predictable vehicle.
     *
     * @param vehicleId for logging messages
     * @param secsInDayForAvlReport
     * @param trip
     * @param tripsThatMatchTime
     * @return
     */
    public static boolean addTripIfActive(String vehicleId,
                                          int secsInDayForAvlReport,
                                          Trip trip,
                                          List<Trip> tripsThatMatchTime,
                                          DbConfig dbConfig) {
        int startTime = trip.getStartTime();
        int endTime = trip.getEndTime();

        int allowableEarlyTimeSecs = coreProperties.getAllowableEarlyForLayoverSeconds();

        if (secsInDayForAvlReport > startTime - allowableEarlyTimeSecs && secsInDayForAvlReport < endTime) {
            tripsThatMatchTime.add(trip);

            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Determined that for blockId={} that a trip is "
                                + "considered to be active for AVL time. "
                                + "TripId={}, tripIndex={} AVLTime={}, "
                                + "startTime={}, endTime={}, "
                                + "allowableEarlyForLayover={} secs, allowableLate={} secs, "
                                + "vehicleId={}",
                        trip.getBlock(dbConfig).getId(),
                        trip.getId(),
                        trip.getBlock(dbConfig).getTripIndex(trip),
                        Time.timeOfDayStr(secsInDayForAvlReport),
                        Time.timeOfDayStr(trip.getStartTime()),
                        Time.timeOfDayStr(trip.getEndTime()),
                        coreProperties.getAllowableEarlyForLayoverSeconds(),
                        coreProperties.getAllowableLateSeconds(),
                        vehicleId);
            }

            return true;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("block {} is not active for vehicleId {}", trip.getBlock(dbConfig).getId(), vehicleId);
        }

        // Not a match so return false
        return false;
    }
}
