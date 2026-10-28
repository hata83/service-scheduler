package dev.hata.scheduler.notification.dto;

import java.time.Instant;
import java.util.UUID;

import dev.hata.scheduler.notification.model.CommunicationLog;

/**
 * DTO response representing a dispatched notification record.
 */
public record CommunicationLogResponse(
        UUID id, String channel, String eventType, String deliveryStatus, Instant dispatchedAt) {

    public static CommunicationLogResponse from(CommunicationLog log) {
        return new CommunicationLogResponse(
                log.getId(), log.getChannel(), log.getEventType(), log.getDeliveryStatus(), log.getDispatchedAt());
    }
}
