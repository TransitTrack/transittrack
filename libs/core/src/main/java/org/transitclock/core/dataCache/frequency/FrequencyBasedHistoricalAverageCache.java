/* (C)2023 */
package org.transitclock.core.dataCache.frequency;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.transitclock.core.dataCache.ArrivalDepartureComparator;
import org.transitclock.core.dataCache.HistoricalAverage;
import org.transitclock.core.dataCache.IpcArrivalDepartureComparator;
import org.transitclock.core.dataCache.StopPathCacheKey;
import org.transitclock.core.dataCache.TripDataHistoryCacheInterface;
import org.transitclock.core.dataCache.TripKey;
import org.transitclock.domain.structs.ArrivalDeparture;
import org.transitclock.domain.structs.QArrivalDeparture;
import org.transitclock.domain.structs.Trip;
import org.transitclock.gtfs.DbConfig;
import org.transitclock.gtfs.GtfsFilter;
import org.transitclock.properties.CoreProperties;
import org.transitclock.properties.GtfsProperties;
import org.transitclock.service.dto.IpcArrivalDeparture;

import com.querydsl.jpa.impl.JPAQuery;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * @author Sean Óg Crudden This class is to hold the historical average for frequency based
 *     services. It puts them in buckets that represent increments of time. The start time of the
 *     trip is used to decide which bucket to apply the data to or which average to retrieve.
 */
@Slf4j
@Component
public class FrequencyBasedHistoricalAverageCache {

    private final Map<StopPathKey, TreeMap<Long, HistoricalAverage>> m = new ConcurrentHashMap<>();
    private final TripDataHistoryCacheInterface tripDataHistoryCacheInterface;
    private final GtfsFilter gtfsFilter;
    private final DbConfig dbConfig;
    private final CoreProperties coreProperties;

    public FrequencyBasedHistoricalAverageCache(TripDataHistoryCacheInterface tripDataHistoryCacheInterface,
                                                GtfsProperties gtfsProperties,
                                                CoreProperties coreProperties,
                                                DbConfig dbConfig) {
        this.tripDataHistoryCacheInterface = tripDataHistoryCacheInterface;
        this.gtfsFilter = new GtfsFilter(gtfsProperties.getRouteIdFilterRegEx(), gtfsProperties.getTripIdFilterRegEx());
        this.dbConfig = dbConfig;
        this.coreProperties = coreProperties;
    }

    public String toString() {
        String totalsString = "";
        for (StopPathKey key : m.keySet()) {
            Map<Long, HistoricalAverage> values = new TreeMap<>();
            Map<Long, HistoricalAverage> map = m.get(key);

            Set<Long> times = map.keySet();
            for (Long time : times) {
                values.put(time, map.get(time));

                totalsString = totalsString
                        + "\n"
                        + key.tripId
                        + ","
                        + key.stopPathIndex
                        + ","
                        + key.travelTime
                        + ","
                        + time
                        + ","
                        + map.get(time).getCount()
                        + ","
                        + map.get(time).getAverage();
            }
        }

        return totalsString + "\nDetails\n" + m;
    }

    public synchronized HistoricalAverage getAverage(StopPathCacheKey key) {

        logger.debug("Looking for average for : {} in FrequencyBasedHistoricalAverageCache cache.", key);
        TreeMap<Long, HistoricalAverage> result = m.get(new StopPathKey(key));
        if (result != null) {
            logger.debug("Found average buckets for {}. ", key);
            if (key.getStartTime() != null) {
                SortedMap<Long, HistoricalAverage> subresult =
                        result.subMap(key.getStartTime(), key.getStartTime() + coreProperties.getFrequency().getCacheIncrementsForFrequencyService());

                if (subresult.size() == 1) {
                    logger.debug(
                            "Found average for : {} in FrequencyBasedHistoricalAverageCache cache"
                                    + " with a value : {}",
                            key,
                            subresult.get(subresult.lastKey()));
                    return subresult.get(subresult.lastKey());
                } else {
                    logger.debug(
                            "No historical data within time range ({} to {}) for this trip {} in"
                                    + " FrequencyBasedHistoricalAverageCache cache.",
                            key.getStartTime(),
                            key.getStartTime() + coreProperties.getFrequency().getCacheIncrementsForFrequencyService(),
                            key);
                }
            }
        }
        logger.debug("No average found in FrequencyBasedHistoricalAverageCache cache for : {}", key);
        return null;
    }

