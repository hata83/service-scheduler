package dev.hata.scheduler.scheduling.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for service_type_costs table.
 */
public class ServiceTypeCostId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID serviceTypeId;
    private UUID vehicleCategoryId;

    public ServiceTypeCostId() {}

    public ServiceTypeCostId(UUID serviceTypeId, UUID vehicleCategoryId) {
        this.serviceTypeId = serviceTypeId;
        this.vehicleCategoryId = vehicleCategoryId;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceTypeCostId that)) {
            return false;
        }
        return Objects.equals(serviceTypeId, that.serviceTypeId)
                && Objects.equals(vehicleCategoryId, that.vehicleCategoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceTypeId, vehicleCategoryId);
    }
}
