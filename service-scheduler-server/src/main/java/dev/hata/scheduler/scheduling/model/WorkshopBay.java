package dev.hata.scheduler.scheduling.model;

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
 * Workshop bay entity. Maps the {@code workshop_bays} table.
 * Lives in the scheduling package because bay slots are a scheduling concern
 * (SYSTEM_DESIGN §4.2.7, ADR-012).
 */
@Entity
@Table(name = "workshop_bays")
public class WorkshopBay {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "dealership_id", nullable = false)
    private UUID dealershipId;

    @Column(name = "bay_name", nullable = false, length = 50)
    private String bayName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "bay_types", columnDefinition = "text[]")
    private List<String> bayTypes = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public boolean isActive() {
        return active;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
