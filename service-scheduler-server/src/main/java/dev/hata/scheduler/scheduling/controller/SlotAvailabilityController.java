package dev.hata.scheduler.scheduling.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;
import dev.hata.scheduler.scheduling.service.SlotAvailabilityService;
import dev.hata.scheduler.scheduling.service.SlotDateOutOfRangeException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
     * Retrieves available time slots from cache or computes them on miss.
     */
    @Operation(
            summary = "Get available time slots",
            description = "Returns available time slots for a dealership, service type, and date."
                    + " Returns HTTP 400 when the date exceeds the dealership advance booking window.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Available time slots retrieved successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                array =
                                        @ArraySchema(
                                                schema = @Schema(implementation = SlotAvailabilityResponse.class)))),
        @ApiResponse(
                responseCode = "400",
                description = "Date is in the past or exceeds the maximum advance booking window",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping("/available")
    public ResponseEntity<?> getAvailableSlots(
            @Parameter(description = "Dealership ID", required = true) @RequestParam("dealershipId") UUID dealershipId,
            @Parameter(description = "Service type ID", required = true) @RequestParam("serviceTypeId")
                    UUID serviceTypeId,
            @Parameter(description = "Booking date (YYYY-MM-DD)", required = true)
                    @RequestParam("date")
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date) {
        try {
            List<SlotAvailabilityResponse> slots =
                    slotService.getCachedAvailableSlots(dealershipId, serviceTypeId, date);
            return ResponseEntity.ok(slots);
        } catch (SlotDateOutOfRangeException ex) {
            ApiProblem problem = ApiProblem.of(
                    HttpStatus.BAD_REQUEST.value(),
                    "Slot date out of range",
                    ex.getMessage(),
                    "/api/v1/slots/available");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
    }
}
