package dev.hata.scheduler.dealership.dto;

import java.time.LocalTime;
import java.util.UUID;

import dev.hata.scheduler.dealership.model.Dealership;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dealership metadata response payload.
 */
public record DealershipResponse(
        @Schema(example = "00000000-0000-0000-0000-000000000001") UUID id,
        @Schema(example = "Hata Demo Service Center") String name,
        @Schema(example = "1 Le Loi") String address,
        @Schema(example = "Ho Chi Minh City") String city,
        @Schema(example = "Vietnam") String country,
        @Schema(example = "Asia/Ho_Chi_Minh") String timezone,
        @Schema(example = "30") int slotIntervalMinutes,
        @Schema(example = "08:00:00") LocalTime openTime,
        @Schema(example = "17:30:00") LocalTime closeTime) {

    public static DealershipResponse from(Dealership dealership) {
        return new DealershipResponse(
                dealership.getId(),
                dealership.getName(),
                dealership.getAddress(),
                dealership.getCity(),
                dealership.getCountry(),
                dealership.getTimezone(),
                dealership.getSlotIntervalMinutes(),
                dealership.getOpenTime(),
                dealership.getCloseTime());
    }
}
