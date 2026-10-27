package dev.hata.scheduler.scheduling.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipConfigPort;
import dev.hata.scheduler.common.provider.DealershipConfigPort.DealershipConfig;
import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;
import dev.hata.scheduler.scheduling.model.ServiceType;
import dev.hata.scheduler.scheduling.repository.ServiceTypeRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Domain service for slot availability: query, compute, and cache eviction.
 *
 * <p>This is the single entry point for all slot grid operations.
 * Callers only need this one service for reads and evictions.
 *
 * <h3>Cache strategy (SYSTEM_DESIGN §12)</h3>
 * <ol>
 *   <li>Check Redis at {@code slot:grid:{dealershipId}:{serviceTypeId}:{date}}.</li>
 *   <li>On hit, deserialize and return immediately.</li>
 *   <li>On miss, validate the date range, compute from live DB state via
 *       {@link SlotGridComputeService}, write to Redis, and return.</li>
 * </ol>
 *
 * <h3>Eviction strategy (SYSTEM_DESIGN §12.1)</h3>
 * <ul>
 *   <li>{@link #evictSlotsByDate} — call after a booking, cancellation, no-show,
 *       or check-in bay/technician adjustment on a specific date. Clears all
 *       service-type grids for that dealership and date so the next query
 *       recomputes from fresh database state.</li>
 *   <li>{@link #evictSlotsByDealership} — call after bay inventory, technician
 *       roster, or dealership config changes. Clears all grids for the dealership.</li>
 * </ul>
 *
 * <p>Uses {@link DealershipConfigPort} to read dealership settings without
 * importing the dealership package (ArchUnit ADR-009).
 */
@Service
public class SlotAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(SlotAvailabilityService.class);
    private static final String SLOT_GRID_KEY_PREFIX = "slot:grid:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DealershipConfigPort dealershipConfig;
    private final ServiceTypeRepository serviceTypeRepository;
    private final SlotGridComputeService gridCompute;
    private final SlotCacheEvictionService eviction;

    public SlotAvailabilityService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            DealershipConfigPort dealershipConfig,
            ServiceTypeRepository serviceTypeRepository,
            SlotGridComputeService gridCompute,
            SlotCacheEvictionService eviction) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.dealershipConfig = dealershipConfig;
        this.serviceTypeRepository = serviceTypeRepository;
        this.gridCompute = gridCompute;
        this.eviction = eviction;
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns available time slots for a dealership, service type, and date.
     *
     * <p>Returns the cached grid on hit. Computes and caches the grid on miss.
     * Returns an empty list for past dates. Throws {@link SlotDateOutOfRangeException}
     * when the date exceeds the dealership advance booking window.
     *
     * @param dealershipId  dealership identifier
     * @param serviceTypeId service type identifier
     * @param date          the calendar date requested
     * @return slot windows; only slots where both bay and technician gates pass
     * @throws SlotDateOutOfRangeException when date &gt; today + maxAdvanceBookingDays
     */
    public List<SlotAvailabilityResponse> getCachedAvailableSlots(
            UUID dealershipId, UUID serviceTypeId, LocalDate date) {

        String key = SLOT_GRID_KEY_PREFIX + dealershipId + ":" + serviceTypeId + ":" + date;

        // Step 1: cache read.
        Optional<List<SlotAvailabilityResponse>> cached = readFromCache(key);
        if (cached.isPresent()) {
            // Finding #1: purge slots whose start time has already elapsed (the grid
            // was computed before the slot started) so they are not resold.
            return filterElapsed(cached.get());
        }

        // Step 2: load dealership config and validate date range.
        DealershipConfig config = dealershipConfig.findConfig(dealershipId).orElse(null);
        if (config == null) {
            log.warn("Dealership config not found for slot grid: {}", dealershipId);
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        LocalDate today = now.atZone(java.time.ZoneId.of(config.timezone())).toLocalDate();

        if (date.isBefore(today)) {
            return Collections.emptyList();
        }

        if (date.isAfter(today.plusDays(config.maxAdvanceBookingDays()))) {
            throw new SlotDateOutOfRangeException("Date " + date + " exceeds the maximum advance booking window of "
                    + config.maxAdvanceBookingDays() + " days from today.");
        }

        // Validate the service type belongs to the dealership and is active before
        // computing. A service type from another dealership or a deactivated one
        // must never produce or cache a grid (Finding #10).
        ServiceType serviceType = loadActiveServiceType(dealershipId, serviceTypeId);
        if (serviceType == null) {
            return Collections.emptyList();
        }

        // Step 3: compute the grid.
        List<SlotAvailabilityResponse> slots = gridCompute.compute(config, serviceType, date, now);

        // Step 4: write to cache.
        writeToCache(key, slots, config.slotCacheTtlSeconds());

        return slots;
    }

    /**
     * Loads a service type only when it belongs to the requested dealership and is
     * active. Returns {@code null} for unknown, cross-dealership, or inactive types.
     */
    private ServiceType loadActiveServiceType(UUID dealershipId, UUID serviceTypeId) {
        ServiceType serviceType = serviceTypeRepository.findById(serviceTypeId).orElse(null);
        if (serviceType == null) {
            log.warn("ServiceType not found for slot grid: {}", serviceTypeId);
            return null;
        }
        if (!dealershipId.equals(serviceType.getDealershipId())) {
            log.warn("ServiceType {} does not belong to dealership {} — refusing grid", serviceTypeId, dealershipId);
            return null;
        }
        if (!serviceType.isActive()) {
            log.warn("ServiceType {} is inactive — refusing grid", serviceTypeId);
            return null;
        }
        return serviceType;
    }

    /**
     * Removes slots whose start time has already elapsed. A grid cached before a
     * slot started must not keep offering that slot, and a grid retained past
     * midnight must not return slots for a date that is now in the past.
     */
    private List<SlotAvailabilityResponse> filterElapsed(List<SlotAvailabilityResponse> slots) {
        java.time.OffsetDateTime now = java.time.Instant.now().atOffset(java.time.ZoneOffset.UTC);
        return slots.stream().filter(s -> s.startTime().isAfter(now)).toList();
    }

    // -------------------------------------------------------------------------
    // Eviction
    // -------------------------------------------------------------------------

    /**
     * Evicts all cached slot grids for a dealership on a specific date.
     *
     * <p>Call this after:
     * <ul>
     *   <li>A booking is committed.</li>
     *   <li>An appointment is cancelled, rejected, or marked no-show.</li>
     *   <li>A manager adjusts the bay or technician assignment at check-in.</li>
     * </ul>
     *
     * <p>The next call to {@link #getCachedAvailableSlots} for that date will
     * recompute the grid from current database state and cache the result.
     *
     * @param dealershipId the dealership scope
     * @param date         the date whose grids must be invalidated
     */
    public void evictSlotsByDate(UUID dealershipId, LocalDate date) {
        eviction.evictByDate(dealershipId, date);
    }

    /**
     * Evicts all cached slot grids for a dealership across all dates and service types.
     *
     * <p>Call this after:
     * <ul>
     *   <li>A workshop bay is added, removed, or deactivated.</li>
     *   <li>A technician is added, removed, or their skills change.</li>
     *   <li>Dealership operating hours or slot interval configuration changes.</li>
     * </ul>
     *
     * @param dealershipId the dealership scope
     */
    public void evictSlotsByDealership(UUID dealershipId) {
        eviction.evictByDealership(dealershipId);
    }

    // -------------------------------------------------------------------------
    // Cache helpers
    // -------------------------------------------------------------------------

    private Optional<List<SlotAvailabilityResponse>> readFromCache(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<SlotAvailabilityResponse> slots =
                    objectMapper.readValue(json, new TypeReference<List<SlotAvailabilityResponse>>() {});
            return Optional.of(slots);
        } catch (Exception ex) {
            log.warn("Failed to read slot grid from cache key {}: {}", key, ex);
            return Optional.empty();
        }
    }

    private void writeToCache(String key, List<SlotAvailabilityResponse> slots, int ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(slots);
            redis.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            log.warn("Failed to write slot grid to cache key {}: {}", key, ex);
        }
    }
}
