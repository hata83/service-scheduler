package dev.hata.scheduler.scheduling.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.common.filter.AuthFilter;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.common.security.StaffScope;
import dev.hata.scheduler.scheduling.dto.ScheduleDayResponse;
import dev.hata.scheduler.scheduling.dto.ScheduleHistoryItem;
import dev.hata.scheduler.scheduling.dto.ScheduleHistoryQuery;
import dev.hata.scheduler.scheduling.service.ScheduleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing the manager schedule board: day grid view and filtered history list
 * (SYSTEM_DESIGN section 3.3, change manager-schedule-realtime).
 */
@Tag(name = "Schedule", description = "Manager day schedule and history queries")
@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * Returns the full day schedule with one column per active bay.
     */
    @Operation(
            summary = "Get manager day schedule",
            description = "Returns every bay of one dealership with its service job blocks for one date")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Day schedule retrieved successfully",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = ScheduleDayResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Bad request - missing date or dealership scope",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Forbidden - only MANAGER and ADMIN may read the schedule",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping(
            value = "/day",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    public ResponseEntity<ScheduleDayResponse> daySchedule(
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "dealershipId", required = false) UUID dealershipId) {
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        UUID scope = StaffScope.dealershipScope(principal, dealershipId);
        if (scope == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "dealershipId is required for the day schedule");
        }
        LocalDate requestedDate =
                date == null || date.isBlank() ? LocalDate.now(ZoneId.of(dealershipTimezone(scope))) : parseDate(date);
        return ResponseEntity.ok(scheduleService.findDaySchedule(scope, requestedDate));
    }

    /**
     * Returns the filtered appointment history list for managers.
     */
    @Operation(
            summary = "Get manager schedule history",
            description = "Returns appointments sorted by start time descending with optional filters")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "History retrieved successfully",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                array = @ArraySchema(schema = @Schema(implementation = ScheduleHistoryItem.class)))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Forbidden - only MANAGER and ADMIN may read the history",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping(
            value = "/history",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    public ResponseEntity<List<ScheduleHistoryItem>> history(
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "bayId", required = false) UUID bayId,
            @RequestParam(name = "customerPhone", required = false) String customerPhone,
            @RequestParam(name = "dealershipId", required = false) UUID dealershipId) {
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        UUID scope = StaffScope.dealershipScope(principal, dealershipId);
        ScheduleHistoryQuery query =
                new ScheduleHistoryQuery(scope, status, parseInstant(from), parseInstant(to), bayId, customerPhone);
        return ResponseEntity.ok(scheduleService.findHistory(query));
    }

    private String dealershipTimezone(UUID dealershipId) {
        return scheduleService.dealershipTimezone(dealershipId);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "date must use the yyyy-MM-dd format: " + value, ex);
        }
    }

    private static java.time.Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.Instant.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "filter must use the ISO-8601 instant format: " + value, ex);
        }
    }
}
