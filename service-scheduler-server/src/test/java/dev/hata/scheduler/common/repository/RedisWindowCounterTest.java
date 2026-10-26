package dev.hata.scheduler.common.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Covers the fixed-window counter (ADR-007 / design D7).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisWindowCounterTest {

    @Mock
    private StringRedisTemplate redis;

    private RedisWindowCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RedisWindowCounter(redis);
    }

    @Test
    void acceptsARequestInsideTheLimit() {
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("key")), any()))
                .thenReturn(2L);

        assertThat(counter.tryAcquire("key", 5, Duration.ofSeconds(60))).isTrue();
    }

    @Test
    void rejectsARequestBeyondTheLimit() {
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("key")), any()))
                .thenReturn(6L);

        assertThat(counter.tryAcquire("key", 5, Duration.ofSeconds(60))).isFalse();
    }

    @Test
    void rejectsWhenTheCounterFails() {
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("key")), any()))
                .thenReturn(null);

        assertThat(counter.tryAcquire("key", 5, Duration.ofSeconds(60))).isFalse();
    }
}
