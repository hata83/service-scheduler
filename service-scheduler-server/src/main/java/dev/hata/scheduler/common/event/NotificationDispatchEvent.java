package dev.hata.scheduler.common.event;

import java.util.UUID;

/**
 * Domain event published when a notification should be staged and dispatched.
 */
public record NotificationDispatchEvent(
        UUID outboxId,
        UUID dealershipId,
        UUID appointmentId,
        UUID customerId,
        String recipientPhone,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        String payload) {

    public NotificationDispatchEvent(
            UUID dealershipId,
            UUID appointmentId,
            UUID customerId,
            String recipientPhone,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String payload) {
        this(
                UUID.randomUUID(),
                dealershipId,
                appointmentId,
                customerId,
                recipientPhone,
                eventType,
                aggregateType,
                aggregateId,
                payload);
    }
}
