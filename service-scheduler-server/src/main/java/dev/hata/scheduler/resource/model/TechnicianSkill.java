package dev.hata.scheduler.resource.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * The {@code technician_skills} row. Associates a technician with one domain
 * {@link TechnicianSkillCode} and its {@link SkillLevel} proficiency rank
 * (SYSTEM_DESIGN §4.2.4).
 */
@Entity
@Table(name = "technician_skills")
@IdClass(TechnicianSkillId.class)
public class TechnicianSkill {

    @Id
    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "skill", nullable = false)
    private TechnicianSkillCode skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private SkillLevel level;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TechnicianSkill() {}

    /**
     * Fills a technician skill row with its domain competency and proficiency level.
     */
    public void assign(UUID technicianId, TechnicianSkillCode skill, SkillLevel level) {
        this.technicianId = Objects.requireNonNull(technicianId);
        this.skill = Objects.requireNonNull(skill);
        this.level = Objects.requireNonNull(level);
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public TechnicianSkillCode getSkill() {
        return skill;
    }

    public SkillLevel getLevel() {
        return level;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
