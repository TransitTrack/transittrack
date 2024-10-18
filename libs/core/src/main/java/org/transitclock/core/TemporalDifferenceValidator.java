package org.transitclock.core;

import org.springframework.stereotype.Component;

import org.transitclock.properties.CoreProperties;

@Component
public class TemporalDifferenceValidator {
    private final CoreProperties coreProperties;

    public TemporalDifferenceValidator(CoreProperties coreProperties) {
        this.coreProperties = coreProperties;
    }
}
