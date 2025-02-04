/* (C)2023 */
package org.transitclock.config.data;

import org.transitclock.config.BooleanConfigValue;
import org.transitclock.config.StringConfigValue;


public class TraccarConfig {
    /**
     * Traccar properties for log in by "TraccarAVLModule"
     *
     * @return
     */
    public static final StringConfigValue TRACCAR_EMAIL = new StringConfigValue("transitclock.avl.traccar.email", null,
            "This is the username for the traccar server api.");

    public static final StringConfigValue TRACCAR_PASSWORD = new StringConfigValue("transitclock.avl.traccar.password",
            null, "This is the password for the traccar server api");

    public static final StringConfigValue TRACCAR_BASEURL = new StringConfigValue("transitclock.avl.traccar.baseurl",
            null, "This is the url for the traccar server api.");

    public static final StringConfigValue TRACCAR_SOURCE = new StringConfigValue("transitclock.avl.traccar.source",
            "TRACCAR", "This is the value recorded in the source for the AVL Report.");

    public static final StringConfigValue OCCUPANCY_SOURCE_URL = new StringConfigValue("transitclock.avl.occupancy.baseurl",
            " ", "This is the url for the traccar server api.");

    public static final BooleanConfigValue nameInsteadOfId = new BooleanConfigValue("transitclock.avl.traccar.nameInsteadOfId",
            false, "True if is wanted to receive a name from the server instead of an ID.");
}
