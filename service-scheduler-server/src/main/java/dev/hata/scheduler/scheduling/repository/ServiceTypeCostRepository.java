package dev.hata.scheduler.scheduling.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.scheduling.model.ServiceTypeCost;
import dev.hata.scheduler.scheduling.model.ServiceTypeCostId;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code service_type_costs} table.
 */
public interface ServiceTypeCostRepository extends JpaRepository<ServiceTypeCost, ServiceTypeCostId> {

    Optional<ServiceTypeCost> findByServiceTypeIdAndVehicleCategoryId(UUID serviceTypeId, UUID vehicleCategoryId);

    List<ServiceTypeCost> findByVehicleCategoryIdAndServiceTypeIdIn(
            UUID vehicleCategoryId, Collection<UUID> serviceTypeIds);
}
