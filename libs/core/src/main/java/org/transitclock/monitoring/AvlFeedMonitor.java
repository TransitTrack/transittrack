/* (C)2023 */
package org.transitclock.monitoring;

import java.util.List;

import org.transitclock.core.avl.AvlProcessor;
import org.transitclock.core.avl.assigner.BlockInfoProvider;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.domain.structs.Block;
import org.transitclock.properties.MonitoringProperties;
import org.transitclock.utils.Time;

import lombok.extern.slf4j.Slf4j;

/**
 * For determining if the AVL feed is up. If not getting data when blocks are active then the AVL
 * feed is considered down. It is important to only expect data when blocks are active because
 * otherwise would always get false positives.
 *
 * @author SkiBu Smith
 */
@Slf4j
public class AvlFeedMonitor extends MonitorBase {
    private final AvlProcessor avlProcessor;
    private final BlockInfoProvider blockInfoProvider;

    public AvlFeedMonitor(String agencyId, DataDbLogger dataDbLogger,
                          AvlProcessor avlProcessor,
                          BlockInfoProvider blockInfoProvider,
                          MonitoringProperties properties) {
        super(agencyId, dataDbLogger, properties);
        this.avlProcessor = avlProcessor;
        this.blockInfoProvider = blockInfoProvider;
    }

    /**
     * Checks GPS time of last AVL report from the AVL feed. If it is recent, as specified by
     * transitclock.monitoring.allowableAvlFeedTimeNoDataSecs, then this method returns 0. If no GPS
     * data or the data is too old then returns age of last AVL report in seconds.
     *
     * @return 0 if have recent valid GPS data or age of last AVL report, in seconds.
     */
    private int avlFeedOutageSecs() {
        // Determine age of AVL report
        long lastAvlReportTime = avlProcessor.lastAvlReportTime();
        long ageOfAvlReport = System.currentTimeMillis() - lastAvlReportTime;
        Double ageOfAvlReportInSecs = (double) (ageOfAvlReport / Time.MS_PER_SEC);

        logger.debug(
                "When monitoring AVL feed last AVL report={}",
                avlProcessor.getLastAvlReport());

        setMessage(
                "Last valid AVL report was "
                        + ageOfAvlReport / Time.MS_PER_SEC
                        + " secs old while allowable age is "
                        + properties.getAllowableNoAvlSecs()
                        + " secs as specified by "
                        + "parameter "
                        + properties.getAllowableNoAvlSecs()
                        + " .",
                ageOfAvlReport / Time.MS_PER_SEC);

        if (ageOfAvlReport > properties.getAllowableNoAvlSecs() * Time.MS_PER_SEC) {
            // last AVL report is too old
            return (int) (ageOfAvlReport / Time.MS_PER_SEC);
        } else {
            // Last AVL report is not too old
            return 0;
        }
    }

    /* (non-Javadoc)
     * @see org.transitclock.monitoring.MonitorBase#triggered()
     */
    @Override
    protected boolean triggered() {
        // Check AVL feed
        int avlFeedOutageSecs = avlFeedOutageSecs();
        return avlFeedOutageSecs != 0;
    }

    /**
     * Returns true if there are no currently active blocks indicating that it doesn't matter if not
     * getting AVL data.
     *
     * @return true if no currently active blocks
     */
    @Override
    protected boolean acceptableEvenIfTriggered() {
        List<Block> activeBlocks = blockInfoProvider.getCurrentlyActiveBlocks();
        if (activeBlocks.isEmpty()) {
            setAcceptableEvenIfTriggeredMessage("No currently active blocks " + "so AVL feed considered to be OK.");
            return true;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.transitclock.monitoring.MonitorBase#type()
     */
    @Override
    protected String type() {
        return "AVL feed";
    }
}
