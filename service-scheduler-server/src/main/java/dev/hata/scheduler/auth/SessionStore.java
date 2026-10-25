package dev.hata.scheduler.auth;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis session store (ADR-005). Each session lives under
 * {@code auth:session:{jti}} with the token TTL. Logout deletes the record,
 * which revokes the session even when the token itself is still valid.
 */
@Component
public class SessionStore {

    public static final String PREFIX = "auth:session:";

    private final StringRedisTemplate redis;

    SessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Stores the session record.
     */
    public void save(String jti, String subject, Duration ttl) {
        redis.opsForValue().set(PREFIX + jti, subject, ttl);
    }

    /**
     * Reports whether the session record still exists.
     */
    public boolean exists(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }

    /**
     * Deletes the session record. Logout calls it.
     */
    public void revoke(String jti) {
        redis.delete(PREFIX + jti);
    }
}
