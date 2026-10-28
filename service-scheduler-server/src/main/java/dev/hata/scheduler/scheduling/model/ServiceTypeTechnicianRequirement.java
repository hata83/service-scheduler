package dev.hata.scheduler.scheduling.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Technician role slot requirement per service type. Maps the
 * {@code service_type_technician_requirements} table
 * (SYSTEM_DESIGN §4.2.9).
 */
@Entity
@Table(name = "service_type_technician_requirements")
public class ServiceTypeTechnicianRequirement {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Column(name = "slot_number", nullable = false)
    private int slotNumber;

    @Column(name = "required_skill", nullable = false, length = 30)
    private String requiredSkill;

    @Column(name = "min_skill_level", nullable = false, length = 20)
    private String minSkillLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ServiceTypeTechnicianRequirement() {}

    public UUID getId() {
        return id;
    }

    public UUID getServiceTypeId() {
        return serviceTypeId;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public String getRequiredSkill() {
        return requiredSkill;
    }

    public String getMinSkillLevel() {
        return minSkillLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
