package dev.hata.scheduler.common;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Idempotency cache (ADR-008 / design D8). The record key is
 * {@code idempotency:{endpoint}:{phone}:{header-value}}, so one header value
 * never collides across endpoints or phones. The cached value holds the whole
 * response snapshot, so a replay returns the original status, body and session
 * cookie without running the operation again.
 */
@Component
public class IdempotencyStorage {

    public static final String PREFIX = "idempotency:";

    private static final Duration TTL = Duration.ofHours(24);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;

    IdempotencyStorage(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Builds the namespaced record key.
     */
    public static String slot(String endpoint, String phone, String headerValue) {
        return PREFIX + endpoint + ":" + phone + ":" + headerValue;
    }

    /**
     * Returns the cached response for a slot, or null when the slot is free.
     */
    public CachedResponse load(String slot) {
        String raw = redis.opsForValue().get(slot);
        if (raw == null) {
            return null;
        }
        try {
            return MAPPER.readValue(raw, CachedResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Stores the response snapshot for 24 hours.
     */
    public void store(String slot, CachedResponse response) {
        try {
            redis.opsForValue().set(slot, MAPPER.writeValueAsString(response), TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot cache idempotent response", ex);
        }
    }

    /**
     * The response snapshot a replay returns.
     */
    public record CachedResponse(int status, String body, String setCookie) {

        public boolean hasSetCookie() {
            return setCookie != null && !setCookie.isBlank();
        }
    }
}
