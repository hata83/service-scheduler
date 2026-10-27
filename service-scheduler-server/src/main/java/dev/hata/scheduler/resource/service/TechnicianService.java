package dev.hata.scheduler.resource.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.EmployeeProfileProvider;
import dev.hata.scheduler.resource.model.Employee;
import dev.hata.scheduler.resource.model.Technician;
import dev.hata.scheduler.resource.repository.EmployeeRepository;
import dev.hata.scheduler.resource.repository.TechnicianSkillRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee and technician resource domain service (design D4/D13). Implements
 * {@link EmployeeProfileProvider} so the auth feature resolves an employee's
 * dealership scope ({@code ADMIN} global, {@code MANAGER}/{@code TECHNICIAN} local)
 * and technician career grade and multi-skill taxonomy without package coupling.
 */
@Service
public class TechnicianService implements EmployeeProfileProvider {

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

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeeScope> findScopeByUserId(UUID userId) {
        return employees
                .findScopeByUserId(userId)
                .map(scope -> new EmployeeScope(scope.getRole().name(), scope.getDealershipId()));
    }

    private EmployeeView toView(Employee employee) {
        if (employee instanceof Technician technician) {
            List<SkillView> skillViews = skills.findByTechnicianId(technician.getUserId()).stream()
                    .map(s -> new SkillView(s.getSkill().name(), s.getLevel().name()))
                    .toList();
            return new EmployeeView(
                    technician.getUserId(),
                    technician.getEmployeeId(),
                    technician.getRole().name(),
                    technician.getDealershipId(),
                    technician.getGrade().name(),
                    technician.isAvailable(),
                    skillViews);
        }
        return new EmployeeView(
                employee.getUserId(),
                employee.getEmployeeId(),
                employee.getRole().name(),
                employee.getDealershipId(),
                null,
                null,
                List.of());
    }
}
