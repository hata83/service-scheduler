package dev.hata.scheduler.platform;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Restores the seeded fixtures so the Bruno and Playwright suites can run
 * again. The task deletes the registered test customers, restores the seeded
 * centralized profiles, and clears the idempotency, rate-limit, OTP and session
 * keys in Redis.
 */
public final class AuthFixtureReset {

    private static final Logger log = LoggerFactory.getLogger(AuthFixtureReset.class);

    private static final String TEST_PHONE_PATTERN = "+849000009%";
    private static final String ADMIN_ID = "00000000-0000-0000-0000-000000000002";
    private static final String MANAGER_ID = "00000000-0000-0000-0000-000000000003";
    private static final String TECH_ID = "00000000-0000-0000-0000-000000000004";
    private static final String CUSTOMER_ID = "00000000-0000-0000-0000-000000000005";

    private AuthFixtureReset() {}

    public static void main(String[] args) throws Exception {
        String url = env("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5433/service_scheduler");
        String user = env("SPRING_DATASOURCE_USERNAME", "scheduler");
        String password = env("SPRING_DATASOURCE_PASSWORD", "scheduler");

        resetDatabase(url, user, password);
        resetCache(env("SPRING_DATA_REDIS_HOST", "localhost"), Integer.parseInt(env("SPRING_DATA_REDIS_PORT", "6379")));
        log.info("Auth fixtures reset");
    }

    private static void resetDatabase(String url, String user, String password) throws Exception {
        String sql = "DELETE FROM profiles WHERE user_id IN (SELECT id FROM users WHERE phone LIKE '"
                + TEST_PHONE_PATTERN
                + "'); DELETE FROM users WHERE phone LIKE '"
                + TEST_PHONE_PATTERN
                + "'; UPDATE profiles SET first_name = 'Demo', last_name = 'Admin' WHERE user_id = '"
                + ADMIN_ID
                + "'; UPDATE profiles SET first_name = 'Demo', last_name = 'Manager' WHERE user_id = '"
                + MANAGER_ID
                + "'; UPDATE profiles SET first_name = 'Demo', last_name = 'Technician' WHERE user_id = '"
                + TECH_ID
                + "'; UPDATE profiles SET first_name = 'Demo', last_name = 'Customer' WHERE user_id = '"
                + CUSTOMER_ID
                + "'; UPDATE users SET email = NULL WHERE id IN ('"
                + ADMIN_ID
                + "', '"
                + MANAGER_ID
                + "', '"
                + TECH_ID
                + "', '"
                + CUSTOMER_ID
                + "');";
        try (Connection connection = DriverManager.getConnection(url, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void resetCache(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        for (String pattern : Set.of("idempotency:*", "ratelimit:*", "otp:*")) {
            Set<String> keys = template.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                template.delete(keys);
            }
        }
        factory.destroy();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
