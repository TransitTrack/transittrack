/* (C)2023 */
package org.transitclock.api.data;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcBlock;

@Data
public class ApiBlocksResponse {
    @JsonProperty("block")
    private List<ApiBlock> data;

    public ApiBlocksResponse(Collection<IpcBlock> blocks) {
        data = blocks.stream()
                .map(ApiBlock::new)
                .toList();
    }
}
