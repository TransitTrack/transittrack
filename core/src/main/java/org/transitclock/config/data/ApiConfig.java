package org.transitclock.config.data;

import org.transitclock.config.BooleanConfigValue;
import org.transitclock.config.IntegerConfigValue;
import org.transitclock.config.StringConfigValue;

public class ApiConfig {
    private static final int DEFAULT_MAX_GTFS_RT_CACHE_SECS = 15;

    public static IntegerConfigValue gtfsRtCacheSeconds = new IntegerConfigValue(
            "transitclock.api.gtfsRtCacheSeconds",
            DEFAULT_MAX_GTFS_RT_CACHE_SECS,
            "How long to cache GTFS Realtime");


    public static final IntegerConfigValue predictionMaxFutureSecs = new IntegerConfigValue(
            "transitclock.api.predictionMaxFutureSecs",
            60 * 60,
            "Number of seconds in the future to accept predictions before");

    public static final BooleanConfigValue includeTripUpdateDelay = new BooleanConfigValue(
            "transitclock.api.includeTripUpdateDelay",
            false,
            "Whether or not to include delay in the TripUpdate message");

    public static String getSecret() {
        return secret.getValue();
    }

    private static final StringConfigValue secret = new StringConfigValue(
            "transitclock.api.secret",
            "", // default value
            "Secret word for the management of api keys.");
}
