/* (C)2023 */
package org.transitclock.core.holdingmethod;

import org.transitclock.core.dataCache.HoldingTimeCache;
import org.transitclock.core.dataCache.PredictionDataCache;
import org.transitclock.core.dataCache.StopArrivalDepartureCacheInterface;
import org.transitclock.core.dataCache.VehicleDataCache;
import org.transitclock.core.dataCache.VehicleStatusManager;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.HoldingProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * @author Sean Óg Crudden
 */
@Configuration
public class HoldingTimeGeneratorFactory {
    // The name of the class to instantiate
    @Value("${transitclock.core.holdingTimeGeneratorClass:org.transitclock.core.holdingmethod.DummyHoldingTimeGeneratorImpl}")
    private Class<?> className;

    @Bean
    @Lazy
    public HoldingTimeGenerator holdingTimeGenerator(HoldingProperties holdingProperties,
                                                     PredictionDataCache predictionDataCache,
                                                     StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface,
                                                     DataDbLogger dataDbLogger,
                                                     DbConfig dbConfig,
                                                     VehicleDataCache vehicleDataCache,
                                                     HoldingTimeCache holdingTimeCache,
                                                     VehicleStatusManager vehicleStatusManager) {
        if (className == HoldingTimeGeneratorDefaultImpl.class) {
            return new HoldingTimeGeneratorDefaultImpl(holdingProperties,predictionDataCache, stopArrivalDepartureCacheInterface, dataDbLogger, dbConfig, vehicleDataCache, holdingTimeCache, vehicleStatusManager);
        } else if(className == SimpleHoldingTimeGeneratorImpl.class) {
            return new SimpleHoldingTimeGeneratorImpl(holdingProperties, predictionDataCache, stopArrivalDepartureCacheInterface, dataDbLogger, dbConfig);
        }

        return new DummyHoldingTimeGeneratorImpl();
    }
}
