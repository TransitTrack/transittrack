/* (C)2023 */
package org.transitclock.monitoring;

import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.properties.MonitoringProperties;
import org.transitclock.utils.StringUtils;

/**
 * For monitoring access to database. Examines size of the db logging queue to make sure that writes
 * are not getting backed up.
 *
 * @author SkiBu Smith
 */
public class DatabaseQueueMonitor extends MonitorBase {
    public DatabaseQueueMonitor(String agencyId, DataDbLogger dataDbLogger, MonitoringProperties properties) {
        super(agencyId, dataDbLogger, properties);
    }

    /* (non-Javadoc)
     * @see org.transitclock.monitoring.MonitorBase#triggered()
     */
    @Override
    protected boolean triggered() {
        setMessage(
                "Database queue fraction="
                        + StringUtils.twoDigitFormat(dataDbLogger.queueLevel())
                        + " while max allowed fraction="
                        + StringUtils.twoDigitFormat(properties.getMaxQueueFraction())
                        + ", and items in queue="
                        + dataDbLogger.queueSize()
                        + ".",
                dataDbLogger.queueLevel());

        // Determine the threshold for triggering. If already triggered
        // then lower the threshold by maxQueueFractionGap in order
        // to prevent lots of e-mail being sent out if the value is
        // dithering around maxQueueFraction.
        double threshold = properties.getMaxQueueFraction();
        if (wasTriggered()) threshold -= properties.getMaxQueueFractionGap();

        return dataDbLogger.queueLevel() > threshold;
    }

    /* (non-Javadoc)
     * @see org.transitclock.monitoring.MonitorBase#type()
     */
    @Override
    protected String type() {
        return "Database Queue";
    }
}
