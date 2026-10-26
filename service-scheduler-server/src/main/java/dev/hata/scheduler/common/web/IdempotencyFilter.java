package dev.hata.scheduler.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the required {@code X-Idempotency-Key} header on write endpoints
 * (ADR-008). The filter rejects a write without the header with 400 and stores
 * the key in MDC for logging. The service layer owns the cached replay.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Idempotency-Key";
    public static final String MDC_KEY = "idempotencyKey";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (requiresKey(request)) {
            String key = request.getHeader(HEADER);
            if (key == null || key.isBlank()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/problem+json");
                response.getWriter()
                        .write("{\"type\":\"/errors/400\",\"title\":\"Idempotency key missing\","
                                + "\"status\":400,\"detail\":\"Request must include an X-Idempotency-Key header.\","
                                + "\"instance\":\"" + request.getRequestURI() + "\"}");
                return;
            }
            MDC.put(MDC_KEY, key);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private boolean requiresKey(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PATCH".equals(method) && !"PUT".equals(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.startsWith("/api/v1/auth/") && !uri.endsWith("/logout");
    }
}
