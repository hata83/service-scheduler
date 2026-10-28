package dev.hata.scheduler.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Verifies Redis demo slot grid fixture populator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkshopSlotFixturePopulatorTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private WorkshopSlotFixturePopulator populator;

    @BeforeEach
    void clean() {
        populator.clearDemoKeys();
    }

    @Test
    void populatesSlotGridKeysForUpcomingDays() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        populator.populate(today);

        String day1Key = "slot:grid:" + DEALERSHIP_ID + ":" + today.plusDays(1) + ":" + SERVICE_TYPE_ID;
        String day2Key = "slot:grid:" + DEALERSHIP_ID + ":" + today.plusDays(2) + ":" + SERVICE_TYPE_ID;
        String day3Key = "slot:grid:" + DEALERSHIP_ID + ":" + today.plusDays(3) + ":" + SERVICE_TYPE_ID;

        assertThat(redis.hasKey(day1Key)).isTrue();
        assertThat(redis.hasKey(day2Key)).isTrue();
        assertThat(redis.hasKey(day3Key)).isTrue();

        String day1Json = redis.opsForValue().get(day1Key);
        assertThat(day1Json).contains("\"available\":false");
        assertThat(day1Json).contains("\"available\":true");
        assertThat(day1Json).contains("08:00:00");

        String brakeKey =
                "slot:grid:" + DEALERSHIP_ID + ":" + today.plusDays(1) + ":22222222-0000-0000-0000-000000000003";
        assertThat(redis.hasKey(brakeKey)).isTrue();
        String brakeJson = redis.opsForValue().get(brakeKey);
        // 120-minute brake intervals: 08:00 to 10:00
        assertThat(brakeJson).contains("10:00:00");
    }
}
