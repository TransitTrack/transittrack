/* (C)2023 */
package org.transitclock.core.prediction.datafilter;

import org.transitclock.properties.PredictionProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class TravelTimeFilterFactory {

    @Value("${transitclock.core.predictiongenerator.datafilter.traveltime:org.transitclock.core.prediction.datafilter.TravelTimeDataFilterImpl}")
    private Class<?> className;

    @Bean
    @Lazy
    public TravelTimeDataFilter travelTimeDataFilter(PredictionProperties properties) {
        return new TravelTimeDataFilterImpl(properties);
    }
}
