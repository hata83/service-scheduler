package dev.hata.scheduler.scheduling.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.scheduling.model.WorkshopBay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads the dealership day schedule grid for the manager board
 * (change manager-schedule-realtime). One row per bay per job.
 */
public interface ScheduleQueryRepository extends JpaRepository<WorkshopBay, UUID> {

    /**
     * Projection row for one bay and one of its service jobs on the requested day.
     * Bays with no jobs still appear with null job fields.
     */
    interface ScheduleRow {
        UUID getBayId();

        String getBayName();

        String[] getBayTypes();

        boolean isActive();

        UUID getJobId();

        UUID getAppointmentId();

        String getStatus();

        Instant getStartTime();

        Instant getEndTime();

        String getFirstName();

        String getLastName();

        String getPhone();

        String getVehicleVin();

        String getServiceTypeName();
    }

    /**
     * Loads every active bay of a dealership with its service jobs on one day.
     * Cancelled service jobs stay off the board, so a cancellation removes the block.
     */
    @Query(
            value =
                    """
            SELECT b.id AS bayId, b.bay_name AS bayName, b.bay_types AS bayTypes,
                   b.is_active AS active,
                   j.id AS jobId, j.appointment_id AS appointmentId, j.status AS status,
                   j.start_time AS startTime, j.end_time AS endTime,
                   p.first_name AS firstName, p.last_name AS lastName,
                   u.phone AS phone, a.vehicle_vin AS vehicleVin, st.name AS serviceTypeName
            FROM workshop_bays b
            LEFT JOIN service_jobs j ON j.bay_id = b.id
                AND j.start_time >= :startOfDay
                AND j.start_time < :endOfDay
                AND j.status <> 'CANCELLED'
            LEFT JOIN appointments a ON a.id = j.appointment_id
            LEFT JOIN users u ON u.id = a.customer_id
            LEFT JOIN profiles p ON p.user_id = a.customer_id
            LEFT JOIN service_types st ON st.id = j.service_type_id
            WHERE b.dealership_id = :dealershipId
              AND b.is_active = true
            ORDER BY b.bay_name ASC, j.start_time ASC
            """,
            nativeQuery = true)
    List<ScheduleRow> findDayRows(
            @Param("dealershipId") UUID dealershipId,
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay") Instant endOfDay);

    /**
     * Projection row for one appointment in the manager schedule history list.
     */
    interface HistoryRow {
        UUID getId();

        UUID getDealershipId();

        UUID getCustomerId();

        String getCustomerPhone();

        String getCustomerName();

        String getVehicleVin();

        String getStatus();

        Instant getStartTime();

        Instant getEndTime();

        java.math.BigDecimal getEstimateCost();

        java.math.BigDecimal getTotalCost();
    }

    /**
     * Loads appointments for the manager history list. Every filter is optional.
     * A null {@code dealershipId} reads all dealerships for the ADMIN role.
     */
    @Query(
            value =
                    """
            SELECT a.id AS id, a.dealership_id AS dealershipId, a.customer_id AS customerId,
                   u.phone AS customerPhone,
                   p.first_name || ' ' || p.last_name AS customerName,
                   a.vehicle_vin AS vehicleVin, a.status AS status,
                   a.start_time AS startTime, a.end_time AS endTime,
                   a.estimate_cost AS estimateCost, a.total_cost AS totalCost
            FROM appointments a
            JOIN users u ON u.id = a.customer_id
            JOIN profiles p ON p.user_id = a.customer_id
            WHERE (CAST(:dealershipId AS UUID) IS NULL OR a.dealership_id = CAST(:dealershipId AS UUID))
              AND (CAST(:status AS VARCHAR) IS NULL OR a.status = CAST(:status AS VARCHAR))
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR a.start_time >= CAST(:from AS TIMESTAMPTZ))
              AND (CAST(:to AS TIMESTAMPTZ) IS NULL OR a.end_time <= CAST(:to AS TIMESTAMPTZ))
              AND (CAST(:customerPhone AS VARCHAR) IS NULL OR u.phone = CAST(:customerPhone AS VARCHAR))
              AND (CAST(:bayId AS UUID) IS NULL OR EXISTS (
                    SELECT 1 FROM service_jobs j
                    WHERE j.appointment_id = a.id AND j.bay_id = CAST(:bayId AS UUID)))
            ORDER BY a.start_time DESC
            LIMIT :maxRows
            """,
            nativeQuery = true)
    List<HistoryRow> findHistoryRows(
            @Param("dealershipId") UUID dealershipId,
            @Param("status") String status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("bayId") UUID bayId,
            @Param("customerPhone") String customerPhone,
            @Param("maxRows") int maxRows);
}
