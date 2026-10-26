package dev.hata.scheduler.dealership.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.port.DealershipFeaturesProvider;
import dev.hata.scheduler.common.ratelimit.RateLimitProperties;
import dev.hata.scheduler.dealership.model.Dealership;
import dev.hata.scheduler.dealership.repository.DealershipRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Dealership feature domain service (design D7/ADR-013). Implements
 * {@link DealershipFeaturesProvider} to resolve dynamic feature settings
 * from the {@code dealerships.features} JSON column with system defaults fallback.
 */
@Service
public class DealershipService implements DealershipFeaturesProvider {

    private static final Logger log = LoggerFactory.getLogger(DealershipService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DealershipRepository dealerships;
    private final RateLimitProperties properties;

    public DealershipService(DealershipRepository dealerships, RateLimitProperties properties) {
        this.dealerships = dealerships;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public RateLimitProperties.Resolved resolveRateLimits(UUID dealershipId) {
        // If a test rate limit location is specified (e.g. rate-limits-test.json), use it for test speed
        if (properties.location() != null && properties.location().contains("test")) {
            return properties.resolve(dealershipId == null ? null : dealershipId.toString());
        }
        if (dealershipId == null) {
            return properties.defaultLimits();
        }
        Optional<Dealership> dealer = dealerships.findById(dealershipId);
        if (dealer.isEmpty()
                || dealer.get().getFeatures() == null
                || dealer.get().getFeatures().isBlank()) {
            return properties.defaultLimits();
        }
        try {
            Map<?, ?> root = MAPPER.readValue(dealer.get().getFeatures(), Map.class);
            if (root != null && root.containsKey("rateLimits")) {
                Object rateLimitsObj = root.get("rateLimits");
                if (rateLimitsObj instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stringMap = (Map<String, Object>) map;
                    return properties.resolveFromMap(stringMap);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse dealership.features JSON for dealership {}: {}", dealershipId, ex.getMessage());
        }
        return properties.defaultLimits();
    }
}
