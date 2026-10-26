package dev.hata.scheduler.common.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared contract for global customer profile management (design D4/D13). Feature
 * packages access customer profile data through this interface without
 * depending directly on the customer domain package.
 */
public interface CustomerProfileProvider {

    /**
     * Global customer profile data transfer record.
     */
    record ProfileView(UUID userId, String firstName, String lastName, String locale) {}

    /**
     * Finds profile by user id.
     */
    Optional<ProfileView> findByUserId(UUID userId);

    /**
     * Creates a new global customer profile.
     */
    ProfileView createProfile(UUID userId, String firstName, String lastName);

    /**
     * Updates first and last name.
     */
    Optional<ProfileView> updateNames(UUID userId, String firstName, String lastName);
}
