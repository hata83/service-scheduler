package dev.hata.scheduler.common.provider;

import java.util.UUID;

/**
 * Shared provider contract for vehicle category validation across features.
 */
@FunctionalInterface
public interface VehicleCategoryValidationProvider {

    /**
     * Checks whether a vehicle category exists and belongs to the given dealership.
     */
    boolean existsCategoryInDealership(UUID dealershipId, UUID categoryId);
}
