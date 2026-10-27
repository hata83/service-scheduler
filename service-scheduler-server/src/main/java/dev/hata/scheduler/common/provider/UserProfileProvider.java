package dev.hata.scheduler.common.provider;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared provider contract for centralized user profile management (design D4/D13).
 * Feature packages access personal profile data for all users (customers and
 * employees) through this interface without direct package coupling.
 */
public interface UserProfileProvider {

    /**
     * Centralized user profile data transfer record.
     */
    record ProfileView(UUID userId, String firstName, String lastName, String locale) {}

    /**
     * Finds profile by user id.
     */
    Optional<ProfileView> findByUserId(UUID userId);

    /**
     * Creates a new centralized user profile.
     */
    ProfileView createProfile(UUID userId, String firstName, String lastName);

    /**
     * Updates first and last name.
     */
    Optional<ProfileView> updateNames(UUID userId, String firstName, String lastName);
}
