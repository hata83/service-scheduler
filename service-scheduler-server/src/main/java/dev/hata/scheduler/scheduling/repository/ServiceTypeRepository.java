package dev.hata.scheduler.scheduling.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.scheduling.model.ServiceType;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code service_types} table.
 */
public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {

    List<ServiceType> findByDealershipIdAndActiveTrue(UUID dealershipId);

    List<ServiceType> findByIdInAndActiveTrue(Collection<UUID> ids);
}
