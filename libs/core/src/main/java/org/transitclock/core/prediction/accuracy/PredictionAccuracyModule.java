package org.transitclock.core.prediction.accuracy;

import org.transitclock.Module;
import org.transitclock.domain.structs.ArrivalDeparture;

public interface PredictionAccuracyModule extends Module {
    void handleArrivalDeparture(ArrivalDeparture arrivalDeparture);
}
