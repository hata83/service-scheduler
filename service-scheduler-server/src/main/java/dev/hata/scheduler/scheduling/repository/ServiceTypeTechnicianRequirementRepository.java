package dev.hata.scheduler.scheduling.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.scheduling.model.ServiceTypeTechnicianRequirement;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads the {@code service_type_technician_requirements} table.
 * Used to load the required technician role slots for a service type
 * (SYSTEM_DESIGN §4.2.9, §5.4 Step 3).
 */
public interface ServiceTypeTechnicianRequirementRepository
        extends JpaRepository<ServiceTypeTechnicianRequirement, UUID> {

    List<ServiceTypeTechnicianRequirement> findByServiceTypeIdOrderBySlotNumber(UUID serviceTypeId);
}
