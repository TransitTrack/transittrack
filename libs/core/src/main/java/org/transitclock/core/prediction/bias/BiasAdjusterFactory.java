/* (C)2023 */
package org.transitclock.core.prediction.bias;

import org.transitclock.properties.CoreProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BiasAdjusterFactory {

    @Value("${transitclock.core.predictiongenerator.biasabjuster:org.transitclock.core.prediction.bias.DummyBiasAdjuster}")
    private Class<?> className;

    @Bean
    public BiasAdjuster biasAdjuster(CoreProperties coreProperties) {
        if(className == ExponentialBiasAdjuster.class) {
            return new ExponentialBiasAdjuster(coreProperties.getPredictionGenerator().getBias().getExponential());
        } else if (className == LinearBiasAdjuster.class) {
            return new LinearBiasAdjuster(coreProperties.getPredictionGenerator().getBias().getLinear());
        }

        return new DummyBiasAdjuster();
    }
}
