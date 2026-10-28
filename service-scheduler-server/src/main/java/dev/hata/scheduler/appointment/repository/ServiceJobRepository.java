package dev.hata.scheduler.appointment.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.ServiceJob;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for service jobs.
 */
@Repository
public interface ServiceJobRepository extends JpaRepository<ServiceJob, UUID> {

    List<ServiceJob> findByAppointmentIdOrderByStartTimeAsc(UUID appointmentId);

    @Modifying(clearAutomatically = true, flushAutomatically = false)
    @Query(
            nativeQuery = true,
            value =
                    """
            UPDATE service_jobs
            SET notes = CASE
                    WHEN notes IS NULL OR notes = '' THEN :note
                    ELSE notes || E'\\n' || :note
                END,
                updated_at = NOW()
            WHERE id = :jobId
            """)
    int appendNoteAtomic(@Param("jobId") UUID jobId, @Param("note") String note);
}
