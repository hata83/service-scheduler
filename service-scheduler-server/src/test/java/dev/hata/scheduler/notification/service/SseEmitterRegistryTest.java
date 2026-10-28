package dev.hata.scheduler.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    void registerReturnsEmitterAndTracksActiveCount() {
        SseEmitter emitter = registry.register("+84900000001");
        assertThat(emitter).isNotNull();
        assertThat(registry.getActiveCount()).isEqualTo(1);
    }

    @Test
    void registeringSecondEmitterForSamePhoneEvictsPreviousOne() {
        SseEmitter first = registry.register("+84900000001");
        assertThat(registry.getActiveCount()).isEqualTo(1);

        SseEmitter second = registry.register("+84900000001");
        assertThat(second).isNotSameAs(first);
        assertThat(registry.getActiveCount()).isEqualTo(1);
    }

    @Test
    void sendDispatchesEventToMatchingEmitter() {
        registry.register("+84900000001");
        boolean delivered = registry.send("+84900000001", "appointment.confirmed", Map.of("status", "CONFIRMED"));
        assertThat(delivered).isTrue();
    }

    @Test
    void sendReturnsFalseWhenNoEmitterRegistered() {
        boolean delivered = registry.send("+84900000099", "appointment.confirmed", Map.of("status", "CONFIRMED"));
        assertThat(delivered).isFalse();
    }

    @Test
    void heartbeatSendsPingToAllActiveEmitters() {
        registry.register("+84900000001");
        registry.register("+84900000002");
        assertThat(registry.getActiveCount()).isEqualTo(2);

        registry.sendHeartbeats();
        assertThat(registry.getActiveCount()).isEqualTo(2);
    }

    @Test
    void removeRemovesEmitterFromRegistry() {
        registry.register("+84900000001");
        assertThat(registry.getActiveCount()).isEqualTo(1);

        registry.remove("+84900000001");
        assertThat(registry.getActiveCount()).isZero();

        // Safe no-op when not found
        registry.remove("+84900000001");
    }

    @Test
    void registerWithNullOrBlankPhoneThrowsException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.register("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerWithShortPhoneMasksSafely() {
        SseEmitter e = registry.register("12");
        assertThat(e).isNotNull();
    }

    @Test
    void sendAndRemoveWithNullPhoneSafelyNoop() {
        assertThat(registry.send(null, "event", "data")).isFalse();
        registry.remove(null);
    }

    @Test
    void sendRemovesEmitterWhenExceptionThrown() {
        SseEmitter emitter = registry.register("+84900000001");
        emitter.complete(); // complete emitter so next send throws IllegalStateException
        boolean sent = registry.send("+84900000001", "test.event", "data");
        assertThat(sent).isFalse();
        assertThat(registry.getActiveCount()).isZero();
    }

    @Test
    void heartbeatEvictsCompletedEmitter() {
        SseEmitter emitter = registry.register("+84900000001");
        emitter.complete();
        registry.sendHeartbeats();
        assertThat(registry.getActiveCount()).isZero();
    }
}
