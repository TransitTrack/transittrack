package org.transitclock.core.prediction.accuracy;

import org.transitclock.Module;
import org.transitclock.domain.hibernate.DataDbLogger;
import org.transitclock.domain.structs.ArrivalDeparture;
import org.transitclock.gtfs.DbConfig;

import java.util.concurrent.TimeUnit;

public interface PredictionAccuracyModule extends Module {
    void handleArrivalDeparture(ArrivalDeparture arrivalDeparture);
}
