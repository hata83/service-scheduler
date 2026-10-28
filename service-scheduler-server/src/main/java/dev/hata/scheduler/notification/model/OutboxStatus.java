package dev.hata.scheduler.notification.model;

/**
 * Status lifecycle for transactional outbox events.
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
