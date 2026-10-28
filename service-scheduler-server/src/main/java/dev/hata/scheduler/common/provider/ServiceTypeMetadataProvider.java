package dev.hata.scheduler.common.provider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared provider contract for service type metadata and cost calculation across features.
 */
@FunctionalInterface
public interface ServiceTypeMetadataProvider {

    record ServiceTypeMetadata(UUID id, int estimatedMinutes, BigDecimal cost) {}

    Optional<ServiceTypeMetadata> findServiceTypeMetadata(
            UUID dealershipId, UUID vehicleCategoryId, UUID serviceTypeId);
}
