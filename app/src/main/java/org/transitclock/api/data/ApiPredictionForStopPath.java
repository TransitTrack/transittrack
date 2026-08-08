/* (C)2023 */
package org.transitclock.api.data;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.transitclock.service.dto.IpcPredictionForStopPath;

@Data
public class ApiPredictionForStopPath {

    @JsonProperty
    private String tripId;

    @JsonProperty
    private Integer stopPathIndex;

    @JsonProperty
    private Date creationTime;

    @JsonProperty
    private Double predictionTime;

    @JsonProperty
    private String algorithm;


    public ApiPredictionForStopPath(IpcPredictionForStopPath prediction) {
        this.tripId = prediction.getTripId();
        this.stopPathIndex = prediction.getStopPathIndex();
        this.creationTime = prediction.getCreationTime();
        this.predictionTime = prediction.getPredictionTime();
        this.algorithm = prediction.getAlgorithm();
    }
}
