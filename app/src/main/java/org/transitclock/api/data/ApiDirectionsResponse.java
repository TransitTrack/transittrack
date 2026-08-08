/* (C)2023 */
package org.transitclock.api.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcDirectionsForRoute;

/**
 * A list of directions.
 *
 * @author SkiBu Smith
 */
@Data
public class ApiDirectionsResponse {

    @JsonProperty("direction")
    private List<ApiDirection> data;

    public ApiDirectionsResponse(IpcDirectionsForRoute stopsForRoute) {
        data = stopsForRoute.getDirections()
                .stream()
                .map(ApiDirection::new)
                .toList();
    }
}
