package dev.hata.scheduler.infra.redis;

import java.time.Duration;

import dev.hata.scheduler.common.idempotency.IdempotencyStore;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis adapter for the idempotency cache (ADR-008 / design D8).
 */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    public static final String PREFIX = "idempotency:";

    private static final Duration TTL = Duration.ofHours(24);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StringRedisTemplate redis;

    public RedisIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String slot(String endpoint, String phone, String headerValue) {
        return PREFIX + endpoint + ":" + phone + ":" + headerValue;
    }

    @Override
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

    @Override
    public void store(String slot, CachedResponse response) {
        try {
            redis.opsForValue().set(slot, MAPPER.writeValueAsString(response), TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot cache idempotent response", ex);
        }
    }
}
