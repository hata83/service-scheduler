package dev.hata.scheduler.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SlotAvailabilityServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private SlotAvailabilityService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        service = new SlotAvailabilityService(redis, mapper);
    }

    @Test
    void getCachedAvailableSlotsReturnsSlotsWhenCacheHit() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + SERVICE_TYPE_ID + ":" + date;
        String json = "[{\"startTime\":\"2026-10-28T08:00:00+07:00\","
                + "\"endTime\":\"2026-10-28T09:00:00+07:00\",\"available\":false},"
                + "{\"startTime\":\"2026-10-28T09:00:00+07:00\","
                + "\"endTime\":\"2026-10-28T10:00:00+07:00\",\"available\":true}]";

        when(values.get(key)).thenReturn(json);

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).available()).isFalse();
        assertThat(slots.get(1).available()).isTrue();
    }

    @Test
    void getCachedAvailableSlotsReturnsEmptyListWhenCacheMiss() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + SERVICE_TYPE_ID + ":" + date;

        when(values.get(key)).thenReturn(null);

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
    }

    @Test
    void getCachedAvailableSlotsReturnsEmptyListWhenBlankOrMalformedJson() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + SERVICE_TYPE_ID + ":" + date;

        when(values.get(key)).thenReturn("   ");
        assertThat(service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date))
                .isEmpty();

        when(values.get(key)).thenReturn("malformed-not-json");
        assertThat(service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date))
                .isEmpty();
    }

    @Test
    void getCachedAvailableSlotsReturnsEmptyListWhenRedisThrowsException() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + SERVICE_TYPE_ID + ":" + date;

        when(values.get(key)).thenThrow(new RuntimeException("Redis connection refused"));
        assertThat(service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date))
                .isEmpty();
    }
}
