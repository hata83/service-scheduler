package dev.hata.scheduler.vehicle.dto;

import java.util.UUID;

import dev.hata.scheduler.vehicle.model.VehicleCategory;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Vehicle category metadata response payload.
 */
public record VehicleCategoryResponse(
        @Schema(example = "11111111-0000-0000-0000-000000000001") UUID id,
        @Schema(example = "00000000-0000-0000-0000-000000000001") UUID dealershipId,
        @Schema(example = "Toyota") String brand,
        @Schema(example = "Camry") String model,
        @Schema(example = "SEDAN") String type) {

    public static VehicleCategoryResponse from(VehicleCategory entity) {
        return new VehicleCategoryResponse(
                entity.getId(), entity.getDealershipId(), entity.getBrand(), entity.getModel(), entity.getType());
    }
}
