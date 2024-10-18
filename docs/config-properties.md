# Transit Track configuration parameters
Configuration parameters with their default values for transit-track realtime generator application.
## Table of Contents
* [**transitclock.gtfs.auto-update** - `org.transitclock.properties.GtfsProperties$AutoUpdate`](#transitclock.gtfs.auto-update)
* [**transitclock.prediction.dwell.average** - `org.transitclock.properties.PredictionProperties$Dwell$Average`](#transitclock.prediction.dwell.average)
* [**transitclock.prediction.dwell** - `org.transitclock.properties.PredictionProperties$Dwell`](#transitclock.prediction.dwell)
* [**transitclock.prediction.rls** - `org.transitclock.properties.PredictionProperties$Rls`](#transitclock.prediction.rls)
* [**transitclock.prediction.travel** - `org.transitclock.properties.PredictionProperties$Travel`](#transitclock.prediction.travel)
* [**transitclock.api** - `org.transitclock.properties.ApiProperties`](#transitclock.api)
* [**transitclock.arrival-departures** - `org.transitclock.properties.ArrivalsDeparturesProperties`](#transitclock.arrival-departures)
* [**transitclock.auto-block-assigner** - `org.transitclock.properties.AutoBlockAssignerProperties`](#transitclock.auto-block-assigner)
* [**transitclock.avl** - `org.transitclock.properties.AvlProperties`](#transitclock.avl)
* [**transitclock.core** - `org.transitclock.properties.CoreProperties`](#transitclock.core)
* [**transitclock.gtfs** - `org.transitclock.properties.GtfsProperties`](#transitclock.gtfs)
* [**transitclock.holding** - `org.transitclock.properties.HoldingProperties`](#transitclock.holding)
* [**transitclock.monitoring** - `org.transitclock.properties.MonitoringProperties`](#transitclock.monitoring)
* [**transitclock.pred-accuracy** - `org.transitclock.properties.PredictionAccuracyProperties`](#transitclock.pred-accuracy)
* [**transitclock.prediction** - `org.transitclock.properties.PredictionProperties`](#transitclock.prediction)
* [**transitclock.service** - `org.transitclock.properties.ServiceProperties`](#transitclock.service)
* [**transitclock.timeout** - `org.transitclock.properties.TimeoutProperties`](#transitclock.timeout)
* [**transitclock.traveltimes** - `org.transitclock.properties.TravelTimesProperties`](#transitclock.traveltimes)
* [**transitclock.tripdatacache** - `org.transitclock.properties.TripDataCacheProperties`](#transitclock.tripdatacache)
* [**transitclock.updates** - `org.transitclock.properties.UpdatesProperties`](#transitclock.updates)
* [**transitclock.core.cache** - `org.transitclock.properties.CoreProperties$Cache`](#transitclock.core.cache)
* [**transitclock.core.frequency** - `org.transitclock.properties.CoreProperties$Frequency`](#transitclock.core.frequency)
* [**transitclock.prediction.data.average** - `org.transitclock.properties.PredictionProperties$PredictionData$Average`](#transitclock.prediction.data.average)
* [**transitclock.prediction.data.kalman** - `org.transitclock.properties.PredictionProperties$PredictionData$Kalman`](#transitclock.prediction.data.kalman)
* [**transitclock.core.prediction-generator.bias.exponential** - `org.transitclock.properties.CoreProperties$PredictionGenerator$Bias$Exponential`](#transitclock.core.prediction-generator.bias.exponential)
* [**transitclock.core.prediction-generator.bias.linear** - `org.transitclock.properties.CoreProperties$PredictionGenerator$Bias$Linear`](#transitclock.core.prediction-generator.bias.linear)

### transitclock.gtfs.auto-update
**Class:** `org.transitclock.properties.GtfsProperties$AutoUpdate`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| dir-name| java.lang.String| | | | 
| enabled| java.lang.Boolean| | | | 
| interval-msec| java.lang.Long| | | | 
| url| java.lang.String| | | | 
### transitclock.prediction.dwell.average
**Class:** `org.transitclock.properties.PredictionProperties$Dwell$Average`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| fractionlimit| java.lang.Double| | | | 
| samplesize| java.lang.Integer| | | | 
### transitclock.prediction.dwell
**Class:** `org.transitclock.properties.PredictionProperties$Dwell`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| min-dwell-time-allowed-in-model| java.lang.Long| | | | 
### transitclock.prediction.rls
**Class:** `org.transitclock.properties.PredictionProperties$Rls`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| lambda| java.lang.Double| | | | 
| max-dwell-time-allowed-in-model| java.lang.Long| | | | 
| max-headway-allowed-in-model| java.lang.Long| | | | 
| max-scehedule-adherence| java.lang.Integer| | | | 
| min-dwell-time-allowed-in-model| java.lang.Long| | | | 
| min-headway-allowed-in-model| java.lang.Long| | | | 
| min-scehedule-adherence| java.lang.Integer| | | | 
### transitclock.prediction.travel
**Class:** `org.transitclock.properties.PredictionProperties$Travel`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| max-travel-time-allowed-in-model| java.lang.Long| | | | 
| min-travel-time-allowed-in-model| java.lang.Long| | | | 
### transitclock.api
**Class:** `org.transitclock.properties.ApiProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| gtfs-rt-cache-seconds| java.lang.Integer| | | | 
| include-trip-update-delay| java.lang.Boolean| | | | 
| prediction-max-future-secs| java.lang.Integer| | | | 
### transitclock.arrival-departures
**Class:** `org.transitclock.properties.ArrivalsDeparturesProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| allowable-difference-between-avl-time-secs| java.lang.Integer| | | | 
| max-stops-between-matches| java.lang.Integer| | | | 
| max-stops-when-no-previous-match| java.lang.Integer| | | | 
### transitclock.auto-block-assigner
**Class:** `org.transitclock.properties.AutoBlockAssignerProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| allowable-early-seconds| java.lang.Integer| | | | 
| allowable-late-seconds| java.lang.Integer| | | | 
| auto-assigner-enabled| java.lang.Boolean| | | | 
| ignore-avl-assignments| java.lang.Boolean| | | | 
| min-distance-from-current-report| java.lang.Double| | | | 
| min-time-between-auto-assigning-secs| java.lang.Integer| | | | 
### transitclock.avl
**Class:** `org.transitclock.properties.AvlProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| alternative-max-speed| java.lang.Double| | | | 
| authentication-password| java.lang.String| | | | 
| authentication-user| java.lang.String| | | | 
| feed-polling-rate-secs| java.lang.Integer| | | | 
| feed-timeout-in-m-secs| java.lang.Integer| | | | 
| gtfs-realtime-feed-u-r-i| java.util.List&lt;java.lang.String&gt;| | | | 
| max-latitude| java.lang.Float| | | | 
| max-longitude| java.lang.Float| | | | 
| max-speed| java.lang.Double| | | | 
| max-stop-paths-ahead| java.lang.Integer| | | | 
| min-latitude| java.lang.Float| | | | 
| min-longitude| java.lang.Float| | | | 
| min-speed-for-valid-heading| java.lang.Double| | | | 
| min-time-between-avl-reports-secs| java.lang.Integer| | | | 
| num-threads| java.lang.Integer| | | | 
| process-in-real-time| java.lang.Boolean| | | | 
| queue-size| java.lang.Integer| | | | 
| should-process-avl| java.lang.Boolean| | | | 
| unpredictable-assignments-reg-ex| java.lang.String| | | | 
| urls| java.util.List&lt;java.lang.String&gt;| | | | 
### transitclock.core
**Class:** `org.transitclock.properties.CoreProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| after-stop-distance| java.lang.Double| | | | 
| agency-id| java.lang.String| | | | 
| agressive-block-loading| java.lang.Boolean| | | | 
| allowable-bad-assignments| java.lang.Integer| | | | 
| allowable-early-departure-time-for-logging-event| java.lang.Integer| | | | 
| allowable-early-for-layover-seconds| java.lang.Integer| | | | 
| allowable-early-seconds| java.lang.Integer| | | | 
| allowable-early-seconds-for-initial-matching| java.lang.Integer| | | | 
| allowable-early-time-for-early-departure-secs| java.lang.Integer| | | | 
| allowable-late-at-terminal-for-logging-event| java.lang.Integer| | | | 
| allowable-late-departure-time-for-logging-event| java.lang.Integer| | | | 
| allowable-late-seconds| java.lang.Integer| | | | 
| allowable-late-seconds-for-initial-matching| java.lang.Integer| | | | 
| allowable-number-of-bad-matches| java.lang.Integer| | | | 
| avl-history-max-size| java.lang.Integer| | | | 
| before-stop-distance| java.lang.Double| | | | 
| blockactive-for-time-after-secs| java.lang.Integer| | | | 
| blockactive-for-time-before-secs| java.lang.Integer| | | | 
| cache-reload-end-time-str| java.lang.String| | | | 
| cache-reload-start-time-str| java.lang.String| | | | 
| deadheading-short-versus-long-distance| java.lang.Float| | | | 
| default-break-time-sec| java.lang.Integer| | | | 
| distance-between-avls-for-initial-matching-without-heading| java.lang.Double| | | | 
| distance-from-end-of-block-for-initial-matching| java.lang.Double| | | | 
| distance-from-last-stop-for-end-matching| java.lang.Double| | | | 
| distance-from-layover-for-early-departure| java.lang.Double| | | | 
| early-to-late-ratio| java.lang.Double| | | | 
| email-messages-when-assignment-grab-improper| java.lang.Boolean| | | | 
| event-history-max-size| java.lang.Integer| | | | 
| exclusive-block-assignments| java.lang.Boolean| | | | 
| generate-holding-time-when-prediction-within| java.lang.Long| | | | 
| ignore-inactive-blocks| java.lang.Boolean| | | | 
| layover-distance| java.lang.Double| | | | 
| long-distance-deadheading-speed| java.lang.Float| | | | 
| match-history-max-size| java.lang.Integer| | | | 
| max-distance-for-assignment-grab| java.lang.Double| | | | 
| max-distance-from-segment| java.lang.Double| | | | 
| max-distance-from-segment-for-auto-assigning| java.lang.Double| | | | 
| max-dwell-time| java.lang.Integer| | | | 
| max-heading-offset-from-segment| java.lang.Float| | | | 
| max-late-cutoff-preds-for-next-trips-secs| java.lang.Integer| | | | 
| max-match-distance-from-a-v-l-record| java.lang.Double| | | | 
| max-prediction-time-for-db-secs| java.lang.Integer| | | | 
| max-predictions-time-secs| java.lang.Integer| | | | 
| min-distance-for-delayed| java.lang.Double| | | | 
| min-distance-for-no-progress| java.lang.Double| | | | 
| only-need-arrival-departures| java.lang.Boolean| | | | 
| pause-if-db-queue-filling| java.lang.Boolean| | | | 
| short-distance-deadheading-speed| java.lang.Float| | | | 
| spatial-match-to-layovers-allowed-for-auto-assignment| java.lang.Boolean| | | | 
| store-data-in-database| java.lang.Boolean| | | | 
| store-dwell-time-stop-path-predictions| java.lang.Boolean| | | | 
| store-travel-time-stop-path-predictions| java.lang.Boolean| | | | 
| terminal-distance-for-route-matching| java.lang.Double| | | | 
| time-for-determining-delayed-secs| java.lang.Integer| | | | 
| time-for-determining-no-progress| java.lang.Integer| | | | 
| timezone| java.lang.String| For setting timezone for application. Ideally would get timezone from the agency db but once a Hibernate session factory is created, such as for reading timezone from db, then it is too late to set the timezone. Therefore this provides ability to set it manually.| null| | 
| use-arrival-predictions-for-normal-stops| java.lang.Boolean| | | | 
| use-exact-sched-time-for-wait-stops| java.lang.Boolean| | | | 
| use-holding-time-in-prediction| java.lang.Boolean| | | | 
### transitclock.gtfs
**Class:** `org.transitclock.properties.GtfsProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| block-id-reg-ex| java.lang.String| | | | 
| capitalize| java.lang.Boolean| Sometimes GTFS titles have all capital letters or other capitalization issues. If set to true then will properly capitalize titles when process GTFS data. But note that this can require using regular expressions to fix things like acronyms that actually should be all caps.| false| | 
| gtfs-trip-update-url| java.lang.String| | | | 
| min-distance-between-stops-to-disambiguate-headsigns| java.lang.Double| | | | 
| output-paths-and-stops-for-graphing-route-ids| java.lang.String| | | | 
| route-id-filter-reg-ex| java.lang.String| | | | 
| stop-code-base-value| java.lang.Integer| | | | 
| trip-id-filter-reg-ex| java.lang.String| | | | 
| trip-short-name-reg-ex| java.lang.String| | | | 
### transitclock.holding
**Class:** `org.transitclock.properties.HoldingProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| control-stops| java.util.List&lt;java.lang.String&gt;| | | | 
| max-predictions-for-holding-time-calculation| java.lang.Integer| | | | 
| planned-headway-msec| java.lang.Integer| | | | 
| regenerateondeparture| java.lang.Boolean| Regenerate a holding time for all vehicles at control point when a vehicle departs the control point.| false| | 
| store-holding-times| java.lang.Boolean| | | | 
| usearrivalevents| java.lang.Boolean| Generate a holding time on arrival events.| true| | 
| usearrivalpredictions| java.lang.Boolean| Generate a holding time on arrival predictions.| true| | 
### transitclock.monitoring
**Class:** `org.transitclock.properties.MonitoringProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| allowable-no-avl-secs| java.lang.Integer| | | | 
| available-free-physical-memory-threshold| java.lang.Long| | | | 
| available-free-physical-memory-threshold-gap| java.lang.Long| | | | 
| avl-feed-email-recipients| java.lang.String| | | | 
| cpu-threshold| java.lang.Double| | | | 
| cpu-threshold-gap| java.lang.Double| | | | 
| email-recipients| java.lang.String| | | | 
| max-queue-fraction| java.lang.Double| | | | 
| max-queue-fraction-gap| java.lang.Double| | | | 
| min-predictable-blocks| java.lang.Double| | | | 
| min-predictable-blocks-gap| java.lang.Double| | | | 
| minimum-predictable-vehicles| java.lang.Integer| | | | 
| retry-timeout-secs| java.lang.Integer| | | | 
| seconds-between-monitorin-polling| java.lang.Integer| | | | 
| usable-disk-space-threshold| java.lang.Long| | | | 
| usable-disk-space-threshold-gap| java.lang.Long| | | | 
### transitclock.pred-accuracy
**Class:** `org.transitclock.properties.PredictionAccuracyProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| max-earlyness-compared-to-prediction-msec| java.lang.Integer| | | | 
| max-lateness-compared-to-prediction-msec| java.lang.Integer| | | | 
| max-pred-staleness-minutes| java.lang.Integer| | | | 
| max-pred-time-minutes| java.lang.Integer| | | | 
| max-random-stop-selections-per-trip| java.lang.Integer| | | | 
| polling-rate-msec| java.lang.Integer| | | | 
| stops-per-trip| java.lang.Integer| | | | 
### transitclock.prediction
**Class:** `org.transitclock.properties.PredictionProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| after-start-time-minutes| java.lang.Integer| | | | 
| before-start-time-minutes| java.lang.Integer| | | | 
| cancel-trip-on-timeout| java.lang.Boolean| | | | 
| closestvehiclestopsahead| java.lang.Integer| | | | 
| polling-rate-msec| java.lang.Integer| | | | 
| process-immediately-at-startup| java.lang.Boolean| | | | 
| return-arrival-prediction-for-end-of-trip| java.lang.Boolean| | | | 
### transitclock.service
**Class:** `org.transitclock.properties.ServiceProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| minutes-into-morning-to-include-previous-service-ids| java.lang.Integer| | | | 
### transitclock.timeout
**Class:** `org.transitclock.properties.TimeoutProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| allowable-no-avl-after-sched-depart-secs| java.lang.Integer| | | | 
| allowable-no-avl-secs| java.lang.Integer| | | | 
| polling-rate-secs| java.lang.Integer| | | | 
| remove-timed-out-vehicles-from-vehicle-data-cache| java.lang.Boolean| | | | 
### transitclock.traveltimes
**Class:** `org.transitclock.properties.TravelTimesProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| fraction-limit-for-stop-times| java.lang.Double| | | | 
| fraction-limit-for-travel-times| java.lang.Double| | | | 
| max-segment-speed-mps| java.lang.Double| | | | 
| max-travel-time-segment-length| java.lang.Double| | | | 
| min-segment-speed-mps| java.lang.Double| | | | 
| reset-early-terminal-departures| java.lang.Boolean| | | | 
### transitclock.tripdatacache
**Class:** `org.transitclock.properties.TripDataCacheProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| trip-data-cache-max-age-sec| java.lang.Integer| | | | 
### transitclock.updates
**Class:** `org.transitclock.properties.UpdatesProperties`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| page-db-reads| java.lang.Boolean| | | | 
| page-size| java.lang.Integer| | | | 
### transitclock.core.cache
**Class:** `org.transitclock.properties.CoreProperties$Cache`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| days-populate-historical-cache| java.lang.Integer| | | | 
### transitclock.core.frequency
**Class:** `org.transitclock.properties.CoreProperties$Frequency`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| cache-increments-for-frequency-service| java.lang.Integer| | | | 
| max-dwell-time-filter-value| java.lang.Integer| | | | 
| max-travel-time-filter-value| java.lang.Integer| | | | 
| min-dwell-time-filter-value| java.lang.Integer| | | | 
| min-travel-time-filter-value| java.lang.Integer| | | | 
### transitclock.prediction.data.average
**Class:** `org.transitclock.properties.PredictionProperties$PredictionData$Average`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| mindays| java.lang.Integer| | | | 
### transitclock.prediction.data.kalman
**Class:** `org.transitclock.properties.PredictionProperties$PredictionData$Kalman`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| initialerrorvalue| java.lang.Double| | | | 
| maxdays| java.lang.Integer| | | | 
| maxdaystosearch| java.lang.Integer| | | | 
| mindays| java.lang.Integer| | | | 
| percentage-prediction-method-differencene| java.lang.Integer| | | | 
| threshold-for-difference-event-log| java.lang.Integer| | | | 
| useaverage| java.lang.Boolean| | | | 
| usekalmanforpartialstoppaths| java.lang.Boolean| | | | 
### transitclock.core.prediction-generator.bias.exponential
**Class:** `org.transitclock.properties.CoreProperties$PredictionGenerator$Bias$Exponential`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| a| java.lang.Double| | | | 
| b| java.lang.Double| | | | 
| c| java.lang.Double| | | | 
| updown| java.lang.Integer| | | | 
### transitclock.core.prediction-generator.bias.linear
**Class:** `org.transitclock.properties.CoreProperties$PredictionGenerator$Bias$Linear`

|Key|Type|Description|Default value|Deprecation|
|---|----|-----------|-------------|-----------|
| rate| java.lang.Double| | | | 
| updown| java.lang.Integer| | | | 


This is a generated file, generated at: **2024-10-18T22:29:57.954559614**

