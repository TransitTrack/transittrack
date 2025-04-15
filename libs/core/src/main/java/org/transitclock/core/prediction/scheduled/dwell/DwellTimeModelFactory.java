/* (C)2023 */
package org.transitclock.core.prediction.scheduled.dwell;

import org.transitclock.properties.PredictionProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author scrudden Returns the model that is to be used to estimate dwell time for a stop.
 */
@Configuration
public class DwellTimeModelFactory {
    // The name of the class to instantiate
    @Value("${transitclock.core.dwelltime.model:org.transitclock.core.prediction.scheduled.dwell.DwellAverage}")
    private Class<?> className;

    @Bean
    public DwellModel dwellModel(PredictionProperties properties) {
        if (className == DwellAverage.class) {
            return new DwellAverage(properties.getDwell().getAverage());
        }

        return new DwellRLS(properties.getRls());
    }
}
