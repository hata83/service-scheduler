package dev.hata.scheduler.auth.model;

/**
 * Global account classification in {@code users.type}.
 * Staff authorization roles ({@code ADMIN}, {@code MANAGER}, {@code TECHNICIAN})
 * live in {@code employees.role}.
 */
public enum UserType {
    CUSTOMER,
    EMPLOYEE
}
