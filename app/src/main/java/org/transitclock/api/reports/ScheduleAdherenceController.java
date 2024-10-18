/* (C)2023 */
package org.transitclock.api.reports;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.transitclock.domain.hibernate.HibernateUtils;
import org.transitclock.domain.structs.ArrivalDeparture;
import org.transitclock.properties.WebProperties;
import org.transitclock.utils.Time;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleAdherenceController {

    // TODO: Combine routeScheduleAdherence and stopScheduleAdherence
    // - Make this a REST endpoint
    // problem - negative schedule adherence means we're late

    private final WebProperties webProperties;
//    private static final String ADHERENCE_SQL = "(time - scheduledTime) AS scheduleAdherence";
//    private static final Projection ADHERENCE_PROJECTION = Projections.sqlProjection(
//            ADHERENCE_SQL, new String[] {"scheduleAdherence"}, new Type[] {DoubleType.INSTANCE});
//    private static final Projection AVG_ADHERENCE_PROJECTION = Projections.sqlProjection(
//            "avg" + ADHERENCE_SQL, new String[] {"scheduleAdherence"}, new Type[] {DoubleType.INSTANCE});

    public List<Object> stopScheduleAdherence(
            Date startDate,
            int numDays,
            String startTime,
            String endTime,
            List<String> stopIds,
            boolean byStop,
            String datatype) {

        return groupScheduleAdherence(startDate, numDays, startTime, endTime, "stopId", stopIds, byStop, datatype);
    }

    public List<Object> routeScheduleAdherence(
            Date startDate,
            int numDays,
            String startTime,
            String endTime,
            List<String> routeIds,
            boolean byRoute,
            String datatype) {

        return groupScheduleAdherence(startDate, numDays, startTime, endTime, "routeId", routeIds, byRoute, datatype);
    }

    public Map<String, String> routeScheduleAdherenceSummary(Date startDate, int numDays, String startTime, String endTime, Double earlyLimitParam, Double lateLimitParam, List<String> routeIds) {

        int count = 0;
        int early = 0;
        int late = 0;
        int ontime = 0;

        var earlyLimit = (webProperties.getUsePredictionLimits() ? earlyLimitParam : (double) webProperties.getScheduleEarlyMinutes());
        var lateLimit = (webProperties.getUsePredictionLimits() ? lateLimitParam : (double) webProperties.getScheduleLateMinutes());

        List<Object> results = routeScheduleAdherence(startDate, numDays, startTime, endTime, routeIds, false, null);
        Map <String, String> result = new HashMap<>();

        for (Object o : results) {
            count++;

            var hm = (HashMap) o;
            Duration d = (Duration) hm.get("scheduleAdherence");
            double totalSeconds = d.toMillis() / 1000.0;

            if (totalSeconds > lateLimit) {
                late++;
            } else if (totalSeconds < earlyLimit) {
                early++;
            } else {
                ontime++;
            }
        }
        logger.info( "query complete -- earlyLimit={}, lateLimit={}, early={}, onTime={}, late={}," + " count={}",
                     earlyLimit,
                     lateLimit,
                     early,
                     ontime,
                     late,
                     count);

        double earlyPercent = (1.0 - (double) (count - early) / count) * 100;
        double onTimePercent = (1.0 - (double) (count - ontime) / count) * 100;
        double latePercent = (1.0 - (double) (count - late) / count) * 100;
        logger.info( "count=static{} earlyPercent={} onTimePercent={} latePercent={}",
                     count,
                     earlyPercent,
                     onTimePercent,
                     latePercent);
        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.DOWN);

        result.put("count", String.valueOf(count));
        result.put("early", df.format(earlyPercent));
        result.put("late", df.format(latePercent));
        result.put("onTime", df.format(onTimePercent));
        return result;
    }

    private List<Object> groupScheduleAdherence(Date startDate, int numDays, String startTime, String endTime, String groupName, List<String> idsOrEmpty, boolean byGroup, String datatype) {

        List<String> ids = new ArrayList<>();
        if (idsOrEmpty != null) {
            for (String id : idsOrEmpty) {
                if (!StringUtils.isBlank(id)) {
                    ids.add(id);
                }
            }
        }

        // Calculate end date based on start date and numDays
        Date endDate = new Date(startDate.getTime() + (numDays * Time.MS_PER_DAY));

        try(Session session = HibernateUtils.getSession()) {
            // Create Query
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
            Root<ArrivalDeparture> root = query.from(ArrivalDeparture.class);

            // Building predicates
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(root.get("time"), startDate, endDate));
            predicates.add(cb.isNotNull(root.get("scheduledTime")));

            // Check if we're dealing with 'arrival' or 'departure'
            if ("arrival".equals(datatype)) {
                predicates.add(cb.isTrue(root.get("isArrival")));
            } else if ("departure".equals(datatype)) {
                predicates.add(cb.isFalse(root.get("isArrival")));
            }

            Expression<String> timePartExpr = cb.function("TO_CHAR", String.class, root.get("time"), cb.literal("HH24:MI:SS"));
            predicates.add(cb.greaterThanOrEqualTo(timePartExpr, startTime));
            predicates.add(cb.lessThanOrEqualTo(timePartExpr, endTime));

            if (!ids.isEmpty()) {
                predicates.add(root.get(groupName).in(ids));
            }

            query.where(predicates.toArray(new Predicate[0]));

            // Grouping logic based on byGroup flag
            if (byGroup) {
                query.multiselect(
                        root.get(groupName),
                        cb.count(root),
                        cb.avg(cb.diff(root.get("time"), root.get("scheduledTime")))
                ).groupBy(root.get(groupName));
            } else {
                query.multiselect(
                        root.get("routeId"),
                        root.get("stopId"),
                        root.get("tripId"),
                        cb.diff(root.get("time"), root.get("scheduledTime"))
                );
            }

            Query<Object[]> hibernateQuery = session.createQuery(query);
            List<Object[]> results = hibernateQuery.getResultList();
            // Get result
            return results.stream()
                    .map(result -> {
                        HashMap<String, Object> map = new HashMap<>();
                        if (byGroup) {
                            map.put(groupName, result[0]);
                            map.put("count", result[1]);
                            map.put("scheduleAdherence", result[2]);
                        } else {
                            map.put("routeId", result[0]);
                            map.put("stopId", result[1]);
                            map.put("tripId", result[2]);
                            map.put("scheduleAdherence", result[3]);
                        }
                        return map;
                    })
                    .collect(Collectors.toList());

        } catch (Exception esqEx) {
            esqEx.printStackTrace();
        }
        return List.of();
    }
}
