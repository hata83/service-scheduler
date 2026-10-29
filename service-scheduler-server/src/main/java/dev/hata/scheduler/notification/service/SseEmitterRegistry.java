package dev.hata.scheduler.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

    private final Map<String, UUID> staffDealerships = new ConcurrentHashMap<>();

    private final Set<String> globalStaffPhones = ConcurrentHashMap.newKeySet();

    /**
     * Registers an SSE emitter for the given phone number, evicting any existing emitter.
     */
    public SseEmitter register(String phone) {
        return register(phone, null, false);
    }

    /**
     * Registers a staff SSE emitter for the given phone number and dealership scope.
     * A null dealership scope marks a global ADMIN session that receives every dealership event.
     */
    public SseEmitter registerStaff(String phone, UUID dealershipId) {
        return register(phone, dealershipId, true);
    }

    private SseEmitter register(String phone, UUID dealershipId, boolean staff) {
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

        if (staff) {
            if (dealershipId == null) {
                globalStaffPhones.add(phone);
                staffDealerships.remove(phone);
            } else {
                globalStaffPhones.remove(phone);
                staffDealerships.put(phone, dealershipId);
            }
        } else {
            globalStaffPhones.remove(phone);
            staffDealerships.remove(phone);
        }

        Consumer<String> cleanup = reason -> evict(phone, emitter, reason);

        emitter.onCompletion(() -> cleanup.accept("completed"));
        emitter.onTimeout(() -> cleanup.accept("timed out"));
        emitter.onError(e -> {
            cleanup.accept("error");
            log.debug("SSE emitter error details for phone: {}", maskPhone(phone), e);
        });

        log.info(
                "Registered SSE emitter for phone: {}. Active count: {}. Staff scope: {}",
                maskPhone(phone),
                emitters.size(),
                staff ? (dealershipId == null ? "GLOBAL" : dealershipId.toString()) : "none");
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
            evict(phone, emitter, "send failed");
            return false;
        }
    }

    /**
     * Dispatches a named event with payload data to every staff session of one dealership.
     * Global ADMIN sessions receive the event for every dealership.
     *
     * @return the delivery result per staff phone number
     */
    public Map<String, Boolean> sendToDealershipStaff(UUID dealershipId, String eventName, Object data) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        for (String phone : globalStaffPhones) {
            results.put(phone, send(phone, eventName, data));
        }
        for (Map.Entry<String, UUID> entry : staffDealerships.entrySet()) {
            if (!entry.getValue().equals(dealershipId)) {
                continue;
            }
            results.put(entry.getKey(), send(entry.getKey(), eventName, data));
        }
        return results;
    }

    /**
     * Returns the staff session count of one dealership. Global ADMIN sessions count for all.
     */
    public int getStaffSessionCount(UUID dealershipId) {
        int scoped = (int) staffDealerships.entrySet().stream()
                .filter(entry -> entry.getValue().equals(dealershipId))
                .count();
        return scoped + globalStaffPhones.size();
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
                evict(phone, emitter, "heartbeat failed");
            }
        }
    }

    /**
     * Removes an emitter and its staff routing metadata when the emitter is still the active one
     * of the phone. A replaced emitter never clears the scope of a newer session.
     * The method is the single eviction path for send failures, heartbeat failures, and
     * emitter callbacks. It stays visible to the package so that the registry tests can
     * simulate a late callback of a replaced emitter.
     */
    void evict(String phone, SseEmitter emitter, String reason) {
        if (emitters.remove(phone, emitter)) {
            globalStaffPhones.remove(phone);
            staffDealerships.remove(phone);
            log.debug("Evicted SSE emitter {} for phone: {}", reason, maskPhone(phone));
        }
    }

    /**
     * Removes an emitter for the specified phone number.
     */
    public void remove(String phone) {
        if (phone == null) {
            return;
        }
        staffDealerships.remove(phone);
        globalStaffPhones.remove(phone);
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
