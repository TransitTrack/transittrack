/* (C)2023 */
package org.transitclock.api.data;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcTripPattern;

/**
 * A list of ApiTripPattern objects
 *
 * @author SkiBu Smith
 */
@Data
public class ApiTripPatternsResponse {

    @JsonProperty("tripPatterns")
    private List<ApiTripPattern> data;


    public ApiTripPatternsResponse(Collection<IpcTripPattern> ipcTripPatterns) {
        data = ipcTripPatterns
                .stream()
                .map(tp -> new ApiTripPattern(tp, true))
                .toList();
    }
}
