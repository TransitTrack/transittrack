package org.transitclock.core.prediction.accuracy;

import org.transitclock.domain.structs.ArrivalDeparture;

public class NoopPredictionAccuracyModule implements PredictionAccuracyModule, PredictionAccuracyModuleRunnable{
    @Override
    public void run() {
        // noop
    }

    @Override
    public void handleArrivalDeparture(ArrivalDeparture arrivalDeparture) {
        // noop
    }
}
