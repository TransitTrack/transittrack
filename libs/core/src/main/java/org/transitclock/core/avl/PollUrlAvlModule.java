/* (C)2023 */
package org.transitclock.core.avl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.springframework.scheduling.annotation.Scheduled;

import org.transitclock.domain.structs.AvlReport;
import org.transitclock.properties.AvlProperties;
import org.transitclock.utils.IntervalTimer;

/**
 * Subclass of AvlModule to be used when reading AVL data from a feed. Calls the abstract method
 * getAndProcessData() for the subclass to actually get data from the feed. The getAndProcessData()
 * should call processAvlReport(avlReport) for each AVL report read in. If in JMS mode then it
 * outputs the data to the appropriate JMS topic so that it can be read from an AvlClient. If not in
 * JMS mode then uses a BoundedExecutor with multiple threads to directly call AvlClient.run().
 *
 * @author Michael Smith (michael@transitclock.org)
 */
@Slf4j
public abstract class PollUrlAvlModule extends AvlModule {
    protected PollUrlAvlModule(AvlProperties avlProperties, AvlReportProcessor avlReportProcessor) {
        super(avlProperties, avlReportProcessor);
    }

    /**
     * Feed specific URL to use when accessing data. Will often be overridden by subclass.
     *
     * @return
     */
    protected abstract List<String> getSources();

    /**
     * Override this method if AVL feed needs to specify header info
     *
     * @param con
     */
    protected void setRequestHeaders(URLConnection con) {

    }

    /**
     * Actually processes the data from the InputStream. Called by getAndProcessData(). Should be
     * overwritten unless getAndProcessData() is overwritten by superclass.
     *
     * @param url The URL to source for AVL data
     * @return List of AvlReports read in
     * @throws Exception Throws a generic exception since the processing is done in the abstract
     *     method processData() and it could throw any type of exception since we don't really know
     *     how the AVL feed will be processed.
     */
    protected abstract Collection<AvlReport> getAndProcessData(String url) throws Exception;

    /**
     * Converts the input stream into a JSON string. Useful for when processing a JSON feed.
     *
     * @param in
     * @return the JSON string
     * @throws IOException
     * @throws JSONException
     */
    protected String getJsonString(InputStream in) throws IOException, JSONException {
        BufferedReader streamReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder responseStrBuilder = new StringBuilder();

        String inputStr;
        while ((inputStr = streamReader.readLine()) != null) responseStrBuilder.append(inputStr);

        String responseStr = responseStrBuilder.toString();
        logger.debug("JSON={}", responseStr);
        return responseStr;
    }

    /**
     * Actually reads data from feed and processes it by opening up a URL specified by getUrl() and
     * then reading the contents. Calls the abstract method processData() to actually process the
     * input stream.
     *
     * <p>This method needs to be overwritten if not real data from a URL
     *
     * @throws Exception Throws a generic exception since the processing is done in the abstract
     *     method processData() and it could throw any type of exception since we don't really know
     *     how the AVL feed will be processed.
     */
    private void fetchData() throws Exception {

        IntervalTimer timer = new IntervalTimer();

        List<String> sources = getSources();

        for (var source: sources) {

                Collection<AvlReport> avlReportsReadIn = getAndProcessData(source);
                logger.debug("Time to parse document {} msec", timer.elapsedMsec());

                // Process all the reports read in
                if (avlProperties.getShouldProcessAvl()) {
                    processAvlReports(avlReportsReadIn);
                }

        }
    }

    protected void configureConnectionAuthentication(URLConnection con) {
        // If authentication being used then set user and password
        if (avlProperties.getAuthenticationUser() != null && avlProperties.getAuthenticationPassword() != null) {
            String authString = avlProperties.getAuthenticationUser() + ":" + avlProperties.getAuthenticationPassword();
            byte[] authEncBytes = Base64.getEncoder().encode(authString.getBytes());
            String authStringEnc = new String(authEncBytes);
            con.setRequestProperty("Authorization", "Basic " + authStringEnc);
        }
    }

    protected void configureConnectionLifetime(URLConnection con) {
        int timeoutMsec = avlProperties.getFeedTimeoutInMSecs();
        con.setConnectTimeout(timeoutMsec);
        con.setReadTimeout(timeoutMsec);
    }

    /**
     * Does all of the work for the class. Runs forever and reads in AVL data from feed and
     * processes it.
     */
    @Scheduled(fixedRateString = "${transitclock.avl.feedTimeoutInMSecs:15000}")
    public void run() {
        try {
            // Process data
            fetchData();
        } catch (SocketTimeoutException e) {
            logger.error(
                    "Error accessing AVL feed using URL={} with a timeout of {} msec.",
                    getSources(),
                    avlProperties.getFeedPollingRateSecs(),
                    e);
        } catch (Exception e) {
            logger.error("Error accessing AVL feed using URL={}.", getSources(), e);
        }
    }
}
