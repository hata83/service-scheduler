package dev.hata.scheduler.scheduling.controller;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.scheduling.dto.PricingEstimateResponse;
import dev.hata.scheduler.scheduling.service.PricingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service pricing estimate endpoints.
 */
@Tag(name = "Pricing", description = "Instant service cost estimation operations")
@RestController
@RequestMapping("/api/v1/dealerships")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /**
     * Calculates instant price estimate for selected service types and vehicle category.
     */
    @Operation(
            summary = "Calculate price estimate",
            description =
                    "Calculates total and itemized estimated costs for selected service types and a vehicle category")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Estimate calculated successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PricingEstimateResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid request or missing cost mapping",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Dealership not found",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping("/{id}/pricing/estimate")
    public ResponseEntity<PricingEstimateResponse> calculateEstimate(
            @PathVariable("id") UUID id,
            @Parameter(description = "Vehicle category ID", required = true) @RequestParam("categoryId")
                    UUID categoryId,
            @Parameter(description = "Selected service type IDs", required = true) @RequestParam("serviceTypeIds")
                    List<UUID> serviceTypeIds) {
        return ResponseEntity.ok(pricingService.calculateEstimate(id, categoryId, serviceTypeIds));
    }
}
