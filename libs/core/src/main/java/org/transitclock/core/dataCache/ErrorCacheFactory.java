/* (C)2023 */
package org.transitclock.core.dataCache;

import org.ehcache.CacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.transitclock.core.dataCache.ehcache.KalmanErrorCache;

/**
 * @author Sean Óg Crudden Factory that will provide cache to hold Kalman error values.
 */
@Configuration
public class ErrorCacheFactory {
    @Value("${transitclock.core.cache.errorCacheClass:org.transitclock.core.dataCache.ehcache.KalmanErrorCache}")
    private Class<?> className;

    @Bean
    public ErrorCache errorCache(CacheManager cacheManager) {
        if(className == KalmanErrorCache.class) {
            return new KalmanErrorCache(cacheManager);
        }

        throw new IllegalArgumentException("Unknown class " + className);
    }
}
