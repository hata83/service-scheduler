package dev.hata.scheduler.resource.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/**
 * Base {@code employees} entity for staff roles ({@link EmployeeRole#ADMIN},
 * {@link EmployeeRole#MANAGER}, {@link EmployeeRole#TECHNICIAN}). Enforces the
 * employee dealership scope rule: {@code ADMIN} is global ({@code dealershipId == null}),
 * while {@code MANAGER} and {@code TECHNICIAN} belong to a specific local {@code dealershipId}.
 */
@Entity
@Table(name = "employees")
@Inheritance(strategy = InheritanceType.JOINED)
public class Employee {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "employee_id", nullable = false, unique = true)
    private String employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private EmployeeRole role;

    @Column(name = "dealership_id")
    private UUID dealershipId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Employee() {}

    /**
     * Fills the base employee fields and validates the role-to-dealership scope.
     */
    public void assignEmployee(UUID userId, String employeeId, EmployeeRole role, UUID dealershipId) {
        Objects.requireNonNull(role, "Employee role is required.");
        if (role == EmployeeRole.ADMIN && dealershipId != null) {
            throw new IllegalArgumentException("ADMIN employee is global and must not have a dealershipId.");
        }
        if ((role == EmployeeRole.MANAGER || role == EmployeeRole.TECHNICIAN) && dealershipId == null) {
            throw new IllegalArgumentException(role + " employee is local and requires a dealershipId.");
        }
        this.userId = Objects.requireNonNull(userId);
        this.employeeId = Objects.requireNonNull(employeeId);
        this.role = role;
        this.dealershipId = dealershipId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public EmployeeRole getRole() {
        return role;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
