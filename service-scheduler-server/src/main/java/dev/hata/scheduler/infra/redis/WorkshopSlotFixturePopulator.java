package dev.hata.scheduler.infra.redis;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Populates Redis cache with demo slot grid keys for development and testing.
 */
@Component
@Profile({"dev", "test"})
public class WorkshopSlotFixturePopulator {

    private static final Logger log = LoggerFactory.getLogger(WorkshopSlotFixturePopulator.class);

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_OIL = UUID.fromString("22222222-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_MAINTENANCE = UUID.fromString("22222222-0000-0000-0000-000000000002");
    private static final UUID SERVICE_TYPE_BRAKES = UUID.fromString("22222222-0000-0000-0000-000000000003");

    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public WorkshopSlotFixturePopulator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Populates demo slot grid fixtures for upcoming days.
     */
    public void populate(LocalDate today) {
        populateMaintenanceSlots(today);
        populateOilSlots(today);
        populateBrakeSlots(today);
        log.info("Populated demo slot availability grids in Redis");
    }

    /**
     * Clears all demo slot grid keys from Redis.
     */
    public void clearDemoKeys() {
        try {
            Set<String> keys = redis.keys("slot:grid:*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception ex) {
            log.warn("Failed to clear demo slot keys from Redis: {}", ex.getMessage(), ex);
        }
    }

    private void populateMaintenanceSlots(LocalDate today) {
        // Day 1 has booked slot at 08:00
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_MAINTENANCE, today.plusDays(1), 60, List.of(LocalTime.of(8, 0)));
        // Day 2 has booked slot at 09:00
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_MAINTENANCE, today.plusDays(2), 60, List.of(LocalTime.of(9, 0)));
        // Day 3 has booked slot at 10:00
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_MAINTENANCE, today.plusDays(3), 60, List.of(LocalTime.of(10, 0)));
    }

    private void populateOilSlots(LocalDate today) {
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_OIL, today.plusDays(1), 30, List.of());
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_OIL, today.plusDays(2), 30, List.of());
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_OIL, today.plusDays(3), 30, List.of());
    }

    private void populateBrakeSlots(LocalDate today) {
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_BRAKES, today.plusDays(1), 120, List.of());
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_BRAKES, today.plusDays(2), 120, List.of());
        writeGrid(DEALERSHIP_ID, SERVICE_TYPE_BRAKES, today.plusDays(3), 120, List.of());
    }

    private void writeGrid(
            UUID dealershipId,
            UUID serviceTypeId,
            LocalDate date,
            int durationMinutes,
            List<LocalTime> bookedStartTimes) {
        String key = "slot:grid:" + dealershipId + ":" + serviceTypeId + ":" + date;
        try {
            String json = buildGridJson(date, durationMinutes, bookedStartTimes);
            redis.opsForValue().set(key, json, TTL);
        } catch (Exception ex) {
            log.warn("Failed to write slot grid for key {}: {}", key, ex.getMessage(), ex);
        }
    }

    private String buildGridJson(LocalDate date, int durationMinutes, List<LocalTime> bookedStartTimes) {
        List<LocalTime> startTimes = generateDailyIntervals(durationMinutes);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < startTimes.size(); i++) {
            LocalTime start = startTimes.get(i);
            LocalTime end = start.plusMinutes(durationMinutes);
            ZonedDateTime startZdt = ZonedDateTime.of(date, start, TIMEZONE);
            ZonedDateTime endZdt = ZonedDateTime.of(date, end, TIMEZONE);
            boolean available = !bookedStartTimes.contains(start);

            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"startTime\":\"")
                    .append(startZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .append("\",\"endTime\":\"")
                    .append(endZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .append("\",\"available\":")
                    .append(available)
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<LocalTime> generateDailyIntervals(int durationMinutes) {
        List<LocalTime> slots = new ArrayList<>();
        // Morning slots: 08:00 to 12:00
        LocalTime current = LocalTime.of(8, 0);
        LocalTime lunchStart = LocalTime.of(12, 0);
        while (current.plusMinutes(durationMinutes).isBefore(lunchStart)
                || current.plusMinutes(durationMinutes).equals(lunchStart)) {
            slots.add(current);
            current = current.plusMinutes(durationMinutes);
        }
        // Afternoon slots: 13:00 to 17:30
        current = LocalTime.of(13, 0);
        LocalTime closeTime = LocalTime.of(17, 30);
        while (current.plusMinutes(durationMinutes).isBefore(closeTime)
                || current.plusMinutes(durationMinutes).equals(closeTime)) {
            slots.add(current);
            current = current.plusMinutes(durationMinutes);
        }
        return slots;
    }
}
