/* (C)2023 */
package org.transitclock.api.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcCalendar;

/**
 * List of GTFS calendars
 *
 * @author SkiBu Smith
 */
@Data
public class ApiCalendarsResponse {

    @JsonProperty("calendars")
    private List<ApiCalendar> data;

    public ApiCalendarsResponse(List<IpcCalendar> ipcCalendars) {
        data = ipcCalendars.stream()
                .map(ApiCalendar::new)
                .toList();
    }
}
