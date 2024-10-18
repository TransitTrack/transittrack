package org.transitclock.properties;

import lombok.Data;

@Data
public class WebProperties {
    // config param: transitclock.web.mapTileUrl
    // Specifies the URL used by Leaflet maps to fetch map tiles.
    private String mapTileUrl = "http://otile4.mqcdn.com/tiles/1.0.0/osm/{z}/{x}/{y}.png";

    // config param: transitclock.web.mapTileCopyright
    // For displaying as map attributing for the where map tiles from.
    private String mapTileCopyright = "MapQuest";

    // config param: transitclock.web.scheduleEarlyMinutes
    // Schedule Adherence early limit
    private Integer scheduleEarlyMinutes = -120;

    // config param: transitclock.web.scheduleLateMinutes
    // Schedule Adherence late limit
    private Integer scheduleLateMinutes = 420;

    // config param: transitclock.web.userPredictionLimits
    // Use the allowable early/late report params or use configured schedule limits
    private Boolean usePredictionLimits = true;

}
