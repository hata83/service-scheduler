package dev.hata.scheduler.common.ratelimit;

import java.time.Duration;
import java.util.Map;

import dev.hata.scheduler.common.config.JsonConfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate-limit configuration (design D7). The values load from a bundled JSON
 * resource. The {@code default} block holds the fleet limits; the
 * {@code dealerships} map holds per-dealership overrides keyed by dealership UUID.
 * The loader resolves a dealership block with the default block as fallback.
 */
@ConfigurationProperties(prefix = "app.ratelimits")
public record RateLimitProperties(String location, boolean trustForwardedFor) {

    /**
     * Reads the JSON resource and returns the resolved limit set.
     *
     * @param dealershipId dealership UUID, or null when the caller has no dealership
     */
    public Resolved resolve(String dealershipId) {
        if (location == null || location.isBlank()) {
            return defaultLimits();
        }
        Map<String, Object> root = JsonConfig.readObject(location);
        Map<String, Object> defaults = asMap(root.get("default"));
        Map<String, Object> overrides = asMap(root.get("dealerships"));
        Map<String, Object> selected = defaults;
        if (dealershipId != null && overrides.containsKey(dealershipId)) {
            selected = asMap(overrides.get(dealershipId));
        }
        return resolveFromMap(selected, defaults);
    }

    /**
     * Resolves limits from a dynamic feature map, falling back to system defaults from config.
     */
    public Resolved resolveFromMap(Map<String, Object> features) {
        return resolveFromMap(features, defaultMapFromConfig());
    }

    private Resolved resolveFromMap(Map<String, Object> selected, Map<String, Object> defaults) {
        return new Resolved(
                longValue(selected, defaults, "otpSendPerPhonePurpose", 5),
                longValue(selected, defaults, "otpSendPerIp", 50),
                longValue(selected, defaults, "otpVerifyPerIp", 50),
                longValue(selected, defaults, "registerPerIp", 20),
                secondsValue(selected, defaults, "otpSendPerPhonePurposeWindowSeconds", 600),
                secondsValue(selected, defaults, "otpSendPerIpWindowSeconds", 60),
                secondsValue(selected, defaults, "otpVerifyPerIpWindowSeconds", 60),
                secondsValue(selected, defaults, "registerPerIpWindowSeconds", 60));
    }

    /**
     * Returns fleet default rate limits from config or fallback map.
     */
    public Resolved defaultLimits() {
        if (location != null && !location.isBlank()) {
            return resolve(null);
        }
        return resolveFromMap(defaultMap(), defaultMap());
    }

    private Map<String, Object> defaultMapFromConfig() {
        if (location != null && !location.isBlank()) {
            try {
                Map<String, Object> root = JsonConfig.readObject(location);
                if (root != null && root.containsKey("default")) {
                    return asMap(root.get("default"));
                }
            } catch (Exception ignored) {
                // fall back to static defaultMap
            }
        }
        return defaultMap();
    }

    private static Map<String, Object> defaultMap() {
        return Map.of(
                "otpSendPerPhonePurpose", 5L,
                "otpSendPerIp", 50L,
                "otpVerifyPerIp", 50L,
                "registerPerIp", 20L,
                "otpSendPerPhonePurposeWindowSeconds", 600L,
                "otpSendPerIpWindowSeconds", 60L,
                "otpVerifyPerIpWindowSeconds", 60L,
                "registerPerIpWindowSeconds", 60L);
    }

    private static long longValue(
            Map<String, Object> selected, Map<String, Object> defaults, String key, long fallback) {
        Object value = selected.get(key);
        if (value == null) {
            value = defaults.get(key);
        }
        return value == null ? fallback : ((Number) value).longValue();
    }

    private static long secondsValue(
            Map<String, Object> selected, Map<String, Object> defaults, String key, long fallback) {
        Object value = selected.get(key);
        if (value == null) {
            value = defaults.get(key);
        }
        return value == null ? fallback : ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value == null ? Map.of() : (Map<String, Object>) value;
    }

    /**
     * The effective limits for one request.
     */
    public record Resolved(
            long otpSendPerPhonePurpose,
            long otpSendPerIp,
            long otpVerifyPerIp,
            long registerPerIp,
            long otpSendPerPhonePurposeWindowSeconds,
            long otpSendPerIpWindowSeconds,
            long otpVerifyPerIpWindowSeconds,
            long registerPerIpWindowSeconds) {

        public Duration otpSendPerPhonePurposeWindow() {
            return Duration.ofSeconds(otpSendPerPhonePurposeWindowSeconds);
        }

        public Duration otpSendPerIpWindow() {
            return Duration.ofSeconds(otpSendPerIpWindowSeconds);
        }

        public Duration otpVerifyPerIpWindow() {
            return Duration.ofSeconds(otpVerifyPerIpWindowSeconds);
        }

        public Duration registerPerIpWindow() {
            return Duration.ofSeconds(registerPerIpWindowSeconds);
        }
    }
}
