package dev.hata.scheduler.common.ratelimit;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate limit configuration bound from {@code app.ratelimits} in YAML profile files
 * (design D7 / ADR-007).
 */
@ConfigurationProperties(prefix = "app.ratelimits")
public record RateLimitProperties(boolean trustForwardedFor, Map<String, Policy> policies) {

    public record Policy(long limit, Duration window) {}

    private static final Map<String, Policy> DEFAULTS = Map.of(
            "otp-send-phone", new Policy(5, Duration.ofMinutes(10)),
            "otp-send-ip", new Policy(30, Duration.ofMinutes(1)),
            "otp-verify-ip", new Policy(10, Duration.ofMinutes(1)),
            "register-ip", new Policy(10, Duration.ofMinutes(1)));

    /**
     * Resolves the rate-limit policy for the given bucket name, falling back to defaults.
     */
    public Policy policy(String name) {
        if (policies != null && policies.containsKey(name)) {
            return policies.get(name);
        }
        return DEFAULTS.getOrDefault(name, new Policy(10, Duration.ofMinutes(1)));
    }
}
