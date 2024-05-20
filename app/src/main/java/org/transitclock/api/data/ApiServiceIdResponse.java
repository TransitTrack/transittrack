/* (C)2023 */
package org.transitclock.api.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * For outputting simple list of unsorted service IDs with lists of sorted block IDs
 */
@Data
public class ApiServiceIdResponse {

    @JsonProperty("serviceIds")
    private List<ApiServiceId> apiServiceIds;

    /**
     * Creates the API unsorted version of list of IDs.
     *
     * @param serviceIds
     */
    public ApiServiceIdResponse(Map<String, List<String>> serviceIds) {
        apiServiceIds = new ArrayList<>();
        serviceIds.forEach((key, list) -> apiServiceIds
                .add(new ApiServiceId(key, list)));
    }
}