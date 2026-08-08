package org.transitclock.core.prediction.accuracy;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "transitclock.pred-accuracy.enabled")
class PredictionAccuracyModuleRunner {
    private final PredictionAccuracyModuleRunnable module;

    @Scheduled(fixedRateString = "${transitclock.pred-accuracy.pollingRateMsec:240000}", timeUnit = TimeUnit.MILLISECONDS)
    void run(){
        module.run();
    }
}
