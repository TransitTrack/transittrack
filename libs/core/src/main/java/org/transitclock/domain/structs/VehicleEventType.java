package org.transitclock.domain.structs;

public interface VehicleEventType {

    // Some standard event types
    String PREDICTABLE = "Predictable";
    String TIMEOUT = "Timeout";
    String NO_MATCH = "No match";
    String NO_PROGRESS = "No progress";
    String DELAYED = "Delayed";
    String END_OF_BLOCK = "End of block";
    String LEFT_TERMINAL_EARLY = "Left terminal early";
    String LEFT_TERMINAL_LATE = "Left terminal late";
    String NOT_LEAVING_TERMINAL = "Not leaving terminal";
    String ASSIGNMENT_GRABBED = "Assignment Grabbed";
    String ASSIGNMENT_CHANGED = "Assignment Changed";
    String AVL_CONFLICT = "AVL Conflict";
    String PREDICTION_VARIATION = "Prediction variation";
}
