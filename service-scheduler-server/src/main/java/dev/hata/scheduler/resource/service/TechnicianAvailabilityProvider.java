package dev.hata.scheduler.resource.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.common.provider.TechnicianAvailabilityPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link TechnicianAvailabilityPort} using native SQL.
 *
 * <p>Queries the {@code technicians}, {@code technician_skills}, and
 * {@code job_technician_assignments} tables directly to find technicians
 * available in a candidate time slot (SYSTEM_DESIGN §5.3 Step 3, §4.3.2).
 *
 * <p>Uses JDBC instead of JPA to avoid a bi-directional entity join across
 * package boundaries and to match the raw SQL pattern from SYSTEM_DESIGN.
 */
@Service
public class TechnicianAvailabilityProvider implements TechnicianAvailabilityPort {

    private static final String FIND_AVAILABLE_SQL =
            """
            SELECT t.user_id
            FROM technicians t
            JOIN technician_skills ts ON ts.technician_id = t.user_id
            JOIN employees e ON e.user_id = t.user_id
            WHERE e.dealership_id = ?
              AND t.is_available = true
              AND ts.skill = ?
              AND (
                CASE ts.level
                  WHEN 'TRAINEE'    THEN 1
                  WHEN 'JUNIOR'     THEN 2
                  WHEN 'TECHNICIAN' THEN 3
                  WHEN 'SENIOR'     THEN 4
                  WHEN 'MASTER'     THEN 5
                END
              ) >= (
                CASE CAST(? AS text)
                  WHEN 'TRAINEE'    THEN 1
                  WHEN 'JUNIOR'     THEN 2
                  WHEN 'TECHNICIAN' THEN 3
                  WHEN 'SENIOR'     THEN 4
                  WHEN 'MASTER'     THEN 5
                END
              )
              AND NOT EXISTS (
                SELECT 1 FROM job_technician_assignments a
                WHERE a.technician_id = t.user_id
                  AND a.assigned_start_time < ?
                  AND a.assigned_end_time > ?
              )
            """;

    private final JdbcTemplate jdbc;

    public TechnicianAvailabilityProvider(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findAvailableTechnicianIds(
            UUID dealershipId, String requiredSkill, String minSkillLevel, Instant slotStart, Instant slotEnd) {
        return jdbc.queryForList(
                FIND_AVAILABLE_SQL, UUID.class, dealershipId, requiredSkill, minSkillLevel, slotEnd, slotStart);
    }
}
