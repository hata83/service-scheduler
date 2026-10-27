package dev.hata.scheduler.dealership.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.dealership.model.Dealership;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code dealerships} table.
 */
public interface DealershipRepository extends JpaRepository<Dealership, UUID> {

    List<Dealership> findByActiveTrue();
}
