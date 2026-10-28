package dev.hata.scheduler.notification.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registry for active Server-Sent Event (SSE) emitters scoped by user phone number.
 * Enforces a single active connection per phone number and issues 25-second heartbeats.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final long DEFAULT_TIMEOUT_MS = 0L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Registers an SSE emitter for the given phone number, evicting any existing emitter.
     */
    public SseEmitter register(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone must not be blank");
        }
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        SseEmitter previous = emitters.put(phone, emitter);
        if (previous != null) {
            try {
                previous.complete();
            } catch (Exception e) {
                log.debug("Error completing superseded emitter for phone: {}", maskPhone(phone), e);
            }
        }

        emitter.onCompletion(() -> {
            emitters.remove(phone, emitter);
            log.debug("SSE emitter completed for phone: {}", maskPhone(phone));
        });
        emitter.onTimeout(() -> {
            emitters.remove(phone, emitter);
            log.debug("SSE emitter timed out for phone: {}", maskPhone(phone));
        });
        emitter.onError(e -> {
            emitters.remove(phone, emitter);
            log.debug("SSE emitter error for phone: {}", maskPhone(phone), e);
        });

        log.info("Registered SSE emitter for phone: {}. Active count: {}", maskPhone(phone), emitters.size());
        return emitter;
    }

    /**
     * Dispatches a named event with payload data to the active emitter for the given phone.
     */
    public boolean send(String phone, String eventName, Object data) {
        if (phone == null) {
            return false;
        }
        SseEmitter emitter = emitters.get(phone);
        if (emitter == null) {
            return false;
        }

        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (Exception e) {
            log.warn(
                    "Failed to dispatch SSE event '{}' to phone {}. Removing emitter.", eventName, maskPhone(phone), e);
            emitters.remove(phone, emitter);
            return false;
        }
    }

    /**
     * Emits a comment ping to all registered clients to prevent idle connection termination.
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeats() {
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            String phone = entry.getKey();
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                log.debug("Heartbeat failed for phone {}. Evicting.", maskPhone(phone));
                emitters.remove(phone, emitter);
            }
        }
    }

    /**
     * Removes an emitter for the specified phone number.
     */
    public void remove(String phone) {
        if (phone == null) {
            return;
        }
        SseEmitter emitter = emitters.remove(phone);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("Error completing emitter on remove for phone: {}", maskPhone(phone), e);
            }
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 3) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 3);
    }

    /**
     * Returns the current number of active registered emitters.
     */
    public int getActiveCount() {
        return emitters.size();
    }
}
