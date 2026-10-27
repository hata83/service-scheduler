package dev.hata.scheduler.common.ratelimit;

import java.time.Duration;

/**
 * Port for atomic fixed-window request rate limiting.
 */
@FunctionalInterface
public interface WindowCounter {

    /**
     * Increments the window counter and verifies whether it stays within the allowed limit.
     *
     * @param key unique counter key
     * @param limit maximum allowed hits in the window
     * @param window duration of the rate-limiting window
     * @return true if allowed, false if limit exceeded
     */
    boolean tryAcquire(String key, long limit, Duration window);
}
