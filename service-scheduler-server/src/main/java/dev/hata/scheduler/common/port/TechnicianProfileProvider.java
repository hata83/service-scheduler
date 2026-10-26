package dev.hata.scheduler.common.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared contract for staff employee and technician records (design D4/D13).
 * Resolves an employee's {@code employeeId}, {@code role}, and {@code dealershipId}
 * ({@code null} for global {@code ADMIN}, local dealership UUID for {@code MANAGER}
 * and {@code TECHNICIAN}), plus technician career {@code level} and multi-skill
 * qualifications.
 */
@FunctionalInterface
public interface TechnicianProfileProvider {

    /**
     * One domain skill and its proficiency level for a technician.
     */
    record SkillView(String skill, String level) {}

    /**
     * Employee and optional technician specialization view.
     */
    record EmployeeView(
            UUID userId,
            String employeeId,
            String role,
            UUID dealershipId,
            String level,
            Boolean available,
            List<SkillView> skills) {}

    /**
     * Finds the employee (and technician specialization when applicable) by user id.
     */
    Optional<EmployeeView> findByUserId(UUID userId);
}
