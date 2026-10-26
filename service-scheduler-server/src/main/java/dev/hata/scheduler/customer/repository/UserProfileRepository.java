package dev.hata.scheduler.customer.repository;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.customer.model.UserProfile;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the centralized {@code profiles} table.
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUserId(UUID userId);
}