    private synchronized void putAverage(StopPathCacheKey key, HistoricalAverage average) {
        m.computeIfAbsent(new StopPathKey(key), k -> new TreeMap<>());
        m.get(new StopPathKey(key)).put(key.getStartTime(), average);
    }

    public synchronized void putArrivalDeparture(ArrivalDeparture arrivalDeparture) throws Exception {
        Trip trip = dbConfig.getTrip(arrivalDeparture.getTripId());

        if (trip != null && trip.isNoSchedule()) {
            int time = secondsFromMidnight(arrivalDeparture.getDate(), 2);

            /* this is what puts the trip into the buckets (time slots) */
            time = round(time, coreProperties.getFrequency().getCacheIncrementsForFrequencyService());

            TravelTimeResult pathDuration = getLastPathDuration(new IpcArrivalDeparture(arrivalDeparture), trip);

            if (pathDuration != null
                    && pathDuration.getDuration() > coreProperties.getFrequency().getMinTravelTimeFilterValue()
                    && pathDuration.getDuration() < coreProperties.getFrequency().getMaxTravelTimeFilterValue()) {
                if (trip.isNoSchedule()) {
                    StopPathCacheKey historicalAverageCacheKey = new StopPathCacheKey(
                            trip.getId(), pathDuration.getArrival().getStopPathIndex(), true, (long) time);

                    HistoricalAverage average = getAverage(historicalAverageCacheKey);

                    if (average == null) average = new HistoricalAverage();

                    average.update(pathDuration.getDuration());

                    logger.debug(
                            "Putting : {} in FrequencyBasedHistoricalAverageCache cache for key :"
                                    + " {} which results in : {}.",
                            pathDuration,
                            historicalAverageCacheKey,
                            average);

                    putAverage(historicalAverageCacheKey, average);
                }
            }
            DwellTimeResult stopDuration = getLastStopDuration(new IpcArrivalDeparture(arrivalDeparture), trip);
            if (stopDuration != null
                    && stopDuration.getDuration() > coreProperties.getFrequency().getMinDwellTimeFilterValue()
                    && stopDuration.getDuration() < coreProperties.getFrequency().getMaxDwellTimeFilterValue()) {
                StopPathCacheKey historicalAverageCacheKey = new StopPathCacheKey(
                        trip.getId(), stopDuration.getDeparture().getStopPathIndex(), false, (long) time);

                HistoricalAverage average = getAverage(historicalAverageCacheKey);

                if (average == null) average = new HistoricalAverage();

                average.update(stopDuration.getDuration());

                logger.debug(
                        "Putting : {} in FrequencyBasedHistoricalAverageCache cache for key : {}"
                                + " which results in : {}.",
                        stopDuration,
                        historicalAverageCacheKey,
                        average);

                putAverage(historicalAverageCacheKey, average);
            }
            if (stopDuration == null && pathDuration == null) {
                logger.debug(
                        "Cannot add to FrequencyBasedHistoricalAverageCache as cannot calculate"
                                + " stopDuration or pathDuration. : {}",
                        arrivalDeparture);
            }
            if (pathDuration != null
                    && (pathDuration.getDuration() < coreProperties.getFrequency().getMinTravelTimeFilterValue()
                            || pathDuration.getDuration() > coreProperties.getFrequency().getMaxTravelTimeFilterValue())) {
                logger.debug(
                        "Cannot add to FrequencyBasedHistoricalAverageCache as pathDuration: {} is"
                                + " outside parameters. : {}",
                        pathDuration,
                        arrivalDeparture);
            }
            if (stopDuration != null
                    && (stopDuration.getDuration() < coreProperties.getFrequency().getMinDwellTimeFilterValue()
                            || stopDuration.getDuration() > coreProperties.getFrequency().getMaxDwellTimeFilterValue())) {
                logger.debug(
                        "Cannot add to FrequencyBasedHistoricalAverageCache as stopDuration: {} is"
                                + " outside parameters. : {}",
                        stopDuration,
                        arrivalDeparture);
            }
        } else {
            logger.debug(
                    "Cannot add to FrequencyBasedHistoricalAverageCache as no start time set : {}", arrivalDeparture);
        }
    }

