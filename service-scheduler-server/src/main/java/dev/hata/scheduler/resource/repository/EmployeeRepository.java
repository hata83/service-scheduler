package dev.hata.scheduler.resource.repository;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.resource.model.Employee;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes the {@code employees} (and joined {@code technicians}) tables.
 */
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByUserId(UUID userId);
}
