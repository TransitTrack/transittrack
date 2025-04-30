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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.transitclock.core.avl.OccupancyStatusSource.BusLoadDto;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.properties.AvlProperties;
import org.transitclock.utils.IntervalTimer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static java.math.BigDecimal.valueOf;
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
@Component
@ConditionalOnProperty(name = "transitclock.avl.traccar-base-url")
public class AvlTraccarModule extends PollUrlAvlModule {

    private final String OPC_URL = avlProperties.getOccupancyBaseUrl();

    private final boolean isMiles = avlProperties.getSpeedInMiles();
    private final boolean isIdAsName = avlProperties.getNameInsteadOfId();

    protected AvlTraccarModule(AvlProperties avlProperties, AvlReportProcessor avlReportProcessor) {
        super(avlProperties, avlReportProcessor);
    }

    @Override
    protected Collection<AvlReport> getAndProcessData(String source) throws Exception {


        Map<String, BusLoadDto> occupancies = null;
        if (!Strings.isNullOrEmpty(OPC_URL)) occupancies = fetchBusLoads(OPC_URL);

        Collection<AvlReport> avlReports = new ArrayList<>();
        List<DeviceDto> devices = fetchData(source, "/devices", new TypeReference<List<DeviceDto>>() {
        });
        List<PositionDto> positions = fetchData(source, "/positions", new TypeReference<List<PositionDto>>() {
        });

        for (PositionDto result : positions) {
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

            } else {
                avlReport = new AvlReport(String.valueOf(result.getDeviceId()), "",
                                          result.getDeviceTime().getTime(),
                                          result.getLatitude(),
                                          result.getLongitude(),
                                          result.getSpeed() * (isMiles ? valueOf(1.15077945) : valueOf(1.852)).floatValue(),
                                          result.getCourse(), "TRACCAR");
            }
            avlReports.add(avlReport);
        }
        return avlReports;
    }

    private AvlReport getAvlReport(PositionDto result, DeviceDto device) {
        AvlReport avlReport;
        avlReport = new AvlReport(isIdAsName ? device.getName() : device.getUniqueId(),
                                  device.getName(),
                                  result.getDeviceTime().getTime(),
                                  result.getLatitude(),
                                  result.getLongitude(),
                                  result.getSpeed() * (isMiles ? valueOf(1.15077945) : valueOf(1.852)).floatValue(),
                                  result.getCourse(), "TRACCAR");
        return avlReport;
    }

    private AvlReport getAvlReportWithOccupancy(PositionDto result, DeviceDto device, Map<String, BusLoadDto> occupancies) {
        AvlReport avlReport;
        avlReport = new AvlReport(isIdAsName ? device.getName() : String.valueOf(device.getUniqueId()),
                                  device.getName(),
                                  result.getDeviceTime().getTime(),
                                  result.getLatitude(),
                                  result.getLongitude(),
                                  result.getSpeed() * (isMiles ? valueOf(1.15077945) : valueOf(1.852)).floatValue(),
                                  result.getCourse(),
                                  occupancies.get(device.getName()) != null ? occupancies.get(device.getName()).getCurrentCount() : -1,
                                  occupancies.get(device.getName()) != null ? occupancies.get(device.getName()).getCurrentFullness() : Float.NaN,
                                  "TRACCAR");
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
    protected List<String> getSources() {
        return List.of(avlProperties.getTraccarBaseUrl());
    }

    private <T> List<T> fetchData(String source, String uri, TypeReference<List<T>> type) throws Exception {
        IntervalTimer timer = new IntervalTimer();
        logger.info("Getting data from feed using url={}", source);

        // Create the connection
        URL url = new URL(source + uri);
        URLConnection con = url.openConnection();
        configureConnectionLifetime(con);
        configureConnectionAuthentication(con);
        con.setRequestProperty("Accept", "application/json");

        // Read the response
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            logger.debug("Time to access inputstream {} msec", timer.elapsedMsec());
        }

        ObjectMapper mapper = new ObjectMapper();
        List<T> list = mapper.readValue(response.toString(), type);

        return list;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DeviceDto {
        int id;
        String uniqueId;
        String name;
        int groupId;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PositionDto {
        int deviceId;
        Date deviceTime;
        double latitude;
        double longitude;
        float course;
        float speed;
    }
}
