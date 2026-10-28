package dev.hata.scheduler.appointment.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.ServiceJob;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for service jobs.
 */
@Repository
public interface ServiceJobRepository extends JpaRepository<ServiceJob, UUID> {

    List<ServiceJob> findByAppointmentIdOrderByStartTimeAsc(UUID appointmentId);
}
