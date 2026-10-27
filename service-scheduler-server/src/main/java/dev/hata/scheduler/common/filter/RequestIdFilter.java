package dev.hata.scheduler.common.filter;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns or captures X-Request-Id, stores it in MDC, and adds it to response headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_CLIENT_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String proposed = request.getHeader(HEADER);
        String requestId = (proposed != null && !proposed.isBlank() && proposed.length() <= MAX_CLIENT_LENGTH)
                ? proposed
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        try {
            response.setHeader(HEADER, requestId);
            chain.doFilter(request, response);
        } finally {
            log.info("HTTP {} {} -> {}", request.getMethod(), request.getRequestURI(), response.getStatus());
            MDC.remove(MDC_KEY);
        }
    }
}
