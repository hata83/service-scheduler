package dev.hata.scheduler.appointment.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for an individual service job item within a booking request.
 */
public record ServiceJobItemRequest(
        @NotNull(message = "serviceTypeId is required") UUID serviceTypeId,
        @NotNull(message = "bayId is required") UUID bayId,
        @NotNull(message = "startTime is required") Instant startTime,
        @NotNull(message = "endTime is required") Instant endTime) {}
