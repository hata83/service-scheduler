package dev.hata.scheduler.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

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
 * Covers the Redis session store (ADR-005). The record lives under
 * {@code auth:session:{jti}} and the logout deletes it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionStoreTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private SessionStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        store = new SessionStore(redis);
    }

    @Test
    void saveWritesTheSessionRecord() {
        store.save("jti-1", "user-1", Duration.ofHours(8));

        verify(values).set(eq("auth:session:jti-1"), eq("user-1"), eq(Duration.ofHours(8)));
    }

    @Test
    void existsReportsALiveSession() {
        when(redis.hasKey("auth:session:jti-1")).thenReturn(true);

        assertThat(store.exists("jti-1")).isTrue();
    }

    @Test
    void existsReportsAMissingSession() {
        when(redis.hasKey("auth:session:jti-1")).thenReturn(false);

        assertThat(store.exists("jti-1")).isFalse();
    }

    @Test
    void revokeDeletesTheSessionRecord() {
        store.revoke("jti-1");

        verify(redis).delete("auth:session:jti-1");
    }
}
