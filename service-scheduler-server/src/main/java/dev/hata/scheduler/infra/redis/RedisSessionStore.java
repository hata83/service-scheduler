package dev.hata.scheduler.infra.redis;

import java.time.Duration;

import dev.hata.scheduler.auth.application.SessionStore;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis adapter for the session records (ADR-005). Each session lives under
 * {@code auth:session:{jti}} with the token TTL. Logout deletes the record,
 * which revokes the session even when the token itself is still valid.
 */
@Component
public class RedisSessionStore implements SessionStore {

    public static final String PREFIX = "auth:session:";

    private final StringRedisTemplate redis;

    RedisSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String jti, String subject, Duration ttl) {
        redis.opsForValue().set(PREFIX + jti, subject, ttl);
    }

    @Override
    public boolean exists(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }

    @Override
    public void revoke(String jti) {
        redis.delete(PREFIX + jti);
    }
}
