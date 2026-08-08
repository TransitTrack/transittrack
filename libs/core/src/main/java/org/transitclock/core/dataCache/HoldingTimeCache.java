/* (C)2023 */
package org.transitclock.core.dataCache;

import java.util.List;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.springframework.stereotype.Component;

import org.transitclock.domain.structs.HoldingTime;

/**
 * @author Sean Óg Crudden
 */
@Component
public class HoldingTimeCache {
    private final Cache<HoldingTimeCacheKey, HoldingTime> cache;

    public HoldingTimeCache(CacheManager cm) {
        cache = cm.getCache("HoldingTimeCache", HoldingTimeCacheKey.class, HoldingTime.class);
    }

    public void putHoldingTime(HoldingTime holdingTime) {
        HoldingTimeCacheKey key = new HoldingTimeCacheKey(holdingTime);

        cache.put(key, holdingTime);
    }

    public HoldingTime getHoldingTime(HoldingTimeCacheKey key) {
        return cache.get(key);
    }

    public List<HoldingTimeCacheKey> getKeys() {
        // TODO Auto-generated method stub
        return null;
    }
}
