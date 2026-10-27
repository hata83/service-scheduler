package dev.hata.scheduler.infra.redis;

import java.time.Duration;
import java.util.List;

import dev.hata.scheduler.common.ratelimit.WindowCounter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis adapter for the fixed-window counter (ADR-007 / design D7).
 */
@Component
public class RedisWindowCounter implements WindowCounter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisWindowCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, long limit, Duration window) {
        Long count = redis.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(window.toSeconds()));
        return count != null && count <= limit;
    }
}
