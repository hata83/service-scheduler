package dev.hata.scheduler.appointment.controller;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.dto.AppointmentResponse;
import dev.hata.scheduler.appointment.dto.CancelAppointmentResponse;
import dev.hata.scheduler.appointment.dto.CreateAppointmentRequest;
import dev.hata.scheduler.appointment.dto.StaffAppointmentResponse;
import dev.hata.scheduler.appointment.service.AppointmentService;
import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.common.filter.AuthFilter;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.common.security.StaffScope;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller exposing customer appointment booking, history, and cancellation endpoints.
 */
@Tag(name = "Appointments", description = "Customer service appointment operations")
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Books a customer appointment with itemized service jobs.
     */
    @Operation(
            summary = "Book service appointment",
            description =
                    "Books a customer appointment with one or more service jobs. Requires X-Idempotency-Key header.")
    @Parameter(
            name = "X-Idempotency-Key",
            in = ParameterIn.HEADER,
            required = true,
            description = "Unique client transaction identifier for idempotency replay",
            schema = @Schema(type = "string", format = "uuid"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Appointment booked successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = AppointmentResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Bad request - invalid fields, overlapping intra-jobs, or duration exceeded",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Dealership or category not found",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Conflict - overlapping booking exists for vehicle or workshop bay",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    public ResponseEntity<AppointmentResponse> book(
            @RequestHeader(name = "X-Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal,
            @Valid @RequestBody CreateAppointmentRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!ROLE_CUSTOMER.equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customer accounts can book appointments");
        }
        if (idempotencyKeyHeader == null || idempotencyKeyHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Idempotency-Key header is required");
        }

        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(idempotencyKeyHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid UUID format in X-Idempotency-Key header: " + idempotencyKeyHeader,
                    ex);
        }

        AppointmentResponse response =
                appointmentService.book(request, principal.userId(), principal.phone(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists appointments owned by the authenticated caller.
     */
    @Operation(
            summary = "List customer appointments",
            description = "Returns appointments for the authenticated caller ordered by start time descending")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Appointments retrieved successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                array = @ArraySchema(schema = @Schema(implementation = AppointmentResponse.class)))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    public ResponseEntity<List<AppointmentResponse>> listMyAppointments(
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!ROLE_CUSTOMER.equals(principal.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only customer accounts can access appointment history");
        }
        return ResponseEntity.ok(appointmentService.findCustomerAppointments(principal.userId()));
    }

    /**
     * Retrieves detailed information for a specific owned appointment.
     */
    @Operation(
            summary = "Get appointment details",
            description = "Returns appointment and itemized service jobs for an owned appointment")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Appointment details retrieved successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(anyOf = {AppointmentResponse.class, StaffAppointmentResponse.class}))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Appointment not found or owned by another user",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @GetMapping(
            value = "/{id}",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    public ResponseEntity<?> getAppointmentById(
            @PathVariable("id") UUID id,
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (ROLE_CUSTOMER.equals(principal.role())) {
            return ResponseEntity.ok(appointmentService.findAppointmentById(id, principal.userId()));
        }
        if (!StaffScope.isStaffRole(principal.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only customer, manager, and admin accounts can access appointments");
        }
        UUID scope = StaffScope.dealershipScope(principal, null);
        return ResponseEntity.ok(appointmentService.findStaffAppointment(id, scope));
    }

    /**
     * Cancels an appointment prior to vehicle check-in.
     */
    @Operation(
            summary = "Cancel appointment",
            description =
                    "Cancels an appointment in REQUESTED or CONFIRMED status. Rejects cancellation once checked in.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Appointment cancelled successfully",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CancelAppointmentResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Appointment already in terminal state",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Appointment not found or owned by another user",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(
                responseCode = "422",
                description = "Unprocessable Entity - cancellation rejected after vehicle check-in",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ApiProblem.class)))
    })
    @DeleteMapping(
            value = "/{id}",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    public ResponseEntity<CancelAppointmentResponse> cancel(
            @PathVariable("id") UUID id,
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!ROLE_CUSTOMER.equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only customer accounts can cancel appointments");
        }
        return ResponseEntity.ok(appointmentService.cancelAppointment(id, principal.userId(), principal.phone()));
    }
}
