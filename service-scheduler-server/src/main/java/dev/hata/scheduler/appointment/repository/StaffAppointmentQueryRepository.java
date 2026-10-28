package dev.hata.scheduler.appointment.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.Appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads the manager booking detail view (change manager-schedule-realtime).
 * The native queries join customer, vehicle, catalogue, workshop, and audit tables.
 */
public interface StaffAppointmentQueryRepository extends JpaRepository<Appointment, UUID> {

    /**
     * Projection for the appointment header with customer and vehicle data.
     */
    interface StaffAppointmentRow {
        UUID getId();

        UUID getDealershipId();

        UUID getCustomerId();

        String getVehicleVin();

        String getStatus();

        Instant getStartTime();

        Instant getEndTime();

        BigDecimal getEstimateCost();

        BigDecimal getTotalCost();

        Instant getCreatedAt();

        String getCustomerPhone();

        String getCustomerName();

        String getVehicleCategory();
    }

    /**
     * Projection for one service job with its bay, service type, notes, and costs.
     */
    interface StaffJobRow {
        UUID getJobId();

        String getServiceName();

        String getBayName();

        String getStatus();

        Instant getStartTime();

        Instant getEndTime();

        String getNotes();

        BigDecimal getEstimateCost();

        BigDecimal getFinalCost();
    }

    /**
     * Projection for one communication audit record of an appointment.
     */
    interface StaffCommunicationRow {
        String getEventType();

        String getChannel();

        String getDeliveryStatus();

        Instant getDispatchedAt();
    }

    /**
     * Loads the appointment header with customer contact and vehicle category.
     */
    @Query(
            value =
                    """
            SELECT a.id AS id, a.dealership_id AS dealershipId, a.customer_id AS customerId,
                   a.vehicle_vin AS vehicleVin, a.status AS status,
                   a.start_time AS startTime, a.end_time AS endTime,
                   a.estimate_cost AS estimateCost, a.total_cost AS totalCost,
                   a.created_at AS createdAt,
                   u.phone AS customerPhone,
                   p.first_name || ' ' || p.last_name AS customerName,
                    v.type AS vehicleCategory
            FROM appointments a
            JOIN users u ON u.id = a.customer_id
            JOIN profiles p ON p.user_id = a.customer_id
            JOIN vehicle_categories v ON v.id = a.vehicle_category_id
            WHERE a.id = :appointmentId
            """,
            nativeQuery = true)
    java.util.Optional<StaffAppointmentRow> findStaffAppointment(@Param("appointmentId") UUID appointmentId);

    /**
     * Loads every service job of an appointment with notes and cost feedback.
     */
    @Query(
            value =
                    """
            SELECT j.id AS jobId, s.code AS serviceName, b.bay_name AS bayName, j.status AS status,
                   j.start_time AS startTime, j.end_time AS endTime, j.notes AS notes,
                   j.estimate_cost AS estimateCost, j.final_cost AS finalCost
            FROM service_jobs j
            JOIN service_types s ON s.id = j.service_type_id
            JOIN workshop_bays b ON b.id = j.bay_id
            WHERE j.appointment_id = :appointmentId
            ORDER BY j.start_time
            """,
            nativeQuery = true)
    List<StaffJobRow> findStaffJobs(@Param("appointmentId") UUID appointmentId);

    /**
     * Loads the communication audit records of an appointment.
     */
    @Query(
            value =
                    """
            SELECT event_type AS eventType, channel AS channel,
                   delivery_status AS deliveryStatus, dispatched_at AS dispatchedAt
            FROM communication_logs
            WHERE appointment_id = :appointmentId
            ORDER BY dispatched_at DESC
            """,
            nativeQuery = true)
    List<StaffCommunicationRow> findStaffCommunications(@Param("appointmentId") UUID appointmentId);
}
