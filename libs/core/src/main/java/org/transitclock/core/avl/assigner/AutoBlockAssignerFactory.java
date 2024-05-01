package org.transitclock.core.avl.assigner;

import org.transitclock.core.TravelTimes;
import org.transitclock.core.VehicleStatus;
import org.transitclock.core.avl.time.TemporalMatcher;
import org.transitclock.core.dataCache.VehicleDataCache;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.AutoBlockAssignerProperties;
import org.transitclock.properties.AvlProperties;
import org.transitclock.properties.CoreProperties;

import org.springframework.stereotype.Component;

@Component
public class AutoBlockAssignerFactory {
    private final AutoBlockAssignerProperties autoBlockAssignerProperties;
    private final CoreProperties coreProperties;
    private final AvlProperties avlProperties;
    private final VehicleDataCache vehicleDataCache;
    private final TravelTimes travelTimes;
    private final VehicleStatusManager vehicleStatusManager;
    private final TemporalMatcher temporalMatcher;
    private final DbConfig dbConfig;
    private final BlockInfoProvider blockInfoProvider;

    public AutoBlockAssignerFactory(AutoBlockAssignerProperties autoBlockAssignerProperties,
                                    CoreProperties coreProperties,
                                    AvlProperties avlProperties,
                                    VehicleDataCache vehicleDataCache,
                                    TravelTimes travelTimes,
                                    VehicleStatusManager vehicleStatusManager,
                                    TemporalMatcher temporalMatcher,
                                    DbConfig dbConfig,
                                    BlockInfoProvider blockInfoProvider) {
        this.autoBlockAssignerProperties = autoBlockAssignerProperties;
        this.coreProperties = coreProperties;
        this.avlProperties = avlProperties;
        this.vehicleDataCache = vehicleDataCache;
        this.travelTimes = travelTimes;
        this.vehicleStatusManager = vehicleStatusManager;
        this.temporalMatcher = temporalMatcher;
        this.dbConfig = dbConfig;
        this.blockInfoProvider = blockInfoProvider;
    }

    public AutoBlockAssigner createAssigner(VehicleStatus vehicleStatus) {
        return new AutoBlockAssigner(autoBlockAssignerProperties, coreProperties, avlProperties, vehicleStatus, vehicleDataCache, travelTimes, vehicleStatusManager, temporalMatcher, dbConfig, blockInfoProvider);
    }
}
