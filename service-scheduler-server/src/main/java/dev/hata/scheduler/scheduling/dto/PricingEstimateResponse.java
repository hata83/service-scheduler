package dev.hata.scheduler.scheduling.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Total and itemized cost estimate response payload.
 */
public record PricingEstimateResponse(
        @Schema(example = "00000000-0000-0000-0000-000000000001") UUID dealershipId,
        @Schema(example = "11111111-0000-0000-0000-000000000001") UUID vehicleCategoryId,
        @Schema(description = "Itemized service costs") List<CostItemResponse> items,
        @Schema(example = "150.00") BigDecimal totalCost) {}
