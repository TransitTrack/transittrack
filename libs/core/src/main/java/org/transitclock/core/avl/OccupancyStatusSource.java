package org.transitclock.core.avl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;

@Slf4j
class OccupancyStatusSource {

    public static Map<String, BusLoadDto> fetchBusLoads(String urlStr) {
        try {
            logger.debug("Getting URL={}", urlStr);
            URL url = URI.create(urlStr).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new HttpException("Failed : HTTP error code : " + responseCode);
            }
            // Read
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            connection.disconnect();
            in.close();

            // Convert
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, BusLoadDto> mappedResponse = objectMapper.readValue(response.toString(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, BusLoadDto.class));
            logger.debug("Fetched info of bus occupancy for {} buses", mappedResponse.size() - 1);

            return mappedResponse;

        } catch (Exception ex) {
            logger.error("Exception while fetching bus loads: ", ex);
            return null;
        }
    }

    @Getter
    static class BusLoadDto {
        @JsonProperty("vehicleName")
        private String vehicleName;
        @JsonProperty("currentCount")
        private int currentCount;
        @JsonProperty("currentFullness")
        private float currentFullness;
        @JsonProperty("timestamp")
        private long timestamp;
    }
}
