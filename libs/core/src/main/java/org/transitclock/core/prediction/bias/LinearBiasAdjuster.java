/* (C)2023 */
package org.transitclock.core.prediction.bias;

import org.transitclock.properties.CoreProperties.PredictionGenerator.Bias.Linear;

import lombok.Getter;
import lombok.ToString;

/**
 * @author scrudden
 *     <p>This will adjust a prediction based on a percentage which increases linearly as the
 *     horizon gets bigger.
 *     <p>The rate of increase of the percentage can be set using the constructor.
 */
@Getter
@ToString
public class LinearBiasAdjuster implements BiasAdjuster {
    private final Linear linear;
    private double percentage = Double.NaN;

    public LinearBiasAdjuster(Linear linear) {
        this.linear = linear;
    }

    /* going to adjust by a larger percentage as horizon gets bigger.*/
    @Override
    public long adjustPrediction(long prediction) {
        percentage = (prediction / 100) * linear.getRate();
        return (long) (prediction + (((percentage / 100) * prediction) * linear.getUpdown()));
    }
}
