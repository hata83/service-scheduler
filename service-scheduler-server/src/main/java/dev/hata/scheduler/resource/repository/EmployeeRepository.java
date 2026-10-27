package dev.hata.scheduler.resource.repository;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.resource.model.Employee;
import dev.hata.scheduler.resource.model.EmployeeRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Reads and writes the {@code employees} (and joined {@code technicians}) tables.
 */
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByUserId(UUID userId);

    /**
     * Projection view of role and dealership assignment.
     */
    interface EmployeeScopeProjection {
        EmployeeRole getRole();

        UUID getDealershipId();
    }

    @Query("SELECT e.role AS role, e.dealershipId AS dealershipId FROM Employee e WHERE e.userId = :userId")
    Optional<EmployeeScopeProjection> findScopeByUserId(@Param("userId") UUID userId);
}
