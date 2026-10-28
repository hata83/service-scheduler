package dev.hata.scheduler.dealership.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Workshop bay domain entity (SYSTEM_DESIGN §4.2.8).
 */
@Entity
@Table(name = "workshop_bays")
public class WorkshopBay {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "dealership_id", nullable = false)
    private UUID dealershipId;

    @Column(name = "bay_name", nullable = false, length = 100)
    private String bayName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "bay_types", columnDefinition = "text[]", nullable = false)
    private List<String> bayTypes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equipment_tags", columnDefinition = "jsonb", nullable = false)
    private String equipmentTags;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public WorkshopBay() {}

    public UUID getId() {
        return id;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public String getBayName() {
        return bayName;
    }

    public List<String> getBayTypes() {
        return bayTypes;
    }

    public String getEquipmentTags() {
        return equipmentTags;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
