package dev.hata.scheduler.vehicle.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.vehicle.model.VehicleCategory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code vehicle_categories} table.
 */
public interface VehicleCategoryRepository extends JpaRepository<VehicleCategory, UUID> {

    List<VehicleCategory> findByDealershipId(UUID dealershipId);
}
