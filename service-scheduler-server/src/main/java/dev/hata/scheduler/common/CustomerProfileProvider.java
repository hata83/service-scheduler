package dev.hata.scheduler.common;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared contract for customer profile management (design D4/D13). Feature
 * packages access customer profile data through this interface without
 * depending directly on the customer domain package.
 */
public interface CustomerProfileProvider {

    /**
     * Customer profile data transfer record.
     */
    record ProfileView(UUID userId, UUID dealershipId, String firstName, String lastName, String locale) {}

    /**
     * Finds profile by user id.
     */
    Optional<ProfileView> findByUserId(UUID userId);

    /**
     * Creates a new customer profile.
     */
    ProfileView createProfile(UUID userId, UUID dealershipId, String firstName, String lastName);

    /**
     * Updates first and last name.
     */
    Optional<ProfileView> updateNames(UUID userId, String firstName, String lastName);
}
