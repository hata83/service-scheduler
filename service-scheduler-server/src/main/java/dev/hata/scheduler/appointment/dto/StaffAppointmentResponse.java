package dev.hata.scheduler.appointment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manager booking detail response (change manager-schedule-realtime).
 * Carries the appointment header plus the staff view with customer, vehicle,
 * job notes, cost feedback, and communication audit records.
 */
public record StaffAppointmentResponse(
        UUID id,
        UUID dealershipId,
        String status,
        Instant startTime,
        Instant endTime,
        BigDecimal estimateCost,
        BigDecimal totalCost,
        Instant createdAt,
        StaffView staffView) {

    /**
     * Staff view data of one appointment.
     */
    public record StaffView(
            String customerId,
            String customerName,
            String customerPhone,
            String vehicleVin,
            String vehicleCategory,
            List<StaffJobView> jobs,
            List<StaffCommunicationView> communicationLogs) {}

    /**
     * Staff view of one service job with its inspection notes and cost feedback.
     */
    public record StaffJobView(
            UUID jobId,
            String serviceName,
            String bayName,
            String status,
            Instant startTime,
            Instant endTime,
            List<String> notes,
            BigDecimal estimateCost,
            BigDecimal finalCost) {}

    /**
     * Staff view of one communication audit record.
     */
    public record StaffCommunicationView(
            String eventType, String channel, String deliveryStatus, Instant dispatchedAt) {}
}
