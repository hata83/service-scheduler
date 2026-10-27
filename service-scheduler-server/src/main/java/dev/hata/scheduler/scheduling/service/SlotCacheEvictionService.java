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
 * <p>Cache key pattern: {@code slot:grid:{dealershipId}:{date}:{serviceTypeId}}.
 * Bays and technicians are shared across service types, so one booking or
 * resource change on a date affects capacity for all service types
 * (SYSTEM_DESIGN §12.1).
 *
 * <h3>Eviction triggers</h3>
 * <ul>
 *   <li><strong>Date-level:</strong> booking committed, appointment cancelled/rejected/
 *       no-show, or check-in with bay or technician slot adjustment. Evicts
 *       {@code slot:grid:{dealershipId}:{date}:*}.</li>
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
     * Reads the live generation stamp for a dealership and date. The stamp is a
     * composite logical clock: (dealership-wide counter, date counter). Any
     * eviction advances one of the two halves, so a grid must match both
     * halves to be considered fresh. Returns {@code 0:0} if neither counter
     * has been set yet (no eviction observed).
     *
     * @param dealershipId the dealership scope
     * @param date         the calendar date
     * @return composite stamp string {@code "<dealership>:<date>"}
     */
    /**
     * Reads the live generation stamp for a dealership and date. The stamp is a
     * composite logical clock: (dealership-wide counter, date counter). Any
     * eviction advances one half, so a grid must match both halves to be
     * considered fresh. Returns {@code -1:-1} if a counter read fails (e.g.
     * Redis error) so callers treat the cache as unreadable and never accept a
     * stale grid whose freshness cannot be established (R14).
     *
     * @param dealershipId the dealership scope
     * @param date         the calendar date
     * @return composite stamp {@code "<dealership>:<date>"}
     */
    public String readGeneration(java.util.UUID dealershipId, java.time.LocalDate date) {
        long deal = readCounter(generationKey(dealershipId, null));
        long day = readCounter(generationKey(dealershipId, date));
        if (deal < 0 || day < 0) {
            // At least one counter is unreadable: do not trust any cached stamp
            // (a stale grid would otherwise match a default 0 and be served).
            return "-1:-1";
        }
        return deal + ":" + day;
    }

    private long readCounter(String counterKey) {
        try {
            String value = redis.opsForValue().get(counterKey);
            return (value == null || value.isBlank()) ? 0L : Long.parseLong(value);
        } catch (Exception ex) {
            log.warn("Failed to read generation counter {}: {}", counterKey, ex);
            return -1L;
        }
    }

    /**
     * Evicts all slot grid keys for a dealership on a specific date.
     *
     * <p>Use after: booking committed, appointment cancelled/rejected/no-show,
     * or check-in with bay or technician slot adjustment.
     *
     * <p>Pattern: {@code slot:grid:{dealershipId}:{date}:*}
     *
     * @param dealershipId the dealership scope
     * @param date         the affected calendar date
     */
    public void evictByDate(UUID dealershipId, LocalDate date) {
        String pattern = SLOT_GRID_PREFIX + dealershipId + ":" + date + ":*";
        bumpGeneration(dealershipId, date);
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
        // Dealership-level changes invalidate every date, so bump a single
        // dealership-wide generation counter that applies to all dated grids.
        bumpGeneration(dealershipId, null);
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

    /**
     * Generation counter key for a dealership+date. A null date means the
     * dealership-wide counter (covers all dates).
     */
    private String generationKey(UUID dealershipId, LocalDate date) {
        String prefix = "slot:gen:" + dealershipId + ":";
        return (date == null) ? prefix + "all" : prefix + date;
    }

    /**
     * Atomically advances the generation counter. Grids stamped with a value
     * below the new counter are stale and discarded on read. Best-effort: a
     * counter bump failure is logged but never blocks eviction.
     */
    private void bumpGeneration(UUID dealershipId, LocalDate date) {
        try {
            redis.opsForValue().increment(generationKey(dealershipId, date));
        } catch (Exception ex) {
            log.warn("Failed to bump generation counter for dealership={} date={}: {}", dealershipId, date, ex);
        }
    }
}
