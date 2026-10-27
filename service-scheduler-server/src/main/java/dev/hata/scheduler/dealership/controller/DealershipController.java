package dev.hata.scheduler.dealership.controller;

import java.util.List;

import dev.hata.scheduler.dealership.dto.DealershipResponse;
import dev.hata.scheduler.dealership.service.DealershipService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dealership discovery endpoints.
 */
@Tag(name = "Dealerships", description = "Dealership discovery and metadata operations")
@RestController
@RequestMapping("/api/v1/dealerships")
public class DealershipController {

    private final DealershipService dealershipService;

    public DealershipController(DealershipService dealershipService) {
        this.dealershipService = dealershipService;
    }

    /**
     * Lists all active dealerships.
     */
    @Operation(summary = "List active dealerships", description = "Retrieves all currently active dealership locations")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Active dealerships retrieved successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                array = @ArraySchema(schema = @Schema(implementation = DealershipResponse.class))))
    })
    @GetMapping
    public ResponseEntity<List<DealershipResponse>> listDealerships() {
        return ResponseEntity.ok(dealershipService.findAllActive());
    }
}
