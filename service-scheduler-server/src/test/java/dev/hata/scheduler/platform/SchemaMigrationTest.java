package dev.hata.scheduler.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Applies V1/V2 against a real PostgreSQL 16 container (Testcontainers)
 * and verifies the seed contract (spec: Seed fixtures, design D4).
 * Redis 7 also spins up because the context needs it for health wiring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SchemaMigrationTest {

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
    private JdbcTemplate jdbc;

    @Test
    void migrationsApplyWithV1AndV2Success() {
        List<BeginSchemaHistoryRow> history = jdbc.query(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank",
                (rs, n) -> new BeginSchemaHistoryRow(
                        rs.getString("version"), rs.getString("description"), rs.getBoolean("success")));

        assertThat(history).extracting("success").containsOnly(true);
        assertThat(history)
                .extracting("description")
                .contains(
                        "platform and auth schema",
                        "workshop metadata and appointments schema",
                        "seed dealer and auth",
                        "seed metadata fixtures",
                        "seed schedule and appointments");
    }

    @Test
    void workshopMetadataAndAppointmentsSeedCountsResolve() {
        Integer vehicleCategories = jdbc.queryForObject("SELECT count(*) FROM vehicle_categories", Integer.class);
        Integer bays = jdbc.queryForObject("SELECT count(*) FROM workshop_bays", Integer.class);
        Integer serviceTypes = jdbc.queryForObject("SELECT count(*) FROM service_types", Integer.class);
        Integer techReqs =
                jdbc.queryForObject("SELECT count(*) FROM service_type_technician_requirements", Integer.class);
        Integer costs = jdbc.queryForObject("SELECT count(*) FROM service_type_costs", Integer.class);
        Integer appointments =
                jdbc.queryForObject("SELECT count(*) FROM appointments WHERE status = 'CONFIRMED'", Integer.class);
        Integer jobs = jdbc.queryForObject("SELECT count(*) FROM service_jobs", Integer.class);
        Integer assignments = jdbc.queryForObject("SELECT count(*) FROM job_technician_assignments", Integer.class);

        assertThat(vehicleCategories).isEqualTo(4);
        assertThat(bays).isEqualTo(3);
        assertThat(serviceTypes).isEqualTo(3);
        assertThat(techReqs).isEqualTo(3);
        assertThat(costs).isEqualTo(12);
        assertThat(appointments).isEqualTo(3);
        assertThat(jobs).isEqualTo(3);
        assertThat(assignments).isEqualTo(3);
    }

    @Test
    void seedCountsMatchTheFourAccountContract() {
        Integer users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        Integer profiles = jdbc.queryForObject("SELECT count(*) FROM profiles", Integer.class);
        Integer dealerships = jdbc.queryForObject("SELECT count(*) FROM dealerships", Integer.class);
        Integer employees = jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class);
        Integer technicians = jdbc.queryForObject("SELECT count(*) FROM technicians", Integer.class);
        Integer skills = jdbc.queryForObject("SELECT count(*) FROM technician_skills", Integer.class);

        assertThat(users).isEqualTo(4);
        assertThat(profiles).isEqualTo(4);
        assertThat(dealerships).isEqualTo(1);
        assertThat(employees).isEqualTo(3);
        assertThat(technicians).isEqualTo(1);
        assertThat(skills).isEqualTo(2);
    }

    @Test
    void centralizedProfilesAndEmployeeScopeTaxonomyResolve() {
        String demoDealershipId = jdbc.queryForObject(
                "SELECT id::text FROM dealerships WHERE name = 'Hata Demo Service Center'", String.class);
        String adminDealershipId = jdbc.queryForObject(
                "SELECT e.dealership_id::text FROM employees e "
                        + "JOIN users u ON u.id = e.user_id WHERE u.phone = '+84900000001'",
                String.class);
        String managerDealershipId = jdbc.queryForObject(
                "SELECT e.dealership_id::text FROM employees e "
                        + "JOIN users u ON u.id = e.user_id WHERE u.phone = '+84900000002'",
                String.class);
        String techDealershipId = jdbc.queryForObject(
                "SELECT e.dealership_id::text FROM employees e "
                        + "JOIN users u ON u.id = e.user_id WHERE u.phone = '+84900000003'",
                String.class);
        String techGrade = jdbc.queryForObject(
                "SELECT t.grade FROM technicians t "
                        + "JOIN users u ON u.id = t.user_id WHERE u.phone = '+84900000003'",
                String.class);

        assertThat(adminDealershipId).isNull();
        assertThat(managerDealershipId).isEqualTo(demoDealershipId);
        assertThat(techDealershipId).isEqualTo(demoDealershipId);
        assertThat(techGrade).isEqualTo("SENIOR");
    }

    @Test
    void seededDealershipCarriesFeaturesJson() {
        String features = jdbc.queryForObject(
                "SELECT features::text FROM dealerships WHERE name = 'Hata Demo Service Center'", String.class);

        assertThat(features).isEqualTo("{}");
    }

    @Test
    void phonesMapToThePinnedTypesAndEmployeeRoles() {
        List<String> userTypes = jdbc.queryForList("SELECT type FROM users ORDER BY phone", String.class);
        List<String> employeeRoles = jdbc.queryForList(
                "SELECT e.role FROM employees e JOIN users u ON u.id = e.user_id ORDER BY u.phone", String.class);

        assertThat(userTypes).containsExactly("EMPLOYEE", "EMPLOYEE", "EMPLOYEE", "CUSTOMER");
        assertThat(employeeRoles).containsExactly("ADMIN", "MANAGER", "TECHNICIAN");
    }

    private record BeginSchemaHistoryRow(String version, String description, boolean success) {}
}
