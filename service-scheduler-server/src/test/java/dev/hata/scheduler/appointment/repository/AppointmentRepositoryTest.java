package dev.hata.scheduler.appointment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.appointment.model.Appointment;
import dev.hata.scheduler.appointment.model.AppointmentStatus;
import dev.hata.scheduler.appointment.model.JobStatus;
import dev.hata.scheduler.appointment.model.ServiceJob;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AppointmentRepositoryTest {

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
    private AppointmentRepository appointmentRepository;

    @Test
    @Transactional
    void canPersistAndQueryAppointmentWithChildServiceJobs() {
        UUID appointmentId = UUID.randomUUID();
        UUID dealershipId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID customerId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        UUID categoryId = UUID.fromString("11111111-0000-0000-0000-000000000001");
        UUID bay1 = UUID.fromString("33333333-0000-0000-0000-000000000001");
        UUID serviceType1 = UUID.fromString("22222222-0000-0000-0000-000000000001");
        UUID idempotencyKey = UUID.randomUUID();

        Instant start = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        Appointment appointment = new Appointment();
        appointment.setId(appointmentId);
        appointment.setDealershipId(dealershipId);
        appointment.setCustomerId(customerId);
        appointment.setVehicleCategoryId(categoryId);
        appointment.setVehicleVin("1HGBH41JXMN999999");
        appointment.setStartTime(start);
        appointment.setEndTime(end);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setEstimateCost(new BigDecimal("60.00"));
        appointment.setIdempotencyKey(idempotencyKey);
        appointment.setCreatedAt(Instant.now());
        appointment.setUpdatedAt(Instant.now());
        appointment.setCreatedBy(customerId);
        appointment.setUpdatedBy(customerId);

        ServiceJob job = new ServiceJob();
        job.setId(UUID.randomUUID());
        job.setAppointment(appointment);
        job.setServiceTypeId(serviceType1);
        job.setBayId(bay1);
        job.setStartTime(start);
        job.setEndTime(end);
        job.setStatus(JobStatus.PENDING);
        job.setBaseMinutes(30);
        job.setEstimateCost(new BigDecimal("60.00"));
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job.setCreatedBy(customerId);
        job.setUpdatedBy(customerId);

        appointment.addServiceJob(job);

        appointmentRepository.save(appointment);

        Optional<Appointment> found = appointmentRepository.findByIdAndCustomerId(appointmentId, customerId);
        assertThat(found).isPresent();
        assertThat(found.get().getServiceJobs()).hasSize(1);
        assertThat(found.get().getVehicleVin()).isEqualTo("1HGBH41JXMN999999");

        List<Appointment> customerList = appointmentRepository.findByCustomerIdOrderByStartTimeDesc(customerId);
        assertThat(customerList).extracting(Appointment::getId).contains(appointmentId);
    }
}
