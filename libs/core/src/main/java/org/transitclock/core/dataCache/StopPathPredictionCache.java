/* (C)2023 */
package org.transitclock.core.dataCache;

import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.springframework.stereotype.Component;
import org.transitclock.domain.structs.PredictionForStopPath;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class StopPathPredictionCache {
    private final Cache<StopPathCacheKey, StopPredictions> cache;

    public StopPathPredictionCache(CacheManager cm) {
        cache = cm.getCache("StopPathPredictionCache", StopPathCacheKey.class, StopPredictions.class);
    }

    @SuppressWarnings("unchecked")
    public synchronized List<PredictionForStopPath> getPredictions(StopPathCacheKey key) {
        StopPredictions result = cache.get(key);
        if (result == null) return null;
        else return result.getPredictions();
    }

    public void putPrediction(PredictionForStopPath prediction) {
        StopPathCacheKey key = new StopPathCacheKey(prediction.getTripId(), prediction.getStopPathIndex());
        putPrediction(key, prediction);
    }

    @SuppressWarnings("unchecked")
    public synchronized void putPrediction(StopPathCacheKey key, PredictionForStopPath prediction) {

        List<PredictionForStopPath> list;
        StopPredictions element = cache.get(key);

        if (element != null && element.getPredictions() != null) {
            list = element.getPredictions();
            cache.remove(key);
        } else {
            list = new ArrayList<>();
        }
        list.add(prediction);

        element.setPredictions(list);

        cache.put(key, element);
    }
}
