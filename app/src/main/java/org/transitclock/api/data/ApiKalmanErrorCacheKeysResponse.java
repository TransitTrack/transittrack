/* (C)2023 */
package org.transitclock.api.data;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcKalmanErrorCacheKey;

/**
 * @author Sean Og Crudden
 */
@Data
public class ApiKalmanErrorCacheKeysResponse implements Serializable {

    @JsonProperty
    private List<ApiKalmanErrorCacheKey> data;

    public ApiKalmanErrorCacheKeysResponse(Collection<IpcKalmanErrorCacheKey> cacheKeys) {
        data = cacheKeys.stream()
                .map(ApiKalmanErrorCacheKey::new)
                .toList();
    }
}
