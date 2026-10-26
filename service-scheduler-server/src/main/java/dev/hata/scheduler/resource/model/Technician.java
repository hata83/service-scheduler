package dev.hata.scheduler.resource.model;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/**
 * The {@code technicians} specialization of {@link Employee}. A technician
 * belongs to a specific local {@code dealership_id}, carries an overall career
 * {@link SkillLevel}, and holds multi-skill rows in {@code technician_skills}
 * (design D4, SYSTEM_DESIGN §4.2.3).
 */
@Entity
@Table(name = "technicians")
@PrimaryKeyJoinColumn(name = "user_id")
public class Technician extends Employee {

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private SkillLevel level;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    public Technician() {}

    /**
     * Fills the technician employee and specialization fields.
     */
    public void assignTechnician(UUID userId, UUID dealershipId, String employeeId, SkillLevel level) {
        assignEmployee(userId, employeeId, ROLE_TECHNICIAN, dealershipId);
        this.level = Objects.requireNonNull(level, "Technician level is required.");
        this.available = true;
    }

    public SkillLevel getLevel() {
        return level;
    }

    public boolean isAvailable() {
        return available;
    }
}
