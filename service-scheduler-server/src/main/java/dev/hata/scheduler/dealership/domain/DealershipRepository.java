package dev.hata.scheduler.dealership.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code dealerships} table.
 */
public interface DealershipRepository extends JpaRepository<Dealership, UUID> {}
