/* (C)2026 */
package org.transitclock.api.data;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Version information returned by {@code GET /version}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiVersion {

    private String version;
    private String buildTime;
    private String commit;

    protected ApiVersion() {}

    public ApiVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public String getBuildTime() {
        return buildTime;
    }

    public void setBuildTime(String buildTime) {
        this.buildTime = buildTime;
    }

    public String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
    }
}
