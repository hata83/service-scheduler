package dev.hata.scheduler.scheduling.repository;

import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.scheduling.model.WorkshopBay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads the {@code workshop_bays} table. Used by the slot grid compute algorithm
 * to find bays with no overlapping active service jobs (SYSTEM_DESIGN §5.3 Step 2).
 */
public interface WorkshopBayRepository extends JpaRepository<WorkshopBay, UUID> {

    /**
     * Finds active bays for a dealership where the given bay type is supported
     * and no active service job overlaps the requested time window.
     *
     * <p>Ordered by {@code last_used_at ASC NULLS FIRST} to implement
     * the Least Recently Used bay allocation policy (SYSTEM_DESIGN §4.3.1).
     *
     * @param dealershipId the dealership scope
     * @param bayType      the required bay type string
     * @param slotStart    start of the candidate time window (UTC)
     * @param slotEnd      end of the candidate time window (UTC)
     * @return bays that pass the bay gate, ordered LRU first
     */
    @Query(
            value =
                    """
            SELECT b.*
            FROM workshop_bays b
            WHERE b.dealership_id = :dealershipId
              AND :bayType = ANY(b.bay_types)
              AND b.is_active = true
              AND NOT EXISTS (
                SELECT 1 FROM service_jobs j
                WHERE j.bay_id = b.id
                  AND j.status <> 'CANCELLED'
                  AND j.start_time < :slotEnd
                  AND j.end_time > :slotStart
              )
            ORDER BY b.last_used_at ASC NULLS FIRST, b.bay_name ASC
            """,
            nativeQuery = true)
    List<WorkshopBay> findAvailableBays(
            @Param("dealershipId") UUID dealershipId,
            @Param("bayType") String bayType,
            @Param("slotStart") java.time.Instant slotStart,
            @Param("slotEnd") java.time.Instant slotEnd);
}
