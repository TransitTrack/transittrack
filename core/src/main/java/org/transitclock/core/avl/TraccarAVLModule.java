/*
 * This file is part of thetransitclock.org
 *
 * thetransitclock.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL) as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * thetransitclock.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with thetransitclock.org .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.transitclock.core.avl;

import com.google.common.base.Strings;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.transitclock.core.dataCache.HoldingTimeCache;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.extension.traccar.ApiClient;
import org.transitclock.extension.traccar.ApiException;
import org.transitclock.extension.traccar.api.DefaultApi;
import org.transitclock.extension.traccar.model.DeviceDto;
import org.transitclock.extension.traccar.model.PositionDto;
import org.transitclock.extension.traccar.model.UserDto;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;

import static java.math.BigDecimal.valueOf;
import static org.transitclock.config.data.TraccarConfig.*;
import static org.transitclock.core.avl.OccupancyStatusSource.BusLoadDto;
import static org.transitclock.core.avl.OccupancyStatusSource.fetchBusLoads;


/**
 * @author Sean Óg Crudden This module integrates TheTransitClock with the API of a traccar
 * server to get vehicle locations.
 * <p>
 * See http://www.traccar.org
 * <p>
 * It uses classes that where generated using the swagger file provided
 * with traccar.
 */
@Slf4j
public class TraccarAVLModule extends PollUrlAvlModule {
    @NonNull
    private final DefaultApi api;
    @NonNull
    private final UserDto user;

    private static final boolean isIdAsName = nameInsteadOfId.getValue();
    private static final boolean isMiles = mphInsteadOfKmh.getValue();

    public TraccarAVLModule(String agencyId) throws URISyntaxException {
        super(agencyId);
        useCompression = false;
        ApiClient client;
        try {
            var host = HttpHost.create(TRACCAR_BASEURL.getValue());
            var httpClientBuilder = HttpClientBuilder.create();

            final AuthCache authCache = new BasicAuthCache();
            authCache.put(host, new BasicScheme());

            var provider = new BasicCredentialsProvider();
            provider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials(TRACCAR_EMAIL.getValue(), TRACCAR_PASSWORD.getValue().toCharArray()));
            httpClientBuilder.setDefaultCredentialsProvider(provider);

            client = new ApiClient(httpClientBuilder.build());
        } catch (Exception ex) {
            logger.warn("Unsuccessful traccar authorisation: {} ", ex.toString());
            client = new ApiClient();
        }
        client.setBasePath(TRACCAR_BASEURL.getValue());
        client.setUsername(TRACCAR_EMAIL.getValue());
        client.setPassword(TRACCAR_PASSWORD.getValue());
        this.api = new DefaultApi(client);

        try {
            this.user = this.api
                    .sessionPost(TRACCAR_EMAIL.getValue(), TRACCAR_PASSWORD.getValue());
        } catch (ApiException e) {
            throw new RuntimeException("Traccar login denied", e);
        }
    }

    @Override
    protected void getAndProcessData() throws Exception {


        Map<String, BusLoadDto> occupancies = null;
        if (!OCCUPANCY_SOURCE_URL.getValue().isBlank()) occupancies = fetchBusLoads(OCCUPANCY_SOURCE_URL.getValue());

        Collection<AvlReport> avlReportsReadIn = new ArrayList<>();
        List<DeviceDto> devices = api.devicesGet(true, user.getId(), null, null);
        List<PositionDto> results = api.positionsGet(null, null, null, null);

        for (PositionDto result : results) {
            DeviceDto device = findDeviceById(devices, result.getDeviceId());

            AvlReport avlReport;
            // If have device details use name.
            if (device != null && !Strings.isNullOrEmpty(device.getUniqueId())
                    && occupancies == null && !Strings.isNullOrEmpty(device.getName())) {
                avlReport = getAvlReport(result, device);
                // If have details of occupancy.
            } else if (device != null && !Strings.isNullOrEmpty(device.getUniqueId()) &&
                    occupancies != null && !Strings.isNullOrEmpty(device.getName())) {
                //Traccar return speed in kt
                avlReport = getAvlReportWithOccupancy(result, device, occupancies);
                //Check local time of remote service.
                HoldingTimeCache.setRemoteTimeCheckAPC(occupancies.get("time").getVehicleName());
            } else {
                avlReport = new AvlReport(result.getDeviceId().toString(), "",
                        result.getDeviceTime().toEpochSecond() * 1000,
                        result.getLatitude().doubleValue(),
                        result.getLongitude().doubleValue(),
                        result.getSpeed().multiply(isMiles ? valueOf(1.15077945) : valueOf(1.852)).floatValue(),
                        result.getCourse().floatValue(), TRACCAR_SOURCE.toString());
            }
            avlReportsReadIn.add(avlReport);
        }
        forwardAvlReports(avlReportsReadIn);
    }

    protected void forwardAvlReports(Collection<AvlReport> avlReportsReadIn) {
        processAvlReports(avlReportsReadIn);
    }

    private static AvlReport getAvlReport(PositionDto result, DeviceDto device) {
        AvlReport avlReport;
        avlReport = new AvlReport(isIdAsName ? device.getName() : device.getUniqueId(),
                device.getName(),
                result.getDeviceTime().toEpochSecond() * 1000,
                result.getLatitude().doubleValue(),
                result.getLongitude().doubleValue(),
                result.getSpeed().multiply(isMiles ? valueOf(1.15077945) : valueOf(1.852)).floatValue(),
                result.getCourse().floatValue(),
                TRACCAR_SOURCE.toString());
        return avlReport;
    }

    private static AvlReport getAvlReportWithOccupancy(PositionDto result, DeviceDto device, Map<String, BusLoadDto> occupancies) {
        AvlReport avlReport;
        avlReport = new AvlReport(isIdAsName ? device.getName() : device.getUniqueId(),
                device.getName(),
                result.getDeviceTime().toEpochSecond() * 1000,
                result.getLatitude().doubleValue(),
                result.getLongitude().doubleValue(),
                result.getSpeed().multiply(isMiles ? valueOf(1.15077945) : valueOf(1.852)).floatValue(),
                result.getCourse().floatValue(),
                occupancies.get(device.getName()) != null ? occupancies.get(device.getName()).getCurrentCount() : -1,
                occupancies.get(device.getName()) != null ? occupancies.get(device.getName()).getCurrentFullness() : Float.NaN,
                TRACCAR_SOURCE.toString());
        return avlReport;
    }

    private DeviceDto findDeviceById(List<DeviceDto> devices, Integer id) {
        for (DeviceDto device : devices) {
            if (Objects.equals(device.getId(), id)) {
                return device;
            }
        }
        return null;
    }

    @Override
    protected Collection<AvlReport> processData(InputStream in) throws Exception {
        // Auto-generated method stub
        return null;
    }
}
