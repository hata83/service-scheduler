package dev.hata.scheduler.scheduling.controller;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.scheduling.dto.ServiceTypeResponse;
import dev.hata.scheduler.scheduling.service.ServiceTypeService;

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
 * Service types catalog metadata endpoints.
 */
@Tag(name = "Service Types", description = "Workshop service types catalog operations")
@RestController
@RequestMapping("/api/v1/dealerships")
public class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    public ServiceTypeController(ServiceTypeService serviceTypeService) {
        this.serviceTypeService = serviceTypeService;
    }

    /**
     * Lists active service types for a dealership.
     */
    @Operation(
            summary = "List active service types",
            description = "Retrieves active workshop service types for the specified dealership")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Active service types retrieved successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                array = @ArraySchema(schema = @Schema(implementation = ServiceTypeResponse.class)))),
        @ApiResponse(
                responseCode = "404",
                description = "Dealership not found",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping("/{id}/service-types")
    public ResponseEntity<List<ServiceTypeResponse>> listServiceTypes(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(serviceTypeService.findByDealershipId(id));
    }
}
