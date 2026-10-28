package dev.hata.scheduler.appointment.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.JobTechnicianAssignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for job technician assignments.
 */
@Repository
public interface JobTechnicianAssignmentRepository extends JpaRepository<JobTechnicianAssignment, UUID> {

    List<JobTechnicianAssignment> findByJobId(UUID jobId);

    void deleteByJobIdIn(List<UUID> jobIds);
}
