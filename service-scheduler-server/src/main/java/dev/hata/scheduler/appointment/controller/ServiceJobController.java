package dev.hata.scheduler.appointment.controller;

import java.util.UUID;

import dev.hata.scheduler.appointment.dto.AddJobNoteRequest;
import dev.hata.scheduler.appointment.dto.JobNoteResponse;
import dev.hata.scheduler.appointment.service.AppointmentService;
import dev.hata.scheduler.common.filter.AuthFilter;
import dev.hata.scheduler.common.security.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller exposing technician inspection note operations on service jobs.
 * SYSTEM_DESIGN section 14.1: POST /api/v1/jobs/{id}/notes
 */
@Tag(name = "Service Jobs", description = "Technician inspection note operations")
@RestController
@RequestMapping("/api/v1/jobs")
public class ServiceJobController {

    private final AppointmentService appointmentService;

    public ServiceJobController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Appends an inspection note to a service job.
     * TECHNICIAN must be assigned to the job; MANAGER and ADMIN may append regardless.
     * CUSTOMER is rejected with HTTP 403.
     */
    @Operation(
            summary = "Append inspection note",
            description = "Appends an inspection note to the specified service job")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "Service job UUID",
            schema = @Schema(type = "string", format = "uuid"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Note appended successfully",
                content =
                        @Content(
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                schema = @Schema(implementation = JobNoteResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Bad request - note blank or exceeds 4000 characters",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized - authentication required",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
                responseCode = "403",
                description = "Forbidden - customer or unassigned technician",
                content = @Content(mediaType = "application/problem+json")),
        @ApiResponse(
                responseCode = "404",
                description = "Service job not found",
                content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping(
            value = "/{id}/notes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JobNoteResponse> appendNote(
            @PathVariable("id") UUID jobId,
            @RequestAttribute(name = AuthFilter.PRINCIPAL_ATTRIBUTE, required = false) AuthPrincipal principal,
            @Valid @RequestBody AddJobNoteRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String role = principal.role();
        boolean isManager = "MANAGER".equals(role) || "ADMIN".equals(role);
        boolean isTechnician = "TECHNICIAN".equals(role);

        if (!isManager && !isTechnician) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only technicians and managers may append notes");
        }

        AppointmentService.JobNoteResult result =
                appointmentService.appendJobNote(jobId, principal.userId(), request.note(), isManager);
        return ResponseEntity.ok(appointmentService.readJobNotes(result.jobId()));
    }
}
