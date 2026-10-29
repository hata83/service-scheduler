package dev.hata.scheduler.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    void sendToDealershipStaffReachesOnlyStaffOfThatDealership() {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID dealershipB = UUID.fromString("00000000-0000-0000-0000-000000000002");

        registry.registerStaff("+84900000002", dealershipA);
        registry.registerStaff("+84900000003", dealershipB);

        Map<String, Boolean> deliveries =
                registry.sendToDealershipStaff(dealershipA, "appointment.requested", Map.of("status", "REQUESTED"));

        assertThat(deliveries).containsOnlyKeys("+84900000002");
        assertThat(deliveries.get("+84900000002")).isTrue();
    }

    @Test
    void sendToDealershipStaffReachesGlobalAdminSessions() {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");

        registry.registerStaff("+84900000001", null);

        Map<String, Boolean> deliveries =
                registry.sendToDealershipStaff(dealershipA, "appointment.cancelled", Map.of("status", "CANCELLED"));

        assertThat(deliveries).containsOnlyKeys("+84900000001");
        assertThat(deliveries.get("+84900000001")).isTrue();
    }

    @Test
    void customerRegistrationClearsStaffScopeOfSamePhone() {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");

        registry.registerStaff("+84900000002", dealershipA);
        registry.register("+84900000002");

        Map<String, Boolean> deliveries =
                registry.sendToDealershipStaff(dealershipA, "appointment.requested", Map.of("status", "REQUESTED"));

        assertThat(deliveries).isEmpty();
    }

    @Test
    void staffSessionCountCountsDealershipAndAdminSessions() {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID dealershipB = UUID.fromString("00000000-0000-0000-0000-000000000002");

        registry.registerStaff("+84900000002", dealershipA);
        registry.registerStaff("+84900000003", dealershipB);
        registry.registerStaff("+84900000001", null);

        assertThat(registry.getStaffSessionCount(dealershipA)).isEqualTo(2);
        assertThat(registry.getStaffSessionCount(dealershipB)).isEqualTo(2);
    }

    @Test
    void removeClearsStaffSessionScope() {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");

        registry.registerStaff("+84900000002", dealershipA);
        registry.remove("+84900000002");

        assertThat(registry.getStaffSessionCount(dealershipA)).isZero();
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

    @Test
    void failedSendClearsTheStaffRoutingMetadata() {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");

        SseEmitter emitter = registry.registerStaff("+84900000002", dealershipA);
        emitter.complete();

        boolean sent = registry.send("+84900000002", "test.event", "data");

        assertThat(sent).isFalse();
        assertThat(registry.getActiveCount()).isZero();
        assertThat(registry.getStaffSessionCount(dealershipA)).isZero();
    }

    @Test
    void failedHeartbeatClearsTheStaffRoutingMetadata() {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");

        SseEmitter emitter = registry.registerStaff("+84900000002", dealershipA);
        emitter.complete();

        registry.sendHeartbeats();

        assertThat(registry.getActiveCount()).isZero();
        assertThat(registry.getStaffSessionCount(dealershipA)).isZero();
    }

    @Test
    void failedGlobalAdminSendClearsTheGlobalStaffScope() {
        SseEmitter emitter = registry.registerStaff("+84900000001", null);
        emitter.complete();

        registry.send("+84900000001", "test.event", "data");

        assertThat(registry.getStaffSessionCount(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isZero();
    }

    @Test
    void lateCallbackOfAReplacedEmitterKeepsTheScopeOfTheNewSession() {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID dealershipB = UUID.fromString("00000000-0000-0000-0000-000000000002");

        SseEmitter first = registry.registerStaff("+84900000002", dealershipA);
        registry.registerStaff("+84900000002", dealershipB);

        registry.evict("+84900000002", first, "late callback");

        assertThat(registry.getStaffSessionCount(dealershipB)).isEqualTo(1);
        assertThat(registry.getStaffSessionCount(dealershipA)).isZero();
        assertThat(registry.getActiveCount()).isEqualTo(1);
    }

    @Test
    void concurrentReconnectAndEvictionKeepTheRoutingOfTheActiveSession() throws Exception {
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID dealershipB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String phone = "+84900000002";

        for (int round = 0; round < 300; round++) {
            SseEmitter first = registry.registerStaff(phone, dealershipA);
            CyclicBarrier start = new CyclicBarrier(2);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Thread registrar = new Thread(() -> {
                awaitBarrier(start, failure);
                registry.registerStaff(phone, dealershipB);
            });
            Thread evictor = new Thread(() -> {
                awaitBarrier(start, failure);
                registry.evict(phone, first, "concurrent eviction");
            });
            registrar.start();
            evictor.start();
            registrar.join();
            evictor.join();

            assertThat(failure.get()).isNull();
            assertThat(registry.getStaffSessionCount(dealershipB)).isEqualTo(1);
            assertThat(registry.getStaffSessionCount(dealershipA)).isZero();
            assertThat(registry.getActiveCount()).isEqualTo(1);
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier, AtomicReference<Throwable> failure) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            failure.compareAndSet(null, ex);
        }
    }

    @Test
    void fanOutNeverDeliversAPriorScopeEventToAReconnectedStream() throws Exception {
        RecordingRegistry recording = new RecordingRegistry();
        UUID dealershipA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID dealershipB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String phone = "+84900000002";
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int round = 0; round < 2000; round++) {
            recording.registerStaff(phone, dealershipA);
            CyclicBarrier start = new CyclicBarrier(2);

            Thread registrar = new Thread(() -> {
                awaitBarrier(start, failure);
                recording.registerStaff(phone, dealershipB);
            });
            Thread fanout = new Thread(() -> {
                awaitBarrier(start, failure);
                recording.sendToDealershipStaff(dealershipA, "appointment.requested", Map.of());
            });
            registrar.start();
            fanout.start();
            registrar.join();
            fanout.join();

            RecordingEmitter replacement = recording.created.get(recording.created.size() - 1);
            if (replacement.sentCount() > 0) {
                failure.compareAndSet(
                        null,
                        new IllegalStateException("the replacement stream received the event of the prior scope"));
                break;
            }
        }

        assertThat(failure.get()).isNull();
    }

    /**
     * Registry that records the emitter of every session.
     */
    private static final class RecordingRegistry extends SseEmitterRegistry {

        private final List<RecordingEmitter> created = new CopyOnWriteArrayList<>();

        @Override
        SseEmitter createEmitter() {
            RecordingEmitter emitter = new RecordingEmitter();
            created.add(emitter);
            return emitter;
        }
    }

    /**
     * Emitter that counts the delivered events.
     */
    private static final class RecordingEmitter extends SseEmitter {

        private final AtomicInteger sent = new AtomicInteger();

        RecordingEmitter() {
            super(0L);
        }

        int sentCount() {
            return sent.get();
        }

        @Override
        public void send(SseEmitter.SseEventBuilder builder) throws IOException {
            sent.incrementAndGet();
            super.send(builder);
        }
    }
}