    public IpcArrivalDeparture findPreviousDepartureEvent(
            List<IpcArrivalDeparture> arrivalDepartures, IpcArrivalDeparture current) {
        arrivalDepartures.sort(new IpcArrivalDepartureComparator());
        for (IpcArrivalDeparture tocheck : emptyIfNull(arrivalDepartures)) {
            if (current.getFreqStartTime() != null
                    && tocheck.getFreqStartTime() != null
                    && tocheck.getFreqStartTime().equals(current.getFreqStartTime())) {
                if (tocheck.getStopPathIndex() == (current.getStopPathIndex() - 1)
                        && (current.isArrival() && tocheck.isDeparture())
                        && current.getVehicleId().equals(tocheck.getVehicleId())) {
                    return tocheck;
                }
            }
        }
        return null;
    }

    public IpcArrivalDeparture findPreviousArrivalEvent(
            List<IpcArrivalDeparture> arrivalDepartures, IpcArrivalDeparture current) {
        arrivalDepartures.sort(new IpcArrivalDepartureComparator());
        for (IpcArrivalDeparture tocheck : emptyIfNull(arrivalDepartures)) {
            if (current.getFreqStartTime() != null
                    && tocheck.getFreqStartTime() != null
                    && tocheck.getFreqStartTime().equals(current.getFreqStartTime())) {
                if (tocheck.getStopId().equals(current.getStopId())
                        && (current.isDeparture() && tocheck.isArrival())
                        && current.getVehicleId().equals(tocheck.getVehicleId())) {
                    return tocheck;
                }
            }
        }

        return null;
    }

    private TravelTimeResult getLastPathDuration(IpcArrivalDeparture arrivalDeparture, Trip trip) {
        Date nearestDay = DateUtils.truncate(new Date(arrivalDeparture.getTime().getTime()), Calendar.DAY_OF_MONTH);
        TripKey tripKey = new TripKey(arrivalDeparture.getTripId(), nearestDay, trip.getStartTime());

        List<IpcArrivalDeparture> arrivalDepartures = tripDataHistoryCacheInterface.getTripHistory(tripKey);

        if (arrivalDepartures != null && !arrivalDepartures.isEmpty() && arrivalDeparture.isArrival()) {
            IpcArrivalDeparture previousEvent = findPreviousDepartureEvent(arrivalDepartures, arrivalDeparture);

            if (previousEvent != null && arrivalDeparture != null && previousEvent.isDeparture()) {
                return new TravelTimeResult(previousEvent, arrivalDeparture);
            }
        }

        return null;
    }

    private DwellTimeResult getLastStopDuration(IpcArrivalDeparture arrivalDeparture, Trip trip) {
        Date nearestDay = DateUtils.truncate(new Date(arrivalDeparture.getTime().getTime()), Calendar.DAY_OF_MONTH);
        TripKey tripKey = new TripKey(arrivalDeparture.getTripId(), nearestDay, trip.getStartTime());

        List<IpcArrivalDeparture> arrivalDepartures = tripDataHistoryCacheInterface.getTripHistory(tripKey);

        if (arrivalDepartures != null && !arrivalDepartures.isEmpty() && arrivalDeparture.isDeparture()) {
            IpcArrivalDeparture previousEvent = findPreviousArrivalEvent(arrivalDepartures, arrivalDeparture);

            if (previousEvent != null && arrivalDeparture != null && previousEvent.isArrival()) {
                return new DwellTimeResult(previousEvent, arrivalDeparture);
            }
        }
        return null;
    }

