package dev.hata.scheduler.common.provider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port for querying available technicians without importing the resource package.
 *
 * <p>The {@code scheduling} package cannot import {@code resource} (ArchUnit ADR-009).
 * This port decouples the slot grid compute algorithm from the technician domain.
 * The {@code resource} package provides the implementation.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface TechnicianAvailabilityPort {

    /**
     * Finds technician IDs available in the given time window for a specific skill and level.
     *
     * <p>A technician is available when:
     * <ul>
     *   <li>{@code is_available = true}</li>
     *   <li>holds the required skill at or above the minimum level</li>
     *   <li>has no overlapping assignment in {@code job_technician_assignments}</li>
     * </ul>
     *
     * @param dealershipId   scope of the search
     * @param requiredSkill  skill code string (matches {@code technician_skills.skill})
     * @param minSkillLevel  minimum level string (matches {@code technician_skills.level})
     * @param slotStart      start of the candidate time window
     * @param slotEnd        end of the candidate time window
     * @return list of available technician user IDs
     */
    List<UUID> findAvailableTechnicianIds(
            UUID dealershipId, String requiredSkill, String minSkillLevel, Instant slotStart, Instant slotEnd);
}
