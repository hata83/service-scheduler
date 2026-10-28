package dev.hata.scheduler.common.provider;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for retrieving dealership configuration without importing the dealership package.
 *
 * <p>The {@code scheduling} package cannot import {@code dealership} (ArchUnit ADR-009).
 * This port carries the dealership fields the slot grid algorithm needs.
 * The {@code dealership} package provides the implementation.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface DealershipConfigPort {

    /**
     * Finds the scheduling configuration for a dealership.
     *
     * @param dealershipId the dealership identifier
     * @return the configuration, or empty when the dealership is not found or inactive
     */
    Optional<DealershipConfig> findConfig(UUID dealershipId);

    /**
     * Immutable value object carrying the scheduling fields of a dealership.
     *
     * @param id                    dealership identifier
     * @param timezone              IANA timezone string (e.g. {@code Asia/Ho_Chi_Minh})
     * @param slotIntervalMinutes   grid step in minutes (15, 30, 45, 60, 90, or 120)
     * @param openTime              daily opening time
     * @param closeTime             daily closing time
     * @param lunchStart            lunch break start, or {@code null} when none
     * @param lunchEnd              lunch break end, or {@code null} when none
     * @param maxAdvanceBookingDays maximum lookahead days for slot queries
     * @param slotCacheTtlSeconds   Redis TTL for slot grid keys
     */
    record DealershipConfig(
            UUID id,
            String timezone,
            int slotIntervalMinutes,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime lunchStart,
            LocalTime lunchEnd,
            int maxAdvanceBookingDays,
            int slotCacheTtlSeconds) {}
}
