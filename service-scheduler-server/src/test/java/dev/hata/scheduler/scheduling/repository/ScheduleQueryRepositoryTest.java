package dev.hata.scheduler.scheduling.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.scheduling.repository.ScheduleQueryRepository.HistoryRow;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Integration tests for the manager schedule history query. The suite runs against a real
 * PostgreSQL instance so that the paging and the timezone conversion use the database rules.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ScheduleQueryRepositoryTest {

    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB", "service_scheduler")
            .withEnv("POSTGRES_USER", "scheduler")
            .withEnv("POSTGRES_PASSWORD", "scheduler")
            .withExposedPorts(5432)
            .waitingFor(Wait.forListeningPort());

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DEALERSHIP_A = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID DEALERSHIP_B = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    private static final UUID CATEGORY_A = UUID.fromString("00000000-0000-0000-0000-00000000a101");
    private static final UUID CATEGORY_B = UUID.fromString("00000000-0000-0000-0000-00000000b101");

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
    private ScheduleQueryRepository scheduleQueryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void historyPagesKeepAStableOrderForAppointmentsWithTheSameStartTime() {
        insertDealership(DEALERSHIP_A, "Schedule Test Center", "Asia/Ho_Chi_Minh");
        insertCategory(CATEGORY_A, DEALERSHIP_A, "Schedule Test Car");
        Instant start = Instant.parse("2026-10-28T02:00:00Z");

        UUID first = insertAppointment(DEALERSHIP_A, CATEGORY_A, "STABLEVIN00000001", start);
        UUID second = insertAppointment(DEALERSHIP_A, CATEGORY_A, "STABLEVIN00000002", start);
        UUID third = insertAppointment(DEALERSHIP_A, CATEGORY_A, "STABLEVIN00000003", start);

        List<UUID> pageOne = idsOf(
                scheduleQueryRepository.findHistoryRows(DEALERSHIP_A, null, null, null, null, null, null, null, 0, 2));
        List<UUID> pageTwo = idsOf(
                scheduleQueryRepository.findHistoryRows(DEALERSHIP_A, null, null, null, null, null, null, null, 2, 2));

        assertThat(pageOne).hasSize(2);
        assertThat(pageTwo).hasSize(1);
        assertThat(pageOne).doesNotContainAnyElementsOf(pageTwo);
        assertThat(List.of(pageOne.get(0), pageOne.get(1), pageTwo.get(0)))
                .containsExactlyInAnyOrder(first, second, third);
    }

    @Test
    @Transactional
    void globalDateFilterUsesTheLocalDateOfEveryDealership() {
        insertDealership(DEALERSHIP_A, "Schedule Test Center", "Asia/Ho_Chi_Minh");
        insertDealership(DEALERSHIP_B, "Schedule Test Center East", "America/New_York");
        insertCategory(CATEGORY_A, DEALERSHIP_A, "Schedule Test Car");
        insertCategory(CATEGORY_B, DEALERSHIP_B, "Schedule Test Van");

        Instant shared = Instant.parse("2026-10-28T18:00:00Z");
        UUID inHoChiMinh = insertAppointment(DEALERSHIP_A, CATEGORY_A, "TIMEZONEVIN000001", shared);
        UUID inNewYork = insertAppointment(DEALERSHIP_B, CATEGORY_B, "TIMEZONEVIN000002", shared);

        List<UUID> october28 = idsOf(scheduleQueryRepository.findHistoryRows(
                null, null, null, null, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28), null, null, 0, 200));
        List<UUID> october29 = idsOf(scheduleQueryRepository.findHistoryRows(
                null, null, null, null, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 29), null, null, 0, 200));

        assertThat(october28).contains(inNewYork);
        assertThat(october28).doesNotContain(inHoChiMinh);
        assertThat(october29).contains(inHoChiMinh);
        assertThat(october29).doesNotContain(inNewYork);
    }

    private static List<UUID> idsOf(List<HistoryRow> rows) {
        return rows.stream().map(HistoryRow::getId).toList();
    }

    private void insertDealership(UUID dealershipId, String name, String timezone) {
        jdbcTemplate.update(
                "INSERT INTO dealerships (id, name, timezone, created_by, updated_by) VALUES (?, ?, ?, ?, ?)",
                dealershipId,
                name,
                timezone,
                ADMIN_ID,
                ADMIN_ID);
    }

    private void insertCategory(UUID categoryId, UUID dealershipId, String model) {
        jdbcTemplate.update(
                "INSERT INTO vehicle_categories (id, dealership_id, brand, model, type, created_by, updated_by)"
                        + " VALUES (?, ?, 'Schedule', ?, 'SEDAN', ?, ?)",
                categoryId,
                dealershipId,
                model,
                ADMIN_ID,
                ADMIN_ID);
    }

    private UUID insertAppointment(UUID dealershipId, UUID categoryId, String vin, Instant start) {
        UUID id = UUID.randomUUID();
        OffsetDateTime startAt = start.atOffset(ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO appointments (id, dealership_id, customer_id, vehicle_category_id, vehicle_vin,"
                        + " start_time, end_time, status, estimate_cost, idempotency_key, created_by, updated_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', 0, ?, ?, ?)",
                id,
                dealershipId,
                CUSTOMER_ID,
                categoryId,
                vin,
                startAt,
                startAt.plusSeconds(1800),
                UUID.randomUUID(),
                ADMIN_ID,
                ADMIN_ID);
        return id;
    }
}
