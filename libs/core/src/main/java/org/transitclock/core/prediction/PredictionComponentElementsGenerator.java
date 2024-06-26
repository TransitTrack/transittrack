/* (C)2023 */
package org.transitclock.core.prediction;

import org.transitclock.core.Indices;
import org.transitclock.core.avl.space.SpatialMatch;
import org.transitclock.core.VehicleStatus;
import org.transitclock.domain.structs.AvlReport;

public interface PredictionComponentElementsGenerator {
    /* this generates a prediction for travel time between stops */
    long getTravelTimeForPath(Indices indices, AvlReport avlReport, VehicleStatus vehicleStatus);

    long getStopTimeForPath(Indices indices, AvlReport avlReport, VehicleStatus vehicleStatus);

    long expectedTravelTimeFromMatchToEndOfStopPath(AvlReport avlReport, SpatialMatch match);
}
