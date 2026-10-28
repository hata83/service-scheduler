package dev.hata.scheduler.appointment.model;

/**
 * Status lifecycle for service jobs within an appointment (SYSTEM_DESIGN §4.2.12).
 */
public enum JobStatus {
    PENDING,
    IN_PROGRESS,
    ON_HOLD,
    DONE,
    CANCELLED
}
