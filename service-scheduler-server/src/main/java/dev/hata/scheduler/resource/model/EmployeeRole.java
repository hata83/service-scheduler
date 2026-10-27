package dev.hata.scheduler.resource.model;

/**
 * Staff employee authorization roles in {@code employees.role} (SYSTEM_DESIGN §4.2.2b).
 * {@link #ADMIN} is global ({@code dealership_id IS NULL}), while {@link #MANAGER}
 * and {@link #TECHNICIAN} are local to a specific dealership ({@code dealership_id IS NOT NULL}).
 */
public enum EmployeeRole {
    ADMIN,
    MANAGER,
    TECHNICIAN
}
