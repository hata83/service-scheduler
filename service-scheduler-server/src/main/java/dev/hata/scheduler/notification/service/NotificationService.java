package dev.hata.scheduler.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hata.scheduler.common.event.NotificationDispatchEvent;
import dev.hata.scheduler.notification.dto.CommunicationLogResponse;
import dev.hata.scheduler.notification.model.CommunicationLog;
import dev.hata.scheduler.notification.model.OutboxEvent;
import dev.hata.scheduler.notification.model.OutboxStatus;
import dev.hata.scheduler.notification.repository.CommunicationLogRepository;
import dev.hata.scheduler.notification.repository.OutboxEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Domain service for managing transactional outbox staging, SSE dispatching,
 * and communication audit history.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final OutboxEventRepository outboxEvents;
    private final CommunicationLogRepository communicationLogs;
    private final SseEmitterRegistry emitterRegistry;

    public NotificationService(
            OutboxEventRepository outboxEvents,
            CommunicationLogRepository communicationLogs,
            SseEmitterRegistry emitterRegistry) {
        this.outboxEvents = outboxEvents;
        this.communicationLogs = communicationLogs;
        this.emitterRegistry = emitterRegistry;
    }

    /**
     * Returns communication logs for the authenticated customer ordered by dispatchedAt desc.
     */
    @Transactional(readOnly = true)
    public List<CommunicationLogResponse> findCustomerLogs(UUID customerId) {
        return communicationLogs.findByCustomerIdOrderByDispatchedAtDesc(customerId).stream()
                .map(CommunicationLogResponse::from)
                .toList();
    }

    /**
     * Returns communication logs for staff. A MANAGER reads the logs of its dealership.
     * An ADMIN reads the logs of every dealership. A null scope reads all dealerships.
     */
    @Transactional(readOnly = true)
    public List<CommunicationLogResponse> findStaffLogs(UUID dealershipId) {
        List<CommunicationLog> logs = dealershipId == null
                ? communicationLogs.findAllByOrderByDispatchedAtDesc()
                : communicationLogs.findByDealershipIdOrderByDispatchedAtDesc(dealershipId);
        return logs.stream().map(CommunicationLogResponse::from).toList();
    }

    /**
     * Stages a pending outbox event inside the primary database transaction prior to commit.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(NotificationDispatchEvent event) {
        log.info("Staging outbox event {} for aggregate {}", event.eventType(), event.aggregateId());
        OutboxEvent outbox = new OutboxEvent(
                event.outboxId(),
                event.eventType(),
                event.aggregateType(),
                event.aggregateId(),
                event.payload(),
                OutboxStatus.PENDING,
                0,
                Instant.now(),
                null);
        outboxEvents.save(outbox);
    }

    /**
     * Dispatches the notification via SSE after successful commit and records an audit log.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAfterCommit(NotificationDispatchEvent event) {
        log.info(
                "Dispatching after-commit notification {} to phone {}",
                event.eventType(),
                maskPhone(event.recipientPhone()));
        boolean sent = emitterRegistry.send(event.recipientPhone(), event.eventType(), event.payload());
        String deliveryStatus = sent ? "DELIVERED" : "UNDELIVERED";

        if (sent) {
            outboxEvents.findById(event.outboxId()).ifPresent(outbox -> {
                outbox.setStatus(OutboxStatus.SENT);
                outbox.setSentAt(Instant.now());
                outboxEvents.save(outbox);
            });
        }

        CommunicationLog commLog = new CommunicationLog(
                UUID.randomUUID(),
                event.dealershipId(),
                event.appointmentId(),
                event.customerId(),
                "SSE",
                event.recipientPhone(),
                event.eventType(),
                event.payload(),
                deliveryStatus,
                sent ? null : "No active SSE connection for recipient",
                Instant.now());
        communicationLogs.save(commLog);

        dispatchToDealershipStaff(event);
    }

    /**
     * Fans the domain event out to every staff session of the dealership.
     * Each delivery writes its own audit record so the staff notification list matches the stream.
     */
    private void dispatchToDealershipStaff(NotificationDispatchEvent event) {
        Map<String, Boolean> deliveries =
                emitterRegistry.sendToDealershipStaff(event.dealershipId(), event.eventType(), event.payload());
        log.info(
                "Staff fan-out for {} to dealership {} reached {} staff sessions",
                event.eventType(),
                event.dealershipId(),
                deliveries.size());
        if (deliveries.isEmpty()) {
            return;
        }

        Instant dispatchedAt = Instant.now();
        for (Map.Entry<String, Boolean> delivery : deliveries.entrySet()) {
            boolean delivered = Boolean.TRUE.equals(delivery.getValue());
            CommunicationLog staffLog = new CommunicationLog(
                    UUID.randomUUID(),
                    event.dealershipId(),
                    event.appointmentId(),
                    event.customerId(),
                    "SSE",
                    delivery.getKey(),
                    event.eventType(),
                    event.payload(),
                    delivered ? "DELIVERED" : "UNDELIVERED",
                    delivered ? null : "No active SSE connection for recipient",
                    dispatchedAt);
            communicationLogs.save(staffLog);
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 3) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 3);
    }
}
