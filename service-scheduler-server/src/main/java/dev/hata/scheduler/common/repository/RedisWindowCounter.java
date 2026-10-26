package dev.hata.scheduler.common.repository;

import java.time.Duration;
import java.util.List;

import dev.hata.scheduler.common.port.WindowCounter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis adapter for the fixed-window counter (ADR-007 / design D7). The script
 * increments the counter and sets the TTL on the first hit of a window, so both
 * steps run in one server-side call.
 */
@Component
public class RedisWindowCounter implements WindowCounter {

    /**
     * Increments KEYS[1] and applies the TTL when the counter starts a window.
     * Returns the new count.
     */
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;

    RedisWindowCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Counts one hit and reports whether the caller stays under the limit.
     *
     * @param key counter key
     * @param limit maximum hits inside the window
     * @param window window length
     */
    @Override
    public boolean tryAcquire(String key, long limit, Duration window) {
        Long count = redis.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(window.toSeconds()));
        return count != null && count <= limit;
    }
}
