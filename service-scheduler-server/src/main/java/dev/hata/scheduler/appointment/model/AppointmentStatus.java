package dev.hata.scheduler.appointment.model;

/**
 * Lifecycle status for customer appointments (SYSTEM_DESIGN §4.2.11).
 */
public enum AppointmentStatus {
    REQUESTED,
    CONFIRMED,
    CHECKED_IN,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    INVOICED,
    CANCELLED,
    REJECTED,
    NO_SHOW
}
