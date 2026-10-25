import java.io.IOException;

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
 * Required `X-Idempotency-Key` header (ADR-008) for all writes. The filter
 * validates the header presence, stores it in MDC for logging, and delegates
 * the actual idempotency caching to the controller/service layer.
 * The RFC-7807 problem+json payload matches `openspec/config.yaml` error
 * conventions.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    public static final String HEADER = "X-Idempotency-Key";
    public static final String MDC_KEY = "idempotencyKey";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            sendProblem(
                    response,
                    400,
                    "Idempotency key missing",
                    "Request must include an X-Idempotency-Key header to prevent duplicate writes.");
            return;
        }
        MDC.put(MDC_KEY, key);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private void sendProblem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        String body = String.format(
                "{\"type\":\"/errors/%d\",\"title\":\"%s\",\"detail\":\"%s\",\"timestamp\":\"%s\"}",
                status, title, detail, java.time.Instant.now());
        response.getWriter().write(body);
    }
}
