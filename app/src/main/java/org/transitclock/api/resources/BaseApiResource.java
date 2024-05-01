package org.transitclock.api.resources;

import org.transitclock.properties.ApiProperties;
import org.transitclock.properties.CoreProperties;
import org.transitclock.service.contract.CacheQueryService;
import org.transitclock.service.contract.CommandsService;
import org.transitclock.service.contract.ConfigService;
import org.transitclock.service.contract.HoldingTimeService;
import org.transitclock.service.contract.PredictionAnalysisService;
import org.transitclock.service.contract.PredictionsService;
import org.transitclock.service.contract.VehiclesService;

import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseApiResource {
    @Autowired
    protected ApiProperties apiProperties;

    @Autowired
    protected CoreProperties coreProperties;

    @Autowired
    protected PredictionsService predictionsService;

    @Autowired
    protected CacheQueryService cacheQueryService;

    @Autowired
    protected PredictionAnalysisService predictionAnalysisService;

    @Autowired
    protected VehiclesService vehiclesService;

    @Autowired
    protected CommandsService commandsService;

    @Autowired
    protected ConfigService configService;

    @Autowired
    protected HoldingTimeService holdingTimeService;
}
