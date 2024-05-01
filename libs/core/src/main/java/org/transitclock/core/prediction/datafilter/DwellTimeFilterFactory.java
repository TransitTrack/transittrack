/* (C)2023 */
package org.transitclock.core.prediction.datafilter;

import org.transitclock.properties.PredictionProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class DwellTimeFilterFactory {
    // The name of the class to instantiate
    @Value("${transitclock.core.predictiongenerator.datafilter.dwelltime:org.transitclock.core.prediction.datafilter.DwellTimeDataFilterImpl}")
    private Class<?> className;

    @Bean
    @Lazy
    public DwellTimeDataFilter dwellTimeDataFilter(PredictionProperties predictionProperties) {
        return new DwellTimeDataFilterImpl(predictionProperties);
    }
}
