package dev.hata.scheduler.customer.repository;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.customer.model.CustomerProfile;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code customers} table.
 */
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {

    Optional<CustomerProfile> findByUserId(UUID userId);
}
