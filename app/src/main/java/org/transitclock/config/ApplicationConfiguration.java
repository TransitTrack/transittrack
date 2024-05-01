package org.transitclock.config;


import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import org.transitclock.properties.ApiProperties;
import org.transitclock.properties.ArrivalsDeparturesProperties;
import org.transitclock.properties.AutoBlockAssignerProperties;
import org.transitclock.properties.AvlProperties;
import org.transitclock.properties.CoreProperties;
import org.transitclock.properties.GtfsProperties;
import org.transitclock.properties.HoldingProperties;
import org.transitclock.properties.MonitoringProperties;
import org.transitclock.properties.PredictionAccuracyProperties;
import org.transitclock.properties.PredictionProperties;
import org.transitclock.properties.ServiceProperties;
import org.transitclock.properties.TimeoutProperties;
import org.transitclock.properties.TravelTimesProperties;
import org.transitclock.properties.TripDataCacheProperties;
import org.transitclock.properties.UpdatesProperties;
import org.transitclock.properties.WebProperties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "transitclock.api")
    public ApiProperties apiProperties() {
        return new ApiProperties();
    }


    @Bean
    @ConfigurationProperties(prefix = "transitclock.arrival-departures")
    public ArrivalsDeparturesProperties arrivalsDepartures() {
        return new ArrivalsDeparturesProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.avl")
    public AvlProperties avlProperties() {
        return new AvlProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.auto-block-assigner")
    public AutoBlockAssignerProperties autoBlockAssignerProperties() {
        return new AutoBlockAssignerProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.core")
    public CoreProperties coreProperties() {
        return new CoreProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.gtfs")
    public GtfsProperties gtfsProperties() {
        return new GtfsProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.holding")
    public HoldingProperties holdingProperties() {
        return new HoldingProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.monitoring")
    public MonitoringProperties monitoringProperties() {
        return new MonitoringProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.pred-accuracy")
    public PredictionAccuracyProperties predictionAccuracyProperties() {
        return new PredictionAccuracyProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.prediction")
    public PredictionProperties predictionProperties() {
        return new PredictionProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.service")
    public ServiceProperties serviceProperties() {
        return new ServiceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.timeout")
    public TimeoutProperties timeoutProperties() {
        return new TimeoutProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.traveltimes")
    public TravelTimesProperties travelTimesProperties() {
        return new TravelTimesProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.tripdatacache")
    public TripDataCacheProperties tripDataCacheProperties() {
        return new TripDataCacheProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.updates")
    public UpdatesProperties updatesProperties() {
        return new UpdatesProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "transitclock.web")
    public WebProperties webProperties() {
        return new WebProperties();
    }
}
