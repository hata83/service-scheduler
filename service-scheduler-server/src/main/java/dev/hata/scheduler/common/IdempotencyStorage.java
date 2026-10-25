package dev.hata.scheduler.common;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Dealership-scoped idempotency slots (ADR-008 / D8). Key:
 * idempotency:{endpoint}:{phone}:{header-value}. TTL 24h.
 */
@Component
public class IdempotencyStorage {

    private final StringRedisTemplate redis;

    IdempotencyStorage(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void store(String slot, String value, long ttlSeconds) {
        redis.opsForValue().set(slot, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isConsumed(String slot) {
        return redis.opsForValue().get(slot) != null;
    }
}
