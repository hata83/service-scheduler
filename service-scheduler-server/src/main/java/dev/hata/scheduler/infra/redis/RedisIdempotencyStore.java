package dev.hata.scheduler.infra.redis;

import java.time.Duration;
import java.util.List;

import dev.hata.scheduler.common.idempotency.IdempotencyStore;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis adapter for the idempotency cache (ADR-008 / design D8).
 * Bounded by the 8-hour JWT TTL and uses owner-scoped atomic lock release.
 */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    public static final String PREFIX = "idempotency:";

    private static final Duration TTL = Duration.ofHours(8);
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "  return redis.call('DEL', KEYS[1]) "
                    + "end "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public String slot(String endpoint, String scope, String headerValue) {
        return "resp:" + PREFIX + endpoint + ":" + scope + ":" + headerValue;
    }

    @Override
    public boolean tryClaim(String slot, String ownerToken) {
        String lockSlot = "lock:" + slot;
        Boolean acquired = redis.opsForValue().setIfAbsent(lockSlot, ownerToken, LOCK_TTL);
        return !Boolean.FALSE.equals(acquired);
    }

    @Override
    public void releaseClaim(String slot, String ownerToken) {
        String lockSlot = "lock:" + slot;
        redis.execute(RELEASE_LOCK_SCRIPT, List.of(lockSlot), ownerToken);
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
