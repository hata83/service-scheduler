package dev.hata.scheduler.common.port;

import java.time.Duration;

/**
 * Counts the hits of a time window (ADR-007 / design D7). The
 * implementation keeps the counter in Redis, so the limit holds across pods.
 */
@FunctionalInterface
public interface WindowCounter {

    /**
     * Counts one hit and reports whether the caller stays under the limit.
     *
     * @param key the counter key
     * @param limit the maximum hits inside the window
     * @param window the window length
     */
    boolean tryAcquire(String key, long limit, Duration window);
}
