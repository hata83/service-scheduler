package dev.hata.scheduler.scheduling.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Service type cost matrix item for a vehicle category.
 */
@Entity
@Table(name = "service_type_costs")
@IdClass(ServiceTypeCostId.class)
public class ServiceTypeCost {

    @Id
    @Column(name = "service_type_id", nullable = false)
    private UUID serviceTypeId;

    @Id
    @Column(name = "vehicle_category_id", nullable = false)
    private UUID vehicleCategoryId;

    @Column(name = "cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public ServiceTypeCost() {}

    public UUID getServiceTypeId() {
        return serviceTypeId;
    }

    public void setServiceTypeId(UUID serviceTypeId) {
        this.serviceTypeId = serviceTypeId;
    }

    public UUID getVehicleCategoryId() {
        return vehicleCategoryId;
    }

    public void setVehicleCategoryId(UUID vehicleCategoryId) {
        this.vehicleCategoryId = vehicleCategoryId;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
