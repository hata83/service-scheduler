package dev.hata.scheduler.resource.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.port.TechnicianProfileProvider;
import dev.hata.scheduler.resource.model.Employee;
import dev.hata.scheduler.resource.model.Technician;
import dev.hata.scheduler.resource.repository.EmployeeRepository;
import dev.hata.scheduler.resource.repository.TechnicianSkillRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee and technician resource domain service (design D4/D13). Implements
 * {@link TechnicianProfileProvider} so the auth feature resolves an employee's
 * dealership scope ({@code ADMIN} global, {@code MANAGER}/{@code TECHNICIAN} local)
 * and technician career level and multi-skill taxonomy without package coupling.
 */
@Service
public class TechnicianService implements TechnicianProfileProvider {

    private final EmployeeRepository employees;
    private final TechnicianSkillRepository skills;

    public TechnicianService(EmployeeRepository employees, TechnicianSkillRepository skills) {
        this.employees = employees;
        this.skills = skills;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeView> findByUserId(UUID userId) {
        return employees.findByUserId(userId).map(this::toView);
    }

    private EmployeeView toView(Employee employee) {
        if (employee instanceof Technician technician) {
            List<SkillView> skillViews = skills.findByTechnicianId(technician.getUserId()).stream()
                    .map(s -> new SkillView(s.getSkill().name(), s.getLevel().name()))
                    .toList();
            return new EmployeeView(
                    technician.getUserId(),
                    technician.getEmployeeId(),
                    technician.getRole(),
                    technician.getDealershipId(),
                    technician.getLevel().name(),
                    technician.isAvailable(),
                    skillViews);
        }
        return new EmployeeView(
                employee.getUserId(),
                employee.getEmployeeId(),
                employee.getRole(),
                employee.getDealershipId(),
                null,
                null,
                List.of());
    }
}
