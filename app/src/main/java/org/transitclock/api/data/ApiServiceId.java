/* (C)2023 */
package org.transitclock.api.data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A short description of a serviceId. For when outputting list of block IDs for service.
 */
@Data
public class ApiServiceId {

    @JsonProperty
    private String id;

    @JsonProperty
    private List<String> blockIds;

    public ApiServiceId(String serviceId, List<String> blockIds) {
        this.id = serviceId;
        this.blockIds = blockIds;
    }
}