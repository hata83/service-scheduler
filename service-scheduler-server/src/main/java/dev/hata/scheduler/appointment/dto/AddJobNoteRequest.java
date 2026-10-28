package dev.hata.scheduler.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for appending an inspection note to a service job
 * (POST /api/v1/jobs/{id}/notes, SYSTEM_DESIGN section 14.1).
 */
public record AddJobNoteRequest(
        @NotBlank(message = "note must not be blank") //
                @Size(max = 4000, message = "note must be at most 4000 characters") //
                String note) {}
