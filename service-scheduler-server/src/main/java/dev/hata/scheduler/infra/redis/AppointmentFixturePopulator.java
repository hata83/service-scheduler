package dev.hata.scheduler.infra.redis;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refreshes demo appointment fixtures through JdbcTemplate on startup so DB
 * bookings and Redis slot grids are computed from the same "today" (design D2/D7).
 */
@Component
@Profile({"dev", "test"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppointmentFixturePopulator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppointmentFixturePopulator.class);
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;

    public AppointmentFixturePopulator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(TIMEZONE);
        upsertAppointments(today);
        log.info("Refreshed demo appointment fixtures relative to {}", today);
    }

    private void upsertAppointments(LocalDate today) {
        // Day +1 maintenance at 08:00-09:00; +2 at 09:00-10:00; +3 at 10:00-11:00
        String sql = """
            INSERT INTO appointments
            (id, dealership_id, customer_id, vehicle_category_id, vehicle_vin,
             start_time, end_time, status, estimate_cost, total_cost,
             idempotency_key, version, created_by, updated_by)
            VALUES
            (?, ?, ?, ?, ?,
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             'CONFIRMED', 150.00, 150.00,
             ?, 0, ?, ?),
            (?, ?, ?, ?, ?,
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             'CONFIRMED', 180.00, 180.00,
             ?, 0, ?, ?),
            (?, ?, ?, ?, ?,
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             'CONFIRMED', 160.00, 160.00,
             ?, 0, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
              start_time = EXCLUDED.start_time,
              end_time = EXCLUDED.end_time;
            """;
        jdbc.update(sql,
                UUID.fromString("44444444-0000-0000-0000-000000000001"), // id
                UUID.fromString("00000000-0000-0000-0000-000000000001"), // dealership
                UUID.fromString("00000000-0000-0000-0000-000000000005"), // customer
                UUID.fromString("11111111-0000-0000-0000-000000000001"), // category
                "1HGCR2F83HA000001",
                today.plusDays(1).atTime(8, 0), today.plusDays(1).atTime(9, 0),
                UUID.fromString("44444444-0000-0000-0000-100000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),

                UUID.fromString("44444444-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                UUID.fromString("11111111-0000-0000-0000-000000000002"),
                "1HGCR2F83HA000002",
                today.plusDays(2).atTime(9, 0), today.plusDays(2).atTime(10, 0),
                UUID.fromString("44444444-0000-0000-0000-100000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),

                UUID.fromString("44444444-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                UUID.fromString("11111111-0000-0000-0000-000000000004"),
                "1HGCR2F83HA000003",
                today.plusDays(3).atTime(10, 0), today.plusDays(3).atTime(11, 0),
                UUID.fromString("44444444-0000-0000-0000-100000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );

        // Service jobs
        String jobsSql = """
            INSERT INTO service_jobs
            (id, appointment_id, service_type_id, bay_id,
             start_time, end_time, status, base_minutes, estimate_cost, final_cost,
             created_by, updated_by)
            VALUES (?, ?, ?, ?,
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             'PENDING', 60, 150.00, 150.00,
             ?, ?),
            (?, ?, ?, ?,
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             'PENDING', 60, 180.00, 180.00,
             ?, ?),
            (?, ?, ?, ?,
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             'PENDING', 60, 160.00, 160.00,
             ?, ?)
            ON CONFLICT (id) DO UPDATE SET
              start_time = EXCLUDED.start_time,
              end_time = EXCLUDED.end_time;
            """;
        jdbc.update(jobsSql,
                UUID.fromString("66666666-0000-0000-0000-000000000001"),
                UUID.fromString("44444444-0000-0000-0000-000000000001"),
                UUID.fromString("22222222-0000-0000-0000-000000000002"),
                UUID.fromString("33333333-0000-0000-0000-000000000002"),
                today.plusDays(1).atTime(8, 0), today.plusDays(1).atTime(9, 0),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("66666666-0000-0000-0000-000000000002"),
                UUID.fromString("44444444-0000-0000-0000-000000000002"),
                UUID.fromString("22222222-0000-0000-0000-000000000002"),
                UUID.fromString("33333333-0000-0000-0000-000000000002"),
                today.plusDays(2).atTime(9, 0), today.plusDays(2).atTime(10, 0),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("66666666-0000-0000-0000-000000000003"),
                UUID.fromString("44444444-0000-0000-0000-000000000003"),
                UUID.fromString("22222222-0000-0000-0000-000000000002"),
                UUID.fromString("33333333-0000-0000-0000-000000000003"),
                today.plusDays(3).atTime(10, 0), today.plusDays(3).atTime(11, 0),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );

        // Technician assignments
        String techSql = """
            INSERT INTO job_technician_assignments
            (id, job_id, technician_id, role,
             assigned_start_time, assigned_end_time,
             created_by, updated_by)
            VALUES (?, ?, ?, 'PRIMARY_TECH',
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             ?, ?),
            (?, ?, ?, 'PRIMARY_TECH',
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             ?, ?),
            (?, ?, ?, 'PRIMARY_TECH',
             ? AT TIME ZONE 'Asia/Ho_Chi_Minh', ? AT TIME ZONE 'Asia/Ho_Chi_Minh',
             ?, ?)
            ON CONFLICT (id) DO UPDATE SET
              assigned_start_time = EXCLUDED.assigned_start_time,
              assigned_end_time = EXCLUDED.assigned_end_time;
            """;
        jdbc.update(techSql,
                UUID.fromString("77777777-0000-0000-0000-000000000001"),
                UUID.fromString("66666666-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                today.plusDays(1).atTime(8, 0), today.plusDays(1).atTime(9, 0),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("77777777-0000-0000-0000-000000000002"),
                UUID.fromString("66666666-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                today.plusDays(2).atTime(9, 0), today.plusDays(2).atTime(10, 0),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("77777777-0000-0000-0000-000000000003"),
                UUID.fromString("66666666-0000-0000-0000-000000000003"),
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                today.plusDays(3).atTime(10, 0), today.plusDays(3).atTime(11, 0),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );
    }
}
