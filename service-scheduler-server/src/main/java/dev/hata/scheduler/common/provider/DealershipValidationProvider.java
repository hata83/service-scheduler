package dev.hata.scheduler.common.provider;

import java.util.UUID;

/**
 * Shared provider contract for dealership validation across features.
 */
@FunctionalInterface
public interface DealershipValidationProvider {

    /**
     * Checks whether an active dealership exists with the given id.
     */
    boolean existsActiveDealership(UUID dealershipId);
}
