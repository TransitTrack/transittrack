/* (C)2023 */
package org.transitclock.core.prediction.bias;

import org.transitclock.properties.CoreProperties.PredictionGenerator;

import lombok.Getter;
import lombok.ToString;

/**
 * @author scrudden This will adjust a prediction based on a percentage which increases
 *     exponentially as the horizon gets bigger.
 */
@Getter
@ToString
public class ExponentialBiasAdjuster implements BiasAdjuster {
    private final PredictionGenerator.Bias.Exponential exponentialProperties;
    private double percentage = Double.NaN;


    public ExponentialBiasAdjuster(PredictionGenerator.Bias.Exponential exponentialProperties) {
        this.exponentialProperties = exponentialProperties;
    }

    /**
     * Compute prediction base on the following equation: y=a(b^x)+c
     */
    @Override
    public long adjustPrediction(long prediction) {
        double toThePower = (prediction / 1000) / 60;
        percentage = ((Math.pow(exponentialProperties.getB(), toThePower)) * exponentialProperties.getA()) - exponentialProperties.getC();

        return (long) (prediction + (exponentialProperties.getUpdown() * (((percentage / 100) * prediction))));
    }
}
