package org.transitclock.api.data.gtfs;

import org.transitclock.api.utils.AgencyTimezoneCache;
import org.transitclock.properties.ApiProperties;
import org.transitclock.properties.CoreProperties;
import org.transitclock.service.contract.PredictionsService;
import org.transitclock.service.contract.VehiclesService;

import com.google.transit.realtime.GtfsRealtime;
import org.springframework.stereotype.Component;

@Component
public class FeedCacheManager {
    private final DataCache vehicleFeedDataCache;
    private final DataCache tripFeedDataCache;
    private final ApiProperties apiProperties;

    public FeedCacheManager(ApiProperties apiProperties) {
        this.vehicleFeedDataCache = new DataCache(apiProperties.getGtfsRtCacheSeconds());
        this.tripFeedDataCache = new DataCache(apiProperties.getGtfsRtCacheSeconds());
        this.apiProperties = apiProperties;
    }

    /**
     * For caching Vehicle Positions feed messages.
     *
     * @param agencyId
     * @return
     */
    public GtfsRealtime.FeedMessage getPossiblyCachedMessage(String agencyId,
                                                                    VehiclesService vehiclesService,
                                                                    AgencyTimezoneCache agencyTimezoneCache) {
        GtfsRealtime.FeedMessage feedMessage = vehicleFeedDataCache.get(agencyId);
        if (feedMessage != null) return feedMessage;

        synchronized (vehicleFeedDataCache) {

            // Cache may have been filled while waiting.
            feedMessage = vehicleFeedDataCache.get(agencyId);
            if (feedMessage != null) return feedMessage;

            GtfsRtVehicleFeed feed = new GtfsRtVehicleFeed(agencyId, vehiclesService, agencyTimezoneCache);
            feedMessage = feed.createMessage();
            vehicleFeedDataCache.put(agencyId, feedMessage);
        }

        return feedMessage;
    }

    /**
     * For caching Vehicle Positions feed messages.
     */
    public GtfsRealtime.FeedMessage getPossiblyCachedMessage(CoreProperties coreProperties,
                                                             PredictionsService predictionsService,
                                                             VehiclesService vehiclesService,
                                                             AgencyTimezoneCache agencyTimezoneCache) {
        GtfsRealtime.FeedMessage feedMessage = tripFeedDataCache.get(coreProperties.getAgencyId());
        if (feedMessage != null) return feedMessage;

        synchronized (tripFeedDataCache) {

            // Cache may have been filled while waiting.
            feedMessage = tripFeedDataCache.get(coreProperties.getAgencyId());
            if (feedMessage != null) return feedMessage;

            GtfsRtTripFeed feed = new GtfsRtTripFeed(apiProperties, coreProperties, predictionsService, vehiclesService, agencyTimezoneCache);
            feedMessage = feed.createMessage();
            tripFeedDataCache.put(coreProperties.getAgencyId(), feedMessage);
        }

        return feedMessage;
    }
}
