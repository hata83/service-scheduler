package dev.hata.scheduler.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.event.NotificationDispatchEvent;
import dev.hata.scheduler.notification.dto.CommunicationLogResponse;
import dev.hata.scheduler.notification.model.CommunicationLog;
import dev.hata.scheduler.notification.model.OutboxEvent;
import dev.hata.scheduler.notification.model.OutboxStatus;
import dev.hata.scheduler.notification.repository.CommunicationLogRepository;
import dev.hata.scheduler.notification.repository.OutboxEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private OutboxEventRepository outboxEvents;

    @Mock
    private CommunicationLogRepository communicationLogs;

    @Mock
    private SseEmitterRegistry emitterRegistry;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(outboxEvents, communicationLogs, emitterRegistry);
    }

    @Test
    void handleBeforeCommitStagesPendingOutboxEvent() {
        UUID dealershipId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        NotificationDispatchEvent event = new NotificationDispatchEvent(
                dealershipId,
                appointmentId,
                customerId,
                "+84900000004",
                "appointment.confirmed",
                "APPOINTMENT",
                appointmentId,
                "{\"status\":\"CONFIRMED\"}");

        notificationService.handleBeforeCommit(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEvents).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("appointment.confirmed");
        assertThat(saved.getAggregateType()).isEqualTo("APPOINTMENT");
        assertThat(saved.getAggregateId()).isEqualTo(appointmentId);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void handleAfterCommitDispatchesSseAndLogsCommunication() {
        UUID dealershipId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        NotificationDispatchEvent event = new NotificationDispatchEvent(
                dealershipId,
                appointmentId,
                customerId,
                "+84900000004",
                "appointment.confirmed",
                "APPOINTMENT",
                appointmentId,
                "{\"status\":\"CONFIRMED\"}");

        OutboxEvent pendingEvent = new OutboxEvent(
                UUID.randomUUID(),
                "appointment.confirmed",
                "APPOINTMENT",
                appointmentId,
                "{\"status\":\"CONFIRMED\"}",
                OutboxStatus.PENDING,
                0,
                Instant.now(),
                null);

        when(outboxEvents.findById(event.outboxId())).thenReturn(Optional.of(pendingEvent));
        when(emitterRegistry.send("+84900000004", "appointment.confirmed", "{\"status\":\"CONFIRMED\"}"))
                .thenReturn(true);

        notificationService.handleAfterCommit(event);

        verify(emitterRegistry).send("+84900000004", "appointment.confirmed", "{\"status\":\"CONFIRMED\"}");
        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(pendingEvent.getSentAt()).isNotNull();
        verify(outboxEvents).save(pendingEvent);

        ArgumentCaptor<CommunicationLog> captor = ArgumentCaptor.forClass(CommunicationLog.class);
        verify(communicationLogs).save(captor.capture());
        CommunicationLog saved = captor.getValue();
        assertThat(saved.getRecipient()).isEqualTo("+84900000004");
        assertThat(saved.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(saved.getEventType()).isEqualTo("appointment.confirmed");
    }

    @Test
    void handleAfterCommitUpdatesOnlyItsOwnOutboxRowWhenSent() {
        UUID outboxId1 = UUID.randomUUID();
        UUID outboxId2 = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        NotificationDispatchEvent undelivered = new NotificationDispatchEvent(
                outboxId1,
                UUID.randomUUID(),
                appointmentId,
                UUID.randomUUID(),
                "+84900000004",
                "appointment.requested",
                "APPOINTMENT",
                appointmentId,
                "{}");
        NotificationDispatchEvent delivered = new NotificationDispatchEvent(
                outboxId2,
                UUID.randomUUID(),
                appointmentId,
                UUID.randomUUID(),
                "+84900000004",
                "appointment.cancelled",
                "APPOINTMENT",
                appointmentId,
                "{}");

        OutboxEvent outbox1 = new OutboxEvent(
                outboxId1,
                "appointment.requested",
                "APPOINTMENT",
                appointmentId,
                "{}",
                OutboxStatus.PENDING,
                0,
                Instant.now(),
                null);
        OutboxEvent outbox2 = new OutboxEvent(
                outboxId2,
                "appointment.cancelled",
                "APPOINTMENT",
                appointmentId,
                "{}",
                OutboxStatus.PENDING,
                0,
                Instant.now(),
                null);

        when(emitterRegistry.send("+84900000004", "appointment.requested", "{}"))
                .thenReturn(false);
        when(emitterRegistry.send("+84900000004", "appointment.cancelled", "{}"))
                .thenReturn(true);
        when(outboxEvents.findById(outbox2.getId())).thenReturn(Optional.of(outbox2));

        notificationService.handleAfterCommit(undelivered);
        assertThat(outbox1.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox1.getSentAt()).isNull();

        notificationService.handleAfterCommit(delivered);
        assertThat(outbox2.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outbox2.getSentAt()).isNotNull();
        verify(outboxEvents).save(outbox2);
    }

    @Test
    void handleAfterCommitWhenSseNotSentLogsUndelivered() {
        UUID dealershipId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        NotificationDispatchEvent event = new NotificationDispatchEvent(
                dealershipId,
                appointmentId,
                customerId,
                "+84900000004",
                "appointment.confirmed",
                "APPOINTMENT",
                appointmentId,
                "{\"status\":\"CONFIRMED\"}");

        when(emitterRegistry.send("+84900000004", "appointment.confirmed", "{\"status\":\"CONFIRMED\"}"))
                .thenReturn(false);

        notificationService.handleAfterCommit(event);

        ArgumentCaptor<CommunicationLog> captor = ArgumentCaptor.forClass(CommunicationLog.class);
        verify(communicationLogs).save(captor.capture());
        CommunicationLog saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo("UNDELIVERED");
        assertThat(saved.getErrorMessage()).contains("No active SSE connection");
    }

    @Test
    void handleAfterCommitWithShortOrNullPhoneMasksCorrectly() {
        UUID dealershipId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        NotificationDispatchEvent eventNullPhone = new NotificationDispatchEvent(
                dealershipId,
                appointmentId,
                customerId,
                null,
                "appointment.cancelled",
                "APPOINTMENT",
                appointmentId,
                "{}");

        notificationService.handleAfterCommit(eventNullPhone);

        NotificationDispatchEvent eventShortPhone = new NotificationDispatchEvent(
                dealershipId,
                appointmentId,
                customerId,
                "12",
                "appointment.cancelled",
                "APPOINTMENT",
                appointmentId,
                "{}");

        notificationService.handleAfterCommit(eventShortPhone);
    }

    @Test
    void findCustomerLogsReturnsOrderedResponses() {
        UUID customerId = UUID.randomUUID();
        CommunicationLog log = new CommunicationLog(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                customerId,
                "SSE",
                "+84900000004",
                "appointment.confirmed",
                "{}",
                "DELIVERED",
                null,
                Instant.now());

        when(communicationLogs.findByCustomerIdOrderByDispatchedAtDesc(customerId))
                .thenReturn(List.of(log));

        List<CommunicationLogResponse> responses = notificationService.findCustomerLogs(customerId);
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).eventType()).isEqualTo("appointment.confirmed");
    }
}
