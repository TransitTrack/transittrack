package org.transitclock;

import java.util.TimeZone;

import org.transitclock.domain.ApiKeyManager;
import org.transitclock.domain.webstructs.WebAgency;
import org.transitclock.properties.CoreProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {
    private final ApiKeyManager apiKeyManager;
    private final CoreProperties properties;
    private final DataSourceProperties dataSourceProperties;

    public ApplicationStartupListener(ApiKeyManager apiKeyManager, CoreProperties properties, DataSourceProperties dataSourceProperties) {
        this.apiKeyManager = apiKeyManager;
        this.properties = properties;
        this.dataSourceProperties = dataSourceProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        TimeZone aDefault = TimeZone.getDefault();
        logger.warn("Application started using Timezone [{}, offset={}, daylight={}]", aDefault.getID(), aDefault.getRawOffset(), aDefault.useDaylightTime());

        try {
            apiKeyManager
                .generateApiKey(
                    "Sean Og Crudden",
                    "http://www.transitclock.org",
                    "og.crudden@gmail.com",
                    "123456",
                    "foo");
        } catch (IllegalArgumentException ignored) {

        }

        String agencyId = properties.getAgencyId();
        WebAgency webAgency = new WebAgency(agencyId,
            "127.0.0.1",
            true,
            dataSourceProperties.getName(),
            "postgresql",
            dataSourceProperties.getUrl(),
            dataSourceProperties.getUsername(),
            dataSourceProperties.getPassword());

        try {
            // Store the WebAgency
            webAgency.store(agencyId);
        } catch (IllegalArgumentException ignored) {

        }
    }

}
