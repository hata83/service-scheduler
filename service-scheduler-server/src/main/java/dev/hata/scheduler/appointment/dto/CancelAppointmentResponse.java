package dev.hata.scheduler.appointment.dto;

import java.time.Instant;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.AppointmentStatus;

/**
 * Response DTO returned upon appointment cancellation.
 */
public record CancelAppointmentResponse(UUID id, AppointmentStatus status, Instant cancelledAt) {}
