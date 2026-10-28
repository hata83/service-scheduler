package dev.hata.scheduler.appointment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.Appointment;
import dev.hata.scheduler.appointment.model.AppointmentStatus;

/**
 * Response DTO representing an appointment with itemized service jobs.
 */
public record AppointmentResponse(
        UUID id,
        UUID dealershipId,
        UUID customerId,
        UUID vehicleCategoryId,
        String vehicleVin,
        Instant startTime,
        Instant endTime,
        AppointmentStatus status,
        BigDecimal estimateCost,
        BigDecimal totalCost,
        List<ServiceJobResponse> serviceJobs,
        Instant createdAt) {

    public static AppointmentResponse from(Appointment a) {
        List<ServiceJobResponse> jobs = a.getServiceJobs() == null
                ? List.of()
                : a.getServiceJobs().stream().map(ServiceJobResponse::from).toList();
        return new AppointmentResponse(
                a.getId(),
                a.getDealershipId(),
                a.getCustomerId(),
                a.getVehicleCategoryId(),
                a.getVehicleVin(),
                a.getStartTime(),
                a.getEndTime(),
                a.getStatus(),
                a.getEstimateCost(),
                a.getTotalCost(),
                jobs,
                a.getCreatedAt());
    }
}