    public void populateCacheFromDb(Session session, Date startDate, Date endDate) throws Exception {
        JPAQuery<ArrivalDeparture> query = new JPAQuery<>(session);
        var qentity = QArrivalDeparture.arrivalDeparture;
        List<ArrivalDeparture> results = query.from(qentity)
                .where(qentity.time.between(startDate,endDate))
                .fetch();

        results.sort(new ArrivalDepartureComparator());
        for (ArrivalDeparture result : results) {
            // TODO this might be better done in the database.
            if (gtfsFilter.routeNotFiltered(result.getRouteId())) {
                putArrivalDeparture(result);
            }
        }
    }

    public static int round(double i, int v) {
        return (int) (Math.floor(i / v) * v);
    }

    public static int secondsFromMidnight(Date date, int startHour) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(date.getTime());
        long now = c.getTimeInMillis();
        c.set(Calendar.HOUR_OF_DAY, startHour);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long passed = now - c.getTimeInMillis();
        long secondsPassed = passed / 1000;

        return (int) secondsPassed;
    }

    private static <T> Iterable<T> emptyIfNull(Iterable<T> iterable) {
        return iterable == null ? Collections.emptyList() : iterable;
    }

    private class StopPathKey {

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((stopPathIndex == null) ? 0 : stopPathIndex.hashCode());
            result = prime * result + (travelTime ? 1231 : 1237);
            result = prime * result + ((tripId == null) ? 0 : tripId.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            StopPathKey other = (StopPathKey) obj;
            if (!getOuterType().equals(other.getOuterType())) return false;
            if (stopPathIndex == null) {
                if (other.stopPathIndex != null) return false;
            } else if (!stopPathIndex.equals(other.stopPathIndex)) return false;
            if (travelTime != other.travelTime) return false;
            if (tripId == null) {
                return other.tripId == null;
            } else return tripId.equals(other.tripId);
        }

        @Override
        public String toString() {
            return "StopPathKey [tripId="
                    + tripId
                    + ", stopPathIndex="
                    + stopPathIndex
                    + ", travelTime="
                    + travelTime
                    + "]";
        }

        String tripId;
        Integer stopPathIndex;

        private boolean travelTime = true;

        public boolean isTravelTime() {
            return travelTime;
        }

        public StopPathKey(StopPathCacheKey stopPathCacheKey) {
            this.tripId = stopPathCacheKey.getTripId();
            this.stopPathIndex = stopPathCacheKey.getStopPathIndex();
            this.travelTime = stopPathCacheKey.isTravelTime();
        }

        private FrequencyBasedHistoricalAverageCache getOuterType() {
            return FrequencyBasedHistoricalAverageCache.this;
        }
    }

    private class TravelTimeResult {
        @Override
        public String toString() {
            return "TravelTimeResult [departure="
                    + departure
                    + ", arrival="
                    + arrival
                    + ", duration="
                    + getDuration()
                    + "]";
        }

        public TravelTimeResult(IpcArrivalDeparture previousEvent, IpcArrivalDeparture arrivalDeparture) {
            // TODO Auto-generated constructor stub
        }

        public ArrivalDeparture getArrival() {
            return arrival;
        }

        public ArrivalDeparture getDeparture() {
            return departure;
        }

        public double getDuration() {
            return Math.abs(arrival.getTime() - departure.getTime());
        }

        private final ArrivalDeparture arrival = null;
        private final ArrivalDeparture departure = null;
    }

    private class DwellTimeResult {
        @Override
        public String toString() {
            return "DwellTimeResult [arrival="
                    + arrival
                    + ", departure="
                    + departure
                    + ", duration="
                    + getDuration()
                    + "]";
        }

        public DwellTimeResult(IpcArrivalDeparture arrival, IpcArrivalDeparture departure) {
            super();
            this.arrival = arrival;
            this.departure = departure;
        }

        public IpcArrivalDeparture getArrival() {
            return arrival;
        }

        public IpcArrivalDeparture getDeparture() {
            return departure;
        }

        public double getDuration() {
            return Math.abs(departure.getTime().getTime() - arrival.getTime().getTime());
        }

        private IpcArrivalDeparture arrival = null;
        private IpcArrivalDeparture departure = null;
    }
}
