/* (C)2023 */
package org.transitclock.core.avl;

import lombok.extern.slf4j.Slf4j;
import org.transitclock.config.StringConfigValue;
import org.transitclock.config.data.GtfsConfig;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.gtfs.realtime.GtfsRtVehiclePositionsReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.transitclock.config.data.TraccarConfig.OCCUPANCY_SOURCE_URL;
import static org.transitclock.core.avl.OccupancyStatusSource.fetchBusLoads;

/**
 * For reading in feed of GTFS-realtime AVL data. Is used for both realtime feeds and for when
 * reading in a giant batch of data.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class GtfsRealtimeModule extends PollUrlAvlModule {
    public GtfsRealtimeModule(String projectId) {
        super(projectId);
        // GTFS-realtime is already binary so don't want to get compressed
        // version since that would just be a waste.
        useCompression = false;
    }

    /**
     * Reads and processes the data. Called by AvlModule.run(). Reading GTFS-realtime doesn't use
     * InputSteram so overriding getAndProcessData().
     */
    @Override
    protected void getAndProcessData() {
        Map<String, OccupancyStatusSource.BusLoadDto> occupancies = null;
        if (!OCCUPANCY_SOURCE_URL.getValue().isBlank()) occupancies = fetchBusLoads(OCCUPANCY_SOURCE_URL.getValue());

        String[] urls = GtfsConfig.GTFS_REALTIME_URI.getValue().split(",");

        for (String urlStr : urls) {
            try {
                logger.info("Reading {}", urlStr);
                List<AvlReport> avlReports = GtfsRtVehiclePositionsReader.getAvlReports(urlStr);
                for (AvlReport avlReport : avlReports) {
                    var id = avlReport.getVehicleId();
                    if (occupancies != null && occupancies.get(id) != null)
                        getOccupancy(avlReport, occupancies, id);
                    //process...............
                    processAvlReport(avlReport);
                }
                logger.info("Processed {} reports for feed {}", avlReports.size(), urlStr);
            } catch (Exception e) {
                logger.error("Issues processing feed {}", urlStr, e);
            }
        }
    }

    private static void getOccupancy(AvlReport avlReport, Map<String, OccupancyStatusSource.BusLoadDto> occupancies, String id) {
        avlReport.setPassengerCount(occupancies.get(id).getCurrentCount());
        avlReport.setPassengerFullness(occupancies.get(id).getCurrentFullness());
    }

    @Override
    protected Collection<AvlReport> processData(InputStream inputStream) throws Exception {
        return new ArrayList<>(); // we've overridden getAndProcessData so this need not do anything
    }
}
