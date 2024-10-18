/* (C)2023 */
package org.transitclock.api.data.siri;

import org.transitclock.utils.Geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Location object for SIRI
 *
 * @author SkiBu Smith
 */
@Data
public class SiriLocation {

    @JsonProperty("Longitude")
    private String longitude;

    @JsonProperty("Latitude")
    private String latitude;


    public SiriLocation(double latitude, double longitude) {
        this.longitude = Geo.format(longitude);
        this.latitude = Geo.format(latitude);
    }
}