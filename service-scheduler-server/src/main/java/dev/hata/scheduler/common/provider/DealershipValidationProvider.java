package dev.hata.scheduler.common.provider;

import java.util.UUID;

/**
 * Shared provider contract for dealership validation across features.
 */
public interface DealershipValidationProvider {

    /**
     * Checks whether an active dealership exists with the given id.
     */
    boolean existsActiveDealership(UUID dealershipId);

    /**
     * Checks whether automatic confirmation is enabled for the dealership.
     */
    boolean isAutoConfirmEnabled(UUID dealershipId);

    /**
     * Returns the configured timezone for the dealership.
     */
    String getDealershipTimezone(UUID dealershipId);

    /**
     * Checks whether an active workshop bay belongs to the dealership.
     */
    boolean existsActiveBay(UUID dealershipId, UUID bayId);
}
