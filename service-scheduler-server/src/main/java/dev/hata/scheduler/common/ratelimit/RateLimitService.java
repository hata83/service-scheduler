package dev.hata.scheduler.common.ratelimit;

import dev.hata.scheduler.common.exception.AuthException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Generic rate-limiting service backed by {@link WindowCounter} and YAML-configured
 * {@link RateLimitProperties} (ADR-007 / design D7).
 */
@Service
public class RateLimitService {

    private final WindowCounter counter;
    private final RateLimitProperties properties;

    public RateLimitService(WindowCounter counter, RateLimitProperties properties) {
        this.counter = counter;
        this.properties = properties;
    }

    /**
     * Evaluates a rate-limit rule for the given policy bucket and subject key.
     */
    public void checkLimit(String policyName, String subjectKey, String detail) {
        RateLimitProperties.Policy policy = properties.policy(policyName);
        String redisKey = "ratelimit:" + policyName + ":" + subjectKey;
        if (!counter.tryAcquire(redisKey, policy.limit(), policy.window())) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", detail);
        }
    }
}
