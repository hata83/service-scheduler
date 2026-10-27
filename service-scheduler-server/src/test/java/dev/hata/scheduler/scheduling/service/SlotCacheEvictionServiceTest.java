package dev.hata.scheduler.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SlotCacheEvictionServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SlotCacheEvictionService service;

    @BeforeEach
    void setUp() {
        service = new SlotCacheEvictionService(redis);
    }

    private void stubValueOps() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // --- evictByDate ---

    @Test
    void evictByDateDeletesMatchingKeys() {
        stubValueOps();
        LocalDate date = LocalDate.of(2026, 10, 28);
        String pattern = "slot:grid:" + DEALERSHIP_ID + ":*:" + date;
        String matchedKey = "slot:grid:" + DEALERSHIP_ID + ":22222222-0000-0000-0000-000000000002:" + date;

        when(redis.keys(pattern)).thenReturn(Set.of(matchedKey));

        service.evictByDate(DEALERSHIP_ID, date);

        verify(valueOps).increment(eq("slot:gen:" + DEALERSHIP_ID + ":" + date));
        verify(redis).delete(Set.of(matchedKey));
    }

    @Test
    void evictByDateDoesNotDeleteWhenNoKeysMatch() {
        stubValueOps();
        LocalDate date = LocalDate.of(2026, 10, 28);
        String pattern = "slot:grid:" + DEALERSHIP_ID + ":*:" + date;

        when(redis.keys(pattern)).thenReturn(Set.of());

        service.evictByDate(DEALERSHIP_ID, date);

        verify(valueOps).increment(eq("slot:gen:" + DEALERSHIP_ID + ":" + date));
        verify(redis, never()).delete(anyCollection());
    }

    @Test
    void evictByDateHandlesRedisExceptionGracefully() {
        stubValueOps();
        LocalDate date = LocalDate.of(2026, 10, 28);
        String pattern = "slot:grid:" + DEALERSHIP_ID + ":*:" + date;

        when(redis.keys(pattern)).thenThrow(new RuntimeException("Redis unavailable"));

        // Must not throw — eviction failure is non-fatal.
        service.evictByDate(DEALERSHIP_ID, date);

        verify(redis, never()).delete(anyCollection());
    }

    // --- evictByDealership ---

    @Test
    void evictByDealershipDeletesAllKeysForDealership() {
        stubValueOps();
        String pattern = "slot:grid:" + DEALERSHIP_ID + ":*";
        String key1 = "slot:grid:" + DEALERSHIP_ID + ":type1:2026-10-28";
        String key2 = "slot:grid:" + DEALERSHIP_ID + ":type2:2026-10-29";

        when(redis.keys(pattern)).thenReturn(Set.of(key1, key2));

        service.evictByDealership(DEALERSHIP_ID);

        verify(valueOps).increment(eq("slot:gen:" + DEALERSHIP_ID + ":all"));
        verify(redis).delete(Set.of(key1, key2));
    }

    @Test
    void evictByDealershipDoesNotDeleteWhenNoKeysExist() {
        stubValueOps();
        String pattern = "slot:grid:" + DEALERSHIP_ID + ":*";

        when(redis.keys(pattern)).thenReturn(null);

        service.evictByDealership(DEALERSHIP_ID);

        verify(valueOps).increment(eq("slot:gen:" + DEALERSHIP_ID + ":all"));
        verify(redis, never()).delete(anyCollection());
    }

    @Test
    void evictByDealershipHandlesRedisExceptionGracefully() {
        stubValueOps();
        String pattern = "slot:grid:" + DEALERSHIP_ID + ":*";

        when(redis.keys(pattern)).thenThrow(new RuntimeException("Redis unavailable"));

        // Must not throw.
        service.evictByDealership(DEALERSHIP_ID);

        verify(redis, never()).delete(anyCollection());
    }

    @Test
    void evictByDateBumpGenerationFailureIsLoggedAndDoesNotBlockEviction() {
        stubValueOps();
        LocalDate date = LocalDate.of(2026, 10, 28);

        // Counter bump fails (e.g. Redis down) — eviction must still proceed
        // best-effort and not throw, so a booking commit is never blocked.
        when(valueOps.increment(any())).thenThrow(new RuntimeException("Redis unavailable"));

        service.evictByDate(DEALERSHIP_ID, date);

        verify(redis, never()).delete(anyCollection());
    }

    // --- readGeneration ---

    @Test
    void readGenerationReturnsCompositeStampFromBothCounters() {
        stubValueOps();
        LocalDate date = LocalDate.of(2026, 10, 28);
        String dealKey = "slot:gen:" + DEALERSHIP_ID + ":all";
        String dayKey = "slot:gen:" + DEALERSHIP_ID + ":" + date;

        when(valueOps.get(dealKey)).thenReturn("3");
        when(valueOps.get(dayKey)).thenReturn("7");

        String stamp = service.readGeneration(DEALERSHIP_ID, date);

        assertThat(stamp).isEqualTo("3:7");
    }

    @Test
    void readGenerationReturnsZeroZeroWhenCountersUnset() {
        stubValueOps();
        LocalDate date = LocalDate.of(2026, 10, 28);
        String dealKey = "slot:gen:" + DEALERSHIP_ID + ":all";
        String dayKey = "slot:gen:" + DEALERSHIP_ID + ":" + date;

        when(valueOps.get(dealKey)).thenReturn(null);
        when(valueOps.get(dayKey)).thenReturn(null);

        assertThat(service.readGeneration(DEALERSHIP_ID, date)).isEqualTo("0:0");
    }

    @Test
    void readGenerationTreatsBlankCounterAsZero() {
        stubValueOps();
        LocalDate date = LocalDate.of(2026, 10, 28);
        String dealKey = "slot:gen:" + DEALERSHIP_ID + ":all";
        String dayKey = "slot:gen:" + DEALERSHIP_ID + ":" + date;

        when(valueOps.get(dealKey)).thenReturn(" ");
        when(valueOps.get(dayKey)).thenReturn("5");

        assertThat(service.readGeneration(DEALERSHIP_ID, date)).isEqualTo("0:5");
    }
}
