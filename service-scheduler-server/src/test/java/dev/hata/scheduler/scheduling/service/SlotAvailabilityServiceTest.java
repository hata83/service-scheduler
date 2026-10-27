package dev.hata.scheduler.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.DealershipConfigPort;
import dev.hata.scheduler.common.provider.DealershipConfigPort.DealershipConfig;
import dev.hata.scheduler.scheduling.dto.SlotAvailabilityResponse;
import dev.hata.scheduler.scheduling.model.ServiceType;
import dev.hata.scheduler.scheduling.repository.ServiceTypeRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlotAvailabilityServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE_ID = UUID.fromString("22222222-0000-0000-0000-000000000002");

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    @Mock
    private DealershipConfigPort dealershipConfigPort;

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @Mock
    private SlotGridComputeService gridCompute;

    @Mock
    private SlotCacheEvictionService eviction;

    private SlotAvailabilityService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        // Default generation stamp: 0:0 (no eviction observed). Tests that
        // exercise the stale-write guard override this to a different value.
        when(eviction.readGeneration(DEALERSHIP_ID, LocalDate.of(2026, 10, 28))).thenReturn("0:0");
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        service = new SlotAvailabilityService(
                redis, mapper, dealershipConfigPort, serviceTypeRepository, gridCompute, eviction);
    }

    // -------------------------------------------------------------------------
    // getCachedAvailableSlots — cache hit
    // -------------------------------------------------------------------------

    @Test
    void getCachedAvailableSlotsReturnsSlotsWhenCacheHit() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;
        String json = "{\"generation\":\"0:0\","
                + "\"slots\":[{\"startTime\":\"2026-10-28T08:00:00+07:00\","
                + "\"endTime\":\"2026-10-28T09:00:00+07:00\",\"available\":false},"
                + "{\"startTime\":\"2026-10-28T09:00:00+07:00\","
                + "\"endTime\":\"2026-10-28T10:00:00+07:00\",\"available\":true}]}";

        DealershipConfig config = buildConfig();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(values.get(key)).thenReturn(json);

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).available()).isFalse();
        assertThat(slots.get(1).available()).isTrue();
        verify(gridCompute, never()).compute(any(), any(), any(), any());
    }

    @Test
    void getCachedAvailableSlotsRecomputesWhenCacheStampIsStale() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;
        // Cached grid stamped during a previous generation; the live counter
        // has since advanced to 1:0 (a booking evicted this grid after compute).
        String json = "{\"generation\":\"0:0\","
                + "\"slots\":[{\"startTime\":\"2026-10-28T08:00:00+07:00\","
                + "\"endTime\":\"2026-10-28T09:00:00+07:00\",\"available\":true}]}";
        when(values.get(key)).thenReturn(json);
        when(eviction.readGeneration(DEALERSHIP_ID, date)).thenReturn("1:0");

        DealershipConfig config = buildConfig();
        ServiceType serviceType = buildServiceType();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(serviceType));
        SlotAvailabilityResponse computed = new SlotAvailabilityResponse(
                java.time.OffsetDateTime.parse("2026-10-28T09:00:00+07:00"),
                java.time.OffsetDateTime.parse("2026-10-28T10:00:00+07:00"),
                true);
        when(gridCompute.compute(any(), any(), any(), any())).thenReturn(List.of(computed));

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        // Stale grid rejected; recompute ran.
        assertThat(slots).hasSize(1);
        verify(gridCompute).compute(any(), any(), eq(date), any());
    }

    @Test
    void getCachedAvailableSlotsRecoversWhenGenerationReadFails() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;

        // Redis read of the generation counter throws: the service must treat
        // the cache as unreadable (miss) and still return a live grid — and must
        // NOT write a grid back whose freshness cannot be verified.
        when(eviction.readGeneration(DEALERSHIP_ID, date)).thenThrow(new RuntimeException("Redis down"));
        when(values.get(key)).thenReturn(null);

        DealershipConfig config = buildConfig();
        ServiceType serviceType = buildServiceType();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(serviceType));
        SlotAvailabilityResponse computed = new SlotAvailabilityResponse(
                java.time.OffsetDateTime.parse("2026-10-28T08:00:00+07:00"),
                java.time.OffsetDateTime.parse("2026-10-28T09:00:00+07:00"),
                true);
        when(gridCompute.compute(any(), any(), any(), any())).thenReturn(List.of(computed));

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).hasSize(1);
        verify(gridCompute).compute(any(), any(), eq(date), any());
        verify(values, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }
    // -------------------------------------------------------------------------

    @Test
    void getCachedAvailableSlotsComputesAndCachesOnCacheMiss() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;

        when(values.get(key)).thenReturn(null);

        DealershipConfig config = buildConfig();
        ServiceType serviceType = buildServiceType();

        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(serviceType));

        SlotAvailabilityResponse computed = new SlotAvailabilityResponse(
                java.time.OffsetDateTime.parse("2026-10-28T08:00:00+07:00"),
                java.time.OffsetDateTime.parse("2026-10-28T09:00:00+07:00"),
                true);
        when(gridCompute.compute(eq(config), eq(serviceType), eq(date), any(Instant.class)))
                .thenReturn(List.of(computed));

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).available()).isTrue();
        verify(values).set(eq(key), anyString(), any(java.time.Duration.class));
    }

    // -------------------------------------------------------------------------
    // getCachedAvailableSlots — blank/malformed cache falls through to compute
    // -------------------------------------------------------------------------

    @Test
    void getCachedAvailableSlotsTriggersComputeWhenCacheBlank() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;

        when(values.get(key)).thenReturn("   ");

        DealershipConfig config = buildConfig();
        ServiceType serviceType = buildServiceType();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(serviceType));
        when(gridCompute.compute(any(), any(), any(), any())).thenReturn(List.of());

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
        verify(gridCompute).compute(any(), any(), eq(date), any());
    }

    @Test
    void getCachedAvailableSlotsTriggersComputeWhenCacheMalformed() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;

        when(values.get(key)).thenReturn("not-valid-json{{");

        DealershipConfig config = buildConfig();
        ServiceType serviceType = buildServiceType();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(serviceType));
        when(gridCompute.compute(any(), any(), any(), any())).thenReturn(List.of());

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getCachedAvailableSlots — Redis read exception falls through to compute
    // -------------------------------------------------------------------------

    @Test
    void getCachedAvailableSlotsTriggersComputeWhenRedisThrowsException() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;

        when(values.get(key)).thenThrow(new RuntimeException("Redis connection refused"));

        DealershipConfig config = buildConfig();
        ServiceType serviceType = buildServiceType();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(serviceType));
        when(gridCompute.compute(any(), any(), any(), any())).thenReturn(List.of());

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getCachedAvailableSlots — date out of range
    // -------------------------------------------------------------------------

    @Test
    void getCachedAvailableSlotsThrowsWhenDateExceedsMaxAdvanceBookingDays() {
        LocalDate farFuture = LocalDate.of(2026, 10, 28).plusDays(8);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + farFuture + ":" + SERVICE_TYPE_ID;

        when(values.get(key)).thenReturn(null);

        DealershipConfig config = buildConfig(); // maxAdvanceBookingDays = 7
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, farFuture))
                .isInstanceOf(SlotDateOutOfRangeException.class)
                .hasMessageContaining("exceeds the maximum advance booking window");
    }

    // -------------------------------------------------------------------------
    // getCachedAvailableSlots — dealership or service type not found
    // -------------------------------------------------------------------------

    @Test
    void getCachedAvailableSlotsReturnsEmptyWhenDealershipNotFound() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;

        when(values.get(key)).thenReturn(null);
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.empty());

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
        verify(gridCompute, never()).compute(any(), any(), any(), any());
    }

    @Test
    void getCachedAvailableSlotsReturnsEmptyWhenServiceTypeNotFound() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;

        when(values.get(key)).thenReturn(null);
        DealershipConfig config = buildConfig();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.empty());

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
        verify(gridCompute, never()).compute(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // getCachedAvailableSlots — Redis write failure is non-fatal
    // -------------------------------------------------------------------------

    @Test
    void getCachedAvailableSlotsReturnsResultEvenWhenRedisCacheWriteFails() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        String key = "slot:grid:" + DEALERSHIP_ID + ":" + date + ":" + SERVICE_TYPE_ID;

        when(values.get(key)).thenReturn(null);

        DealershipConfig config = buildConfig();
        ServiceType serviceType = buildServiceType();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(serviceType));

        SlotAvailabilityResponse computed = new SlotAvailabilityResponse(
                java.time.OffsetDateTime.parse("2026-10-28T08:00:00+07:00"),
                java.time.OffsetDateTime.parse("2026-10-28T09:00:00+07:00"),
                true);
        when(gridCompute.compute(any(), any(), any(), any())).thenReturn(List.of(computed));

        // Simulate Redis write failure — result must still be returned (non-fatal).
        org.mockito.Mockito.doThrow(new RuntimeException("Redis write failure"))
                .when(values)
                .set(anyString(), anyString(), any(java.time.Duration.class));

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).available()).isTrue();
    }

    @Test
    void getCachedAvailableSlotsReturnsEmptyForPastDateOnCacheMiss() {
        LocalDate date = LocalDate.of(2020, 1, 1);

        DealershipConfig config = buildConfig();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        ServiceType serviceType = buildServiceType();
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(serviceType));

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
        verify(gridCompute, never()).compute(any(), any(), any(), any());
    }

    @Test
    void getCachedAvailableSlotsRejectsServiceTypeFromAnotherDealership() {
        LocalDate date = LocalDate.of(2026, 10, 28);

        DealershipConfig config = buildConfig();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        ServiceType st = buildServiceTypeWithDealership(UUID.randomUUID());
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
        verify(gridCompute, never()).compute(any(), any(), any(), any());
    }

    @Test
    void getCachedAvailableSlotsRejectsInactiveServiceType() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 28);

        DealershipConfig config = buildConfig();
        when(dealershipConfigPort.findConfig(DEALERSHIP_ID)).thenReturn(Optional.of(config));
        ServiceType st = buildServiceTypeWithDealership(DEALERSHIP_ID);
        set(st, "active", false);
        when(serviceTypeRepository.findById(SERVICE_TYPE_ID)).thenReturn(Optional.of(st));

        List<SlotAvailabilityResponse> slots = service.getCachedAvailableSlots(DEALERSHIP_ID, SERVICE_TYPE_ID, date);

        assertThat(slots).isEmpty();
        verify(gridCompute, never()).compute(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // evictSlotsByDate — delegates to SlotCacheEvictionService
    // -------------------------------------------------------------------------

    @Test
    void evictSlotsByDateDelegatesToEvictionService() {
        LocalDate date = LocalDate.of(2026, 10, 28);

        service.evictSlotsByDate(DEALERSHIP_ID, date);

        verify(eviction).evictByDate(DEALERSHIP_ID, date);
    }

    // -------------------------------------------------------------------------
    // evictSlotsByDealership — delegates to SlotCacheEvictionService
    // -------------------------------------------------------------------------

    @Test
    void evictSlotsByDealershipDelegatesToEvictionService() {
        service.evictSlotsByDealership(DEALERSHIP_ID);

        verify(eviction).evictByDealership(DEALERSHIP_ID);
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private DealershipConfig buildConfig() {
        return new DealershipConfig(
                DEALERSHIP_ID,
                "Asia/Ho_Chi_Minh",
                60,
                LocalTime.of(8, 0),
                LocalTime.of(17, 30),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                7,
                3600);
    }

    private ServiceType buildServiceType() {
        return buildServiceTypeWithDealership(DEALERSHIP_ID);
    }

    private ServiceType buildServiceTypeWithDealership(UUID dealershipId) {
        try {
            ServiceType st = new ServiceType();
            set(st, "id", SERVICE_TYPE_ID);
            set(st, "dealershipId", dealershipId);
            set(st, "active", true);
            set(st, "estimatedMinutes", 60);
            set(st, "allowedBayTypes", new java.util.ArrayList<>(List.of("STANDARD")));
            return st;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void set(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = findField(target.getClass(), fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
            throw ex;
        }
    }
}
