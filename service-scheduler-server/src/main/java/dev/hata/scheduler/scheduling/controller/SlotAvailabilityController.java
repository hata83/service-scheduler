package dev.hata.scheduler.scheduling.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;
import dev.hata.scheduler.scheduling.service.SlotAvailabilityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slot availability endpoints.
 */
@Tag(name = "Slots", description = "Workshop time slot availability operations")
@RestController
@RequestMapping("/api/v1/slots")
public class SlotAvailabilityController {

    private final SlotAvailabilityService slotService;

    public SlotAvailabilityController(SlotAvailabilityService slotService) {
        this.slotService = slotService;
    }

    /**
     * Retrieves candidate available time slots from cache.
     */
    @Operation(
            summary = "Get available time slots",
            description = "Retrieves candidate available time slots for a dealership, service type, and date")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Available time slots retrieved successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                array =
                                        @ArraySchema(
                                                schema = @Schema(implementation = SlotAvailabilityResponse.class))))
    })
    @GetMapping("/available")
    public ResponseEntity<List<SlotAvailabilityResponse>> getAvailableSlots(
            @Parameter(description = "Dealership ID", required = true) @RequestParam("dealershipId") UUID dealershipId,
            @Parameter(description = "Service type ID", required = true) @RequestParam("serviceTypeId")
                    UUID serviceTypeId,
            @Parameter(description = "Booking date (YYYY-MM-DD)", required = true)
                    @RequestParam("date")
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        return ResponseEntity.ok(slotService.getCachedAvailableSlots(dealershipId, serviceTypeId, date));
    }
}
