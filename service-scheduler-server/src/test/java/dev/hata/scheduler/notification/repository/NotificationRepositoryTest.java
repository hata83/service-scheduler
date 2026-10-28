package dev.hata.scheduler.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.notification.model.CommunicationLog;
import dev.hata.scheduler.notification.model.OutboxEvent;
import dev.hata.scheduler.notification.model.OutboxStatus;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationRepositoryTest {

    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB", "service_scheduler")
            .withEnv("POSTGRES_USER", "scheduler")
            .withEnv("POSTGRES_PASSWORD", "scheduler")
            .withExposedPorts(5432)
            .waitingFor(Wait.forListeningPort());

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                        + "/service_scheduler");
        registry.add("spring.datasource.username", () -> "scheduler");
        registry.add("spring.datasource.password", () -> "scheduler");
        registry.add("spring.data.redis.host", () -> REDIS.getHost());
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeAll
    static void startContainers() {
        POSTGRES.start();
        REDIS.start();
    }

    @AfterAll
    static void stopContainers() {
        POSTGRES.stop();
        REDIS.stop();
    }

    @Autowired
    private OutboxEventRepository outboxEvents;

    @Autowired
    private CommunicationLogRepository communicationLogs;

    @Test
    void canPersistAndQueryPendingOutboxEvents() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                eventId,
                "APPOINTMENT_CONFIRMED",
                "APPOINTMENT",
                aggregateId,
                "{\"appointmentId\":\"" + aggregateId + "\",\"status\":\"CONFIRMED\"}",
                OutboxStatus.PENDING,
                0,
                Instant.now(),
                null);

        outboxEvents.save(event);

        List<OutboxEvent> pending = outboxEvents.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        assertThat(pending).extracting(OutboxEvent::getId).contains(eventId);
    }

    @Test
    void canPersistAndQueryCommunicationLogsByCustomer() {
        UUID logId = UUID.randomUUID();
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID customerId = UUID.fromString("00000000-0000-0000-0000-000000000005");

        CommunicationLog log = new CommunicationLog(
                logId,
                dealershipId,
                null,
                customerId,
                "SSE",
                "+84900000004",
                "appointment.confirmed",
                "{\"status\":\"CONFIRMED\"}",
                "DELIVERED",
                null,
                Instant.now());

        communicationLogs.save(log);

        List<CommunicationLog> customerLogs = communicationLogs.findByCustomerIdOrderByDispatchedAtDesc(customerId);
        assertThat(customerLogs).extracting(CommunicationLog::getId).contains(logId);
    }
}
