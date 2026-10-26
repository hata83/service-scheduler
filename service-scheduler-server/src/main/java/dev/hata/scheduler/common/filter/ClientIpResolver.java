package dev.hata.scheduler.common.filter;

import dev.hata.scheduler.common.ratelimit.RateLimitProperties;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Extracts caller client IP from HTTP requests respecting configured proxy trust policy.
 * When {@code trust-forwarded-for} is enabled and the request originates from a trusted
 * loopback/private proxy, inspects {@code X-Forwarded-For} from right to left and selects
 * the first non-trusted-proxy address so clients cannot spoof leftmost headers.
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
        String remoteAddr = request.getRemoteAddr();
        if (properties.trustForwardedFor() && isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                for (int i = parts.length - 1; i >= 0; i--) {
                    String candidate = parts[i].trim();
                    if (!candidate.isEmpty() && !isTrustedProxy(candidate)) {
                        return candidate;
                    }
                }
                String first = parts[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        return remoteAddr;
    }

    private static boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return true;
        }
        String trimmed = ip.trim();
        return "127.0.0.1".equals(trimmed)
                || "::1".equals(trimmed)
                || "0:0:0:0:0:0:0:1".equals(trimmed)
                || trimmed.startsWith("10.")
                || trimmed.startsWith("192.168.")
                || trimmed.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }
}
