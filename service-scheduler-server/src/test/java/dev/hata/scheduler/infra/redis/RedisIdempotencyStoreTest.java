package dev.hata.scheduler.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import dev.hata.scheduler.common.idempotency.IdempotencyStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Covers the Redis idempotency store (ADR-008 / design D8).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private RedisIdempotencyStore storage;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        storage = new RedisIdempotencyStore(redis);
    }

    @Test
    void buildsTheNamespacedSlot() {
        assertThat(storage.slot("register", "+84900000999", "register-84900000999"))
                .isEqualTo("resp:idempotency:register:+84900000999:register-84900000999");
    }

    @Test
    void loadReturnsNullForAFreeSlot() {
        when(values.get("resp:slot")).thenReturn(null);

        assertThat(storage.load("resp:slot")).isNull();
    }

    @Test
    void loadReadsTheCachedResponse() {
        when(values.get("resp:slot")).thenReturn("{\"status\":201,\"body\":\"{\\\"role\\\":\\\"CUSTOMER\\\"}\"}");

        IdempotencyStore.CachedResponse cached = storage.load("resp:slot");

        assertThat(cached).isNotNull();
        assertThat(cached.status()).isEqualTo(201);
        assertThat(cached.body()).contains("CUSTOMER");
    }

    @Test
    void loadReturnsNullForABrokenRecord() {
        when(values.get("resp:slot")).thenReturn("not-json");

        assertThat(storage.load("resp:slot")).isNull();
    }

    @Test
    void storeWritesTheRecordWithTheTtlAndClaimsLocks() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(200, "{}");

        storage.store("resp:slot", cached);
        verify(values).set(eq("resp:slot"), any(String.class), any(Duration.class));

        when(values.setIfAbsent(eq("lock:resp:slot"), eq("owner"), any(Duration.class)))
                .thenReturn(true);
        assertThat(storage.tryClaim("resp:slot", "owner")).isTrue();

        when(values.setIfAbsent(eq("lock:resp:slot"), eq("owner"), any(Duration.class)))
                .thenReturn(false);
        assertThat(storage.tryClaim("resp:slot", "owner")).isFalse();

        storage.releaseClaim("resp:slot", "owner");
        verify(redis).execute(any(), any(), any());
    }

    @Test
    void fooAndFooLockAreDisjoint() {
        String keyFoo = storage.slot("e", "s", "foo");
        String keyFooLock = "lock:" + storage.slot("e", "s", "foo");
        assertThat(keyFoo).isNotEqualTo(keyFooLock);
    }
}
