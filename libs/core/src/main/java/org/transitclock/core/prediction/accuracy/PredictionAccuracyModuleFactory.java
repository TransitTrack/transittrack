package org.transitclock.core.prediction.accuracy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.transitclock.core.dataCache.PredictionDataCache;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.properties.GtfsProperties;
import org.transitclock.properties.PredictionAccuracyProperties;

@Configuration
public class PredictionAccuracyModuleFactory {

    @Value("${transitclock.pred-accuracy.enabled:false}")
    private boolean predAccuracyEnabled;


    @Value("${transitclock.pred-accuracy.factory:org.transitclock.core.prediction.accuracy.DefaultPredictionAccuracyModule}")
    private Class<?> neededClass;

    @Bean
    public PredictionAccuracyModule createPredictionAccuracyModule(
            PredictionDataCache predictionDataCache,
            DbConfig dbConfig,
            DataDbLogger dataDbLogger,
            PredictionAccuracyProperties predictionAccuracyProperties,
            GtfsProperties gtfsProperties
    ) {
        if(predAccuracyEnabled) {
            if (neededClass.equals(GTFSRealtimePredictionAccuracyModule.class)) {
                return new GTFSRealtimePredictionAccuracyModule(predictionDataCache, dbConfig, dataDbLogger, predictionAccuracyProperties, gtfsProperties);
            }
            return new DefaultPredictionAccuracyModule(predictionDataCache, dbConfig, dataDbLogger, predictionAccuracyProperties);
        }


        return new NoopPredictionAccuracyModule();
    }

}
