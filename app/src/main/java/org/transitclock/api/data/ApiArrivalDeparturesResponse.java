/* (C)2023 */
package org.transitclock.api.data;

import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcArrivalDeparture;


@Data
public class ApiArrivalDeparturesResponse {

    @JsonProperty
    private List<ApiArrivalDeparture> data;


    public ApiArrivalDeparturesResponse(Collection<IpcArrivalDeparture> arrivalDepartures) {
        data = arrivalDepartures.stream()
                .map(ApiArrivalDeparture::new)
                .toList();
    }
}
