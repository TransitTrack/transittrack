/* (C)2023 */
package org.transitclock.api.utils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) {
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable ex) {
            logger.error("Filter caught exception: ", ex);
            throw new RuntimeException(ex);
        }
    }
}
