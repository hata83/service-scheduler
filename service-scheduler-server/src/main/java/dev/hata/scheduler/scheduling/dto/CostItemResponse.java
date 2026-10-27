package dev.hata.scheduler.scheduling.dto;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Itemized service cost response.
 */
public record CostItemResponse(
        @Schema(example = "22222222-0000-0000-0000-000000000001") UUID serviceTypeId,
        @Schema(example = "STANDARD_SERVICE") String serviceCode,
        @Schema(example = "150.00") BigDecimal cost) {}
