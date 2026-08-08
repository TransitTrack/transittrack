/* (C)2023 */
package org.transitclock.api.data;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcHistoricalAverageCacheKey;

/**
 * @author Sean Og Crudden
 */
@Data
public class ApiHistoricalAverageCacheKeysResponse {

    @JsonProperty
    private List<ApiHistoricalAverageCacheKey> data;

    public ApiHistoricalAverageCacheKeysResponse(Collection<IpcHistoricalAverageCacheKey> cacheKeys) {
        data = cacheKeys.stream()
                .map(ApiHistoricalAverageCacheKey::new)
                .toList();
    }
}
