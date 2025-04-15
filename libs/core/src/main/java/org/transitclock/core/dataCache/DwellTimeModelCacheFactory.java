/* (C)2023 */
package org.transitclock.core.dataCache;

import org.transitclock.core.dataCache.ehcache.scheduled.DwellTimeModelCache;
import org.transitclock.core.prediction.scheduled.dwell.DwellModel;
import org.transitclock.properties.PredictionProperties;

import org.ehcache.CacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Sean Óg Crudden Factory that will provide cache to hold dwell time model class instances
 *     for each stop.
 */
@Configuration
public class DwellTimeModelCacheFactory {
    @Value("${transitclock.core.cache.dwellTimeModelCache:org.transitclock.core.dataCache.DummyDwellTimeModelCacheImpl}")
    private Class<?> className;


    @Bean
    public DwellTimeModelCacheInterface dwellTimeModelCacheInterface(CacheManager cm,
                                                                     DwellModel dwellModel,
                                                                     StopArrivalDepartureCacheInterface stopArrivalDepartureCacheInterface,
                                                                     PredictionProperties properties) {
        if (className == DwellTimeModelCache.class) {
            return new DwellTimeModelCache(cm, dwellModel, stopArrivalDepartureCacheInterface, properties.getRls());
        }

        return new DummyDwellTimeModelCacheImpl();
    }
}
