package dev.hata.scheduler.common.provider;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Shared provider contract for evicting cached slot availability grids upon booking changes.
 * Concrete providers own the cache eviction mechanics.
 */
@FunctionalInterface
public interface SlotCacheEvictionProvider {

    void evictSlots(UUID dealershipId, LocalDate date);
}
