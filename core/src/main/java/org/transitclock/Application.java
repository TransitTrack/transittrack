package org.transitclock;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.transitclock.applications.Core;
import org.transitclock.applications.GtfsFileProcessor;
import org.transitclock.core.dataCache.ehcache.CacheManagerFactory;

import javax.sql.DataSource;

@Slf4j
public class Application {

    private final CommandLineParameters cli;
    private final ApplicationFactory factory;

    public Application(CommandLineParameters cli) {
        this.cli = cli;
        this.factory = DaggerApplicationFactory
                .builder()
                .commandLineParameters(cli)
                .build();
    }

    public void init() throws SchedulerException {
        // Grab the Scheduler instance from the Factory
        StdSchedulerFactory schedulerFactory = new StdSchedulerFactory();
        Scheduler scheduler = schedulerFactory.getScheduler();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (scheduler.isStarted()) scheduler.shutdown();
            } catch (SchedulerException e) {
                throw new RuntimeException(e);
            }
        }));

        scheduler.start();

        DataSource dataSource = factory.dataSource();
    }

    public void loadGtfs() {
        if(cli.shouldLoadGtfs()) {
            GtfsFileProcessor processor = GtfsFileProcessor.createGtfsFileProcessor(cli);
            processor.process();
        }
    }

    public void run() {
        try {
            try {
                Core.populateCaches();
            } catch (Exception e) {
                logger.error("Failed to populate cache.", e);
            }

            // Close cache if shutting down.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("Closing cache.");
                    CacheManagerFactory.getInstance().close();
                    logger.info("Cache closed.");
                } catch (Exception e) {
                    logger.error("Cache close failed...", e);
                }
            }));

            // Initialize the core now
            Core.createCore();

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

}