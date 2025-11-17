package org.transitclock.api.data;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.transitclock.domain.webstructs.ApiKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A list of keys
 *
 * @author TsimurSh
 */
@XmlRootElement(name = "keys")
public class ApiAppKeys {

    @XmlElement(name = "keys")
    private List<ApiAppKey> keysData;

    /********************** Member Functions **************************/

    /**
     * Need a no-arg constructor for Jersey. Otherwise get really obtuse
     * "MessageBodyWriter not found for media type=application/json" exception.
     */

    protected ApiAppKeys() {
    }

    public ApiAppKeys(Collection<ApiKey> keys) {
        keysData = new ArrayList<>(keys.size());
        for (ApiKey key : keys) {
            // Map Apikey to ApiAppKey
            keysData.add(new ApiAppKey(key));
        }
    }
}

