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

        assertThat(history).extracting("success").containsExactly(true, true);
        assertThat(history)
                .extracting("description")
                .containsExactly("platform and auth schema", "seed dealer and auth");
    }

    @Test
    void seedCountsMatchTheFourAccountContract() {
        Integer users = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        Integer dealerships = jdbc.queryForObject("SELECT count(*) FROM dealerships", Integer.class);
        Integer customers = jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class);
        Integer technicians = jdbc.queryForObject("SELECT count(*) FROM technicians", Integer.class);
        Integer skills = jdbc.queryForObject("SELECT count(*) FROM technician_skills", Integer.class);

        assertThat(users).isEqualTo(4);
        assertThat(dealerships).isEqualTo(1);
        assertThat(customers).isEqualTo(1);
        assertThat(technicians).isEqualTo(1);
        assertThat(skills).isEqualTo(1);
    }

    @Test
    void staffReferenceDemoDealershipWhileCustomerAndAdminAreGlobal() {
        String demoDealershipId = jdbc.queryForObject(
                "SELECT id::text FROM dealerships WHERE name = 'Hata Demo Service Center'", String.class);
        String customerDealershipId = jdbc.queryForObject(
                "SELECT u.dealership_id::text FROM users u WHERE u.phone = '+84900000004'", String.class);
        String adminDealershipId = jdbc.queryForObject(
                "SELECT u.dealership_id::text FROM users u WHERE u.phone = '+84900000001'", String.class);
        String managerDealershipId = jdbc.queryForObject(
                "SELECT u.dealership_id::text FROM users u WHERE u.phone = '+84900000002'", String.class);
        String techDealershipId = jdbc.queryForObject(
                "SELECT u.dealership_id::text FROM users u WHERE u.phone = '+84900000003'", String.class);

        assertThat(customerDealershipId).isNull();
        assertThat(adminDealershipId).isNull();
        assertThat(managerDealershipId).isEqualTo(demoDealershipId);
        assertThat(techDealershipId).isEqualTo(demoDealershipId);
    }

    @Test
    void seededDealershipCarriesDynamicFeaturesJson() {
        String features = jdbc.queryForObject(
                "SELECT features::text FROM dealerships WHERE name = 'Hata Demo Service Center'", String.class);

        assertThat(features).contains("rateLimits").contains("otpSendPerPhonePurpose");
    }

    @Test
    void phonesMapToThePinnedRoles() {
        List<String> roles = jdbc.queryForList("SELECT role FROM users ORDER BY phone", String.class);

        assertThat(roles).containsExactly("ADMIN", "MANAGER", "TECHNICIAN", "CUSTOMER");
    }

    private record BeginSchemaHistoryRow(String version, String description, boolean success) {}
}
