/* (C)2023 */
package org.transitclock.api.data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * For outputting simple list of unsorted service IDs with lists of sorted block IDs
 */
@Data
public class ApiServiceIdResponse {

    @JsonProperty
    private List<ApiServiceId> serviceIds;

    /**
     * Creates the API unsorted version of list of IDs.
     *
     * @param services
     */
    public ApiServiceIdResponse(Map<String, List<String>> services) {
        serviceIds = services.entrySet()
                .stream()
                .map(entry -> new ApiServiceId(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}