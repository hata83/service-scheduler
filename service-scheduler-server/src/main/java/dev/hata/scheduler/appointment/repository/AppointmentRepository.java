package dev.hata.scheduler.appointment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.Appointment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for customer appointments.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @EntityGraph(attributePaths = {"serviceJobs"})
    List<Appointment> findByCustomerIdOrderByStartTimeDesc(UUID customerId);

    Optional<Appointment> findByIdAndCustomerId(UUID id, UUID customerId);

    @EntityGraph(attributePaths = {"serviceJobs"})
    Optional<Appointment> findWithJobsByIdAndCustomerId(UUID id, UUID customerId);

    @EntityGraph(attributePaths = {"serviceJobs"})
    Optional<Appointment> findWithJobsById(UUID id);

    @EntityGraph(attributePaths = {"serviceJobs"})
    Optional<Appointment> findWithJobsByIdempotencyKey(UUID idempotencyKey);

    Optional<Appointment> findByIdempotencyKey(UUID idempotencyKey);
}
