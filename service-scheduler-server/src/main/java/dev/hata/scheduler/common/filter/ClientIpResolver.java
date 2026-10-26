package dev.hata.scheduler.common.filter;

import dev.hata.scheduler.common.ratelimit.RateLimitProperties;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Extracts caller client IP from HTTP requests respecting configured proxy trust policy.
 */
@Component
public class ClientIpResolver {

    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves the client IP address from the given HTTP request.
     */
    public String resolve(HttpServletRequest request) {
        if (properties.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
