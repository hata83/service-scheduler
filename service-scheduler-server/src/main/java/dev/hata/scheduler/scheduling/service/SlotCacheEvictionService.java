package dev.hata.scheduler.scheduling.service;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Evicts slot availability grid caches from Redis.
 *
 * <p>Cache key pattern: {@code slot:grid:{dealershipId}:{serviceTypeId}:{date}}.
 * Bays and technicians are shared across service types, so one booking or
 * resource change on a date affects capacity for all service types
 * (SYSTEM_DESIGN §12.1).
 *
 * <h3>Eviction triggers</h3>
 * <ul>
 *   <li><strong>Date-level:</strong> booking committed, appointment cancelled/rejected/
 *       no-show, or check-in with bay or technician slot adjustment. Evicts
 *       {@code slot:grid:{dealershipId}:*:{date}}.</li>
 *   <li><strong>Dealership-level:</strong> bay added/removed/deactivated, technician
 *       added/removed/skill changed, or dealership config updated. Evicts
 *       {@code slot:grid:{dealershipId}:*}.</li>
 * </ul>
 *
 * <p>When a manager manually reassigns a bay or technician at check-in, the
 * date-level eviction fires. The next availability query recomputes the grid
 * from live database state and stores the fresh result back in Redis.
 *
 * <!-- ponytail: Redis KEYS is O(N) and blocks. Acceptable for single-shard
 *      dev/test. Production upgrade: replace with SCAN MATCH cursor loop. -->
 */
@Service
public class SlotCacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(SlotCacheEvictionService.class);
    private static final String SLOT_GRID_PREFIX = "slot:grid:";

    private final StringRedisTemplate redis;

    public SlotCacheEvictionService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Evicts all slot grid keys for a dealership on a specific date.
     *
     * <p>Use after: booking committed, appointment cancelled/rejected/no-show,
     * or check-in with bay or technician slot adjustment.
     *
     * <p>Pattern: {@code slot:grid:{dealershipId}:*:{date}}
     *
     * @param dealershipId the dealership scope
     * @param date         the affected calendar date
     */
    public void evictByDate(UUID dealershipId, LocalDate date) {
        String pattern = SLOT_GRID_PREFIX + dealershipId + ":*:" + date;
        deleteByPattern(pattern, "date-level for dealership=" + dealershipId + " date=" + date);
    }

    /**
     * Evicts all slot grid keys for a dealership across all dates and service types.
     *
     * <p>Use after: bay added/removed/status changed, technician added/removed/
     * skill changed, or dealership config updated.
     *
     * <p>Pattern: {@code slot:grid:{dealershipId}:*}
     *
     * @param dealershipId the dealership scope
     */
    public void evictByDealership(UUID dealershipId) {
        String pattern = SLOT_GRID_PREFIX + dealershipId + ":*";
        deleteByPattern(pattern, "dealership-level for dealership=" + dealershipId);
    }

    private void deleteByPattern(String pattern, String context) {
        try {
            Set<String> keys = redis.keys(pattern);
            if (keys == null || keys.isEmpty()) {
                log.debug("No slot grid keys to evict for {}", context);
                return;
            }
            redis.delete(keys);
            log.info("Evicted {} slot grid key(s) for {}", keys.size(), context);
        } catch (Exception ex) {
            log.warn("Failed to evict slot grid keys for {}: {}", context, ex);
        }
    }
}
