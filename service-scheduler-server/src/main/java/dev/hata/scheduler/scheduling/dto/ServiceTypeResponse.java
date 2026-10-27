package dev.hata.scheduler.scheduling.dto;

import java.util.UUID;

import dev.hata.scheduler.scheduling.model.ServiceType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Service type metadata response payload.
 */
public record ServiceTypeResponse(
        @Schema(example = "22222222-0000-0000-0000-000000000001") UUID id,
        @Schema(example = "00000000-0000-0000-0000-000000000001") UUID dealershipId,
        @Schema(example = "STANDARD_SERVICE") String code,
        @Schema(example = "Standard Scheduled Service") String name,
        @Schema(example = "Routine scheduled service inspection and fluid top-up") String description,
        @Schema(example = "60") int estimatedMinutes) {

    public static ServiceTypeResponse from(ServiceType entity) {
        return new ServiceTypeResponse(
                entity.getId(),
                entity.getDealershipId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getEstimatedMinutes());
    }
}
