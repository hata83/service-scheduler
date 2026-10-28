package dev.hata.scheduler.dealership.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Workshop bay mapped for dealership validation queries.
 */
@Entity
@Table(name = "workshop_bays")
public class DealershipBay {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "dealership_id", nullable = false)
    private UUID dealershipId;

    @Column(name = "bay_name", nullable = false, length = 50)
    private String bayName;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DealershipBay() {}

    public UUID getId() {
        return id;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public String getBayName() {
        return bayName;
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
