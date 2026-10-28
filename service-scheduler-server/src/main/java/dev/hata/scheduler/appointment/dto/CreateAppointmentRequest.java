package dev.hata.scheduler.appointment.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for booking a workshop appointment (SYSTEM_DESIGN §4.2.11).
 */
public record CreateAppointmentRequest(
        @NotNull(message = "dealershipId is required") UUID dealershipId,
        @NotNull(message = "vehicleCategoryId is required") UUID vehicleCategoryId,
        @NotBlank(message = "vehicleVin is required") //
                @Size(min = 17, max = 17, message = "vehicleVin must be exactly 17 characters") //
                @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$", message = "vehicleVin format is invalid") //
                String vehicleVin,
        @NotEmpty(message = "serviceJobs must not be empty") List<@Valid ServiceJobItemRequest> serviceJobs) {}
