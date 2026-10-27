package dev.hata.scheduler.scheduling.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Time slot availability response payload.
 */
public record SlotAvailabilityResponse(
        @Schema(example = "2026-10-28T08:00:00+07:00") OffsetDateTime startTime,
        @Schema(example = "2026-10-28T09:00:00+07:00") OffsetDateTime endTime,
        @Schema(example = "true") boolean available) {}
