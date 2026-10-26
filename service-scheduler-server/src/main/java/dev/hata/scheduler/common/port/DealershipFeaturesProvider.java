package dev.hata.scheduler.common.port;

import java.util.UUID;

import dev.hata.scheduler.common.ratelimit.RateLimitProperties;

/**
 * Shared contract for dealership feature configuration (design D7/ADR-013).
 * Resolves dynamic feature properties such as rate limits from the
 * {@code dealerships.features} JSON column with system defaults as fallback.
 */
@FunctionalInterface
public interface DealershipFeaturesProvider {

    /**
     * Resolves effective rate limits for the given dealership id.
     *
     * @param dealershipId dealership UUID, or null when resolving default limits
     * @return resolved rate limit thresholds and windows
     */
    RateLimitProperties.Resolved resolveRateLimits(UUID dealershipId);
}
