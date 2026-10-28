package dev.hata.scheduler.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.appointment.dto.AppointmentResponse;
import dev.hata.scheduler.appointment.dto.CreateAppointmentRequest;
import dev.hata.scheduler.appointment.dto.ServiceJobItemRequest;
import dev.hata.scheduler.appointment.model.AppointmentStatus;
import dev.hata.scheduler.appointment.repository.AppointmentRepository;
import dev.hata.scheduler.appointment.service.AppointmentService;
import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.common.exception.GlobalExceptionHandler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Concurrency and database exclusion constraint integration tests.
 * Verifies multi-job booking, duration limits, intra-job overlap rejection,
 * PostgreSQL GIST exclusion constraints (no_vehicle_overlap and no_bay_overlap),
 * and idempotency replay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AppointmentConcurrencyIntegrationTest {

    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_DB", "service_scheduler")
            .withEnv("POSTGRES_USER", "scheduler")
            .withEnv("POSTGRES_PASSWORD", "scheduler")
            .withExposedPorts(5432)
            .waitingFor(Wait.forListeningPort());

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).waitingFor(Wait.forListeningPort());

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final String CUSTOMER_PHONE = "+84900000004";
    private static final UUID SEDAN_CATEGORY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID BAY_1 = UUID.fromString("33333333-0000-0000-0000-000000000001");
    private static final UUID BAY_2 = UUID.fromString("33333333-0000-0000-0000-000000000002");
    private static final UUID SERVICE_TYPE_EXPRESS =
            UUID.fromString("22222222-0000-0000-0000-000000000001"); // 30m, 60.00
    private static final UUID SERVICE_TYPE_STANDARD =
            UUID.fromString("22222222-0000-0000-0000-000000000002"); // 60m, 150.00

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
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    @DisplayName("7.1 Multi-job booking with 2 service types succeeds with 201 Created and auto-confirmed status")
    void multiJobBookingWithTwoServiceTypesSucceeds() {
        String vin = "1HGCR2F83HA100001";
        UUID idempotencyKey = UUID.fromString("b0000001-0000-0000-0000-000000000001");
        Instant start1 = Instant.parse("2026-11-25T14:00:00Z");
        Instant end1 = Instant.parse("2026-11-25T14:30:00Z");
        Instant start2 = Instant.parse("2026-11-25T14:30:00Z");
        Instant end2 = Instant.parse("2026-11-25T15:30:00Z");

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                SEDAN_CATEGORY_ID,
                vin,
                List.of(
                        new ServiceJobItemRequest(SERVICE_TYPE_EXPRESS, BAY_1, start1, end1),
                        new ServiceJobItemRequest(SERVICE_TYPE_STANDARD, BAY_2, start2, end2)));

        AppointmentResponse response = appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, idempotencyKey);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.vehicleVin()).isEqualTo(vin);
        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.estimateCost()).isEqualByComparingTo(new BigDecimal("210.00"));
        assertThat(response.serviceJobs()).hasSize(2);
        assertThat(response.startTime()).isEqualTo(start1);
        assertThat(response.endTime()).isEqualTo(end2);
    }

    @Test
    @DisplayName("7.2 Intra-appointment overlapping service jobs reject with 400 Bad Request")
    void intraAppointmentOverlappingJobsRejectsWithBadRequest() {
        String vin = "1HGCR2F83HA100002";
        UUID idempotencyKey = UUID.fromString("b0000001-0000-0000-0000-000000000002");
        Instant start1 = Instant.parse("2026-11-25T16:00:00Z");
        Instant end1 = Instant.parse("2026-11-25T16:30:00Z");
        Instant start2 = Instant.parse("2026-11-25T16:15:00Z"); // Overlaps job 1
        Instant end2 = Instant.parse("2026-11-25T17:15:00Z");

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                SEDAN_CATEGORY_ID,
                vin,
                List.of(
                        new ServiceJobItemRequest(SERVICE_TYPE_EXPRESS, BAY_1, start1, end1),
                        new ServiceJobItemRequest(SERVICE_TYPE_STANDARD, BAY_2, start2, end2)));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("overlap");
                });
    }

    @Test
    @DisplayName("7.3 Service job duration exceeding base duration rejects with 400 Bad Request")
    void jobDurationExceedingBaseDurationRejectsWithBadRequest() {
        String vin = "1HGCR2F83HA100003";
        UUID idempotencyKey = UUID.fromString("b0000001-0000-0000-0000-000000000003");
        // Express lane base duration is 30m, but 60m is requested
        Instant start = Instant.parse("2026-11-25T18:00:00Z");
        Instant end = Instant.parse("2026-11-25T19:00:00Z");

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                SEDAN_CATEGORY_ID,
                vin,
                List.of(new ServiceJobItemRequest(SERVICE_TYPE_EXPRESS, BAY_1, start, end)));

        assertThatThrownBy(() -> appointmentService.book(request, CUSTOMER_ID, CUSTOMER_PHONE, idempotencyKey))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("exceeds base duration");
                });
    }

    @Test
    @DisplayName("7.4 PostgreSQL no_vehicle_overlap EXCLUDE constraint rejects vehicle overlap with 409 Conflict")
    void databaseConstraintRejectsOverlappingVehicleVinWithConflict() {
        String vin = "1HGCR2F83HA100004";
        UUID key1 = UUID.fromString("b0000001-0000-0000-0000-000000000004");
        UUID key2 = UUID.fromString("b0000001-0000-0000-0000-000000000005");
        Instant start1 = Instant.parse("2026-11-26T09:00:00Z");
        Instant end1 = Instant.parse("2026-11-26T09:30:00Z");

        // First booking succeeds
        CreateAppointmentRequest req1 = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                SEDAN_CATEGORY_ID,
                vin,
                List.of(new ServiceJobItemRequest(SERVICE_TYPE_EXPRESS, BAY_1, start1, end1)));
        AppointmentResponse res1 = appointmentService.book(req1, CUSTOMER_ID, CUSTOMER_PHONE, key1);
        assertThat(res1).isNotNull();

        // Second booking for SAME vehicle in overlapping time window (different bay)
        Instant start2 = Instant.parse("2026-11-26T09:15:00Z");
        Instant end2 = Instant.parse("2026-11-26T10:15:00Z");
        CreateAppointmentRequest req2 = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                SEDAN_CATEGORY_ID,
                vin,
                List.of(new ServiceJobItemRequest(SERVICE_TYPE_STANDARD, BAY_2, start2, end2)));

        assertThatThrownBy(() -> appointmentService.book(req2, CUSTOMER_ID, CUSTOMER_PHONE, key2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    DataIntegrityViolationException dive = (DataIntegrityViolationException) ex;
                    MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/api/v1/appointments");
                    ResponseEntity<ApiProblem> problem = globalExceptionHandler.handleDataIntegrity(dive, mockRequest);
                    assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(problem.getBody()).isNotNull();
                    assertThat(problem.getBody().status()).isEqualTo(409);
                    assertThat(problem.getBody().title()).isEqualTo("Vehicle schedule conflict");
                });
    }

    @Test
    @DisplayName("7.5 PostgreSQL no_bay_overlap EXCLUDE constraint rejects overlapping bay bookings with 409 Conflict")
    void databaseConstraintRejectsOverlappingWorkshopBayWithConflict() {
        String vin1 = "1HGCR2F83HA100005";
        String vin2 = "1HGCR2F83HA100006";
        UUID key1 = UUID.fromString("b0000001-0000-0000-0000-000000000006");
        UUID key2 = UUID.fromString("b0000001-0000-0000-0000-000000000007");
        Instant start1 = Instant.parse("2026-11-26T11:00:00Z");
        Instant end1 = Instant.parse("2026-11-26T11:30:00Z");

        // First booking takes BAY_1
        CreateAppointmentRequest req1 = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                SEDAN_CATEGORY_ID,
                vin1,
                List.of(new ServiceJobItemRequest(SERVICE_TYPE_EXPRESS, BAY_1, start1, end1)));
        AppointmentResponse res1 = appointmentService.book(req1, CUSTOMER_ID, CUSTOMER_PHONE, key1);
        assertThat(res1).isNotNull();

        // Second booking with DIFFERENT vehicle attempts to reserve SAME BAY_1 in overlapping window
        Instant start2 = Instant.parse("2026-11-26T11:15:00Z");
        Instant end2 = Instant.parse("2026-11-26T11:45:00Z");
        CreateAppointmentRequest req2 = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                SEDAN_CATEGORY_ID,
                vin2,
                List.of(new ServiceJobItemRequest(SERVICE_TYPE_EXPRESS, BAY_1, start2, end2)));

        assertThatThrownBy(() -> appointmentService.book(req2, CUSTOMER_ID, CUSTOMER_PHONE, key2))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    DataIntegrityViolationException dive = (DataIntegrityViolationException) ex;
                    MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", "/api/v1/appointments");
                    ResponseEntity<ApiProblem> problem = globalExceptionHandler.handleDataIntegrity(dive, mockRequest);
                    assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(problem.getBody()).isNotNull();
                    assertThat(problem.getBody().status()).isEqualTo(409);
                    assertThat(problem.getBody().title()).isEqualTo("Workshop bay conflict");
                });
    }

    @Test
    @DisplayName("7.6 Idempotency key replay returns cached response without duplicate database insertion")
    void idempotencyKeyReplayReturnsCachedResponse() {
        String vin = "1HGCR2F83HA100007";
        UUID key = UUID.fromString("b0000001-0000-0000-0000-000000000008");
        Instant start = Instant.parse("2026-11-26T14:00:00Z");
        Instant end = Instant.parse("2026-11-26T14:30:00Z");

        CreateAppointmentRequest req = new CreateAppointmentRequest(
                DEALERSHIP_ID,
                SEDAN_CATEGORY_ID,
                vin,
                List.of(new ServiceJobItemRequest(SERVICE_TYPE_EXPRESS, BAY_1, start, end)));

        AppointmentResponse first = appointmentService.book(req, CUSTOMER_ID, CUSTOMER_PHONE, key);
        assertThat(first).isNotNull();

        // Replay same key
        AppointmentResponse second = appointmentService.book(req, CUSTOMER_ID, CUSTOMER_PHONE, key);
        assertThat(second).isNotNull();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.vehicleVin()).isEqualTo(first.vehicleVin());
        assertThat(second.status()).isEqualTo(first.status());

        // Verify exactly one row in DB
        List<dev.hata.scheduler.appointment.model.Appointment> all = appointmentRepository.findAll();
        long matches =
                all.stream().filter(a -> key.equals(a.getIdempotencyKey())).count();
        assertThat(matches).isEqualTo(1);
    }
}
