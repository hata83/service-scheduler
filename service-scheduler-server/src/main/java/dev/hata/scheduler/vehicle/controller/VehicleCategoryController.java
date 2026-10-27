package dev.hata.scheduler.vehicle.controller;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.vehicle.dto.VehicleCategoryResponse;
import dev.hata.scheduler.vehicle.service.VehicleCategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vehicle category metadata endpoints.
 */
@Tag(name = "Vehicle Categories", description = "Supported vehicle categories operations")
@RestController
@RequestMapping("/api/v1/dealerships")
public class VehicleCategoryController {

    private final VehicleCategoryService vehicleCategoryService;

    public VehicleCategoryController(VehicleCategoryService vehicleCategoryService) {
        this.vehicleCategoryService = vehicleCategoryService;
    }

    /**
     * Lists supported vehicle categories for a dealership.
     */
    @Operation(
            summary = "List supported vehicle categories",
            description = "Retrieves vehicle categories supported by the specified dealership")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Supported vehicle categories retrieved successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                array =
                                        @ArraySchema(
                                                schema = @Schema(implementation = VehicleCategoryResponse.class)))),
        @ApiResponse(
                responseCode = "404",
                description = "Dealership not found",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping("/{id}/vehicle-categories")
    public ResponseEntity<List<VehicleCategoryResponse>> listCategories(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(vehicleCategoryService.findByDealershipId(id));
    }
}
