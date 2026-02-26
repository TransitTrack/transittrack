package org.transitclock.core.dataCache;

import lombok.extern.slf4j.Slf4j;

import org.transitclock.domain.repository.WebAgencyRepository;
import org.transitclock.domain.webstructs.WebAgency;
import org.transitclock.utils.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class WebAgencyCache {

    // Cache. Keyed on agencyId
    private static Map<String, WebAgency> webAgencyMapCache;
    private static long webAgencyMapCacheReadTime = 0;
    private static List<WebAgency> webAgencyOrderedList;
    /**
     * If the cache of WebAgency objects not read in for a while then reads the WebAgency objects
     * from the db.
     *
     * <p>Not synchronized since webAgencyMapCache is set in a single operation and it is OK if read
     * db twice on rare occurrence.
     *
     * @param rereadIfOlderThanMsecs If web agencies read from db more than this time ago then they
     *                               are read in again.
     *
     * @return True if data was reread from db
     */
    private static boolean updateCacheIfShould(long rereadIfOlderThanMsecs) {
        // If haven't read in web agencies yet or in a while, do so now
        if (webAgencyMapCache == null || System.currentTimeMillis() - webAgencyMapCacheReadTime > rereadIfOlderThanMsecs) {
            webAgencyMapCache = WebAgencyRepository.getMapFromDb();
            webAgencyMapCacheReadTime = System.currentTimeMillis();

            // Read data from db so return true
            return true;
        } else {
            // Didn't have to read data from db so return false
            return false;
        }
    }

    /**
     * Gets list of web agencies, ordered by their agency names. Uses cached version if possible.
     * But if data not read from db since more than 5 minutes ago then will reread database. This
     * way when this method is used to indicate which agencies are available the list will
     * automatically show additions or deletions.
     *
     * <p>Not synchronized since webAgencyOrderedList is set in a single operation and it is OK if
     * read twice on rare occurrence.
     *
     * @return List of WebAgency objects ordered by their agency names.
     */
    public static synchronized List<WebAgency> getCachedOrderedListOfWebAgencies() {
        // Reread web agencies from db if enough time has elapsed
        boolean reread = updateCacheIfShould(3 * Time.DAY_IN_MSECS);

        if (reread || webAgencyOrderedList == null) {
            List<WebAgency> webAgencyOrderedListTemp = new ArrayList<>(webAgencyMapCache.values());

            // Sort the list
            webAgencyOrderedListTemp.sort((o1, o2) -> {
                // Handle possibility of null agency name
                String agencyName1 = o1.getAgencyName();
                if (agencyName1 == null) {
                    agencyName1 = "";
                }
                String agencyName2 = o2.getAgencyName();
                if (agencyName2 == null) {
                    agencyName2 = "";
                }

                return agencyName1.compareTo(agencyName2);
            });

            // Now that the list has been fully created set the member variable
            webAgencyOrderedList = webAgencyOrderedListTemp;
        }

        return webAgencyOrderedList;
    }

    /**
     * Returns map of all agencies. Values are cached so won't be automatically updated when
     * agencies are changed in the database. Will update cache if haven't done so in
     * rereadIfOlderThanMsecs. This way will automatically get new agencies that are added yet will
     * not be reading from the database for every page hit that needs list of agencies. Also,
     * agencies automatically removed.
     *
     * @param rereadIfOlderThanMsecs If web agencies read from db more than this time ago then they
     *                               are read in again.
     *
     * @return the cached map of WebAgency objects
     */
    private static Map<String, WebAgency> getWebAgencyMapCache(long rereadIfOlderThanMsecs) {
        updateCacheIfShould(rereadIfOlderThanMsecs);

        return webAgencyMapCache;
    }

    /**
     * Gets specified WebAgency from the cache. Rereads WebAgencies from db if cache is older than
     * rereadIfOlderThanMsecs.
     *
     * @param agencyId
     * @param rereadIfOlderThanMsecs If web agencies read from db more than this time ago then they
     *                               are read in again.
     *
     * @return The specified WebAgency, or null if it doesn't exist.
     */
    public static WebAgency getCachedWebAgency(String agencyId, long rereadIfOlderThanMsecs) {
        // Get the web agency from the cache
        WebAgency webAgency = getWebAgencyMapCache(rereadIfOlderThanMsecs).get(agencyId);

        // If WebAgency for agencyId doesn't exist yet it could be because
        // it is a newly configured agency. In this case should update
        // cache and check again, even if was told to not update the cache.
        // Note: the reason that can be told to not update the cache is
        // when just doing a regular RMI call. Can do many of these calls
        // and don't want to access the database unless there is an actual
        // problem. But the possibility of a new agency having been configured
        // is a special case where do need to update the cache.
        // And of course don't want to cause problems by repeatedly accessing
        // the db if an agency doesn't exist anymore. Therefore if want to
        // update the WebAgency cache should only do so once in a while, which
        // is rereadTimeIfWebAgencyNotFoundMsecs is set to 1 minute.
        long rereadTimeIfWebAgencyNotFoundMsecs = Time.MIN_IN_MSECS;
        if (webAgency == null && rereadTimeIfWebAgencyNotFoundMsecs < rereadIfOlderThanMsecs) {
            webAgency = getWebAgencyMapCache(rereadTimeIfWebAgencyNotFoundMsecs).get(agencyId);
        }

        // If web agency was not in cache update the cache and try again
        if (webAgency == null) {
            logger.error(
                    "Did not find agencyId={} in WebAgencies table"
                            + ". Will reload data from database to see if "
                            + "agency only recently added.",
                    agencyId);
        }

        // Return the possibly null web agency
        return webAgency;
    }

    /**
     * Gets specified WebAgency from the cache. Doesn't cause the cache of WebAgencies to be
     * reloaded from the db.
     *
     * @param agencyId
     *
     * @return The specified WebAgency, or null if it doesn't exist.
     */
    public static WebAgency getCachedWebAgency(String agencyId) {
        return getCachedWebAgency(agencyId, Integer.MAX_VALUE);
    }
}
