package dev.hata.scheduler.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;
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
 * The emitter and its staff scope live in one session entry, so that the routing and the
 * emitter of a phone change together and a reconnect can not lose its routing.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);
    private static final long DEFAULT_TIMEOUT_MS = 0L;

    /**
     * One connected client. A staff session with a null dealership is a global ADMIN session
     * that receives the events of every dealership.
     */
    private record Session(String phone, SseEmitter emitter, UUID dealershipId, boolean staff) {

        boolean matchesDealership(UUID id) {
            return staff && (dealershipId == null || dealershipId.equals(id));
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

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
        SseEmitter emitter = createEmitter();
        Session previous = sessions.put(phone, new Session(phone, emitter, dealershipId, staff));
        if (previous != null) {
            try {
                previous.emitter().complete();
            } catch (Exception e) {
                log.debug("Error completing superseded emitter for phone: {}", maskPhone(phone), e);
            }
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
                sessions.size(),
                staff ? (dealershipId == null ? "GLOBAL" : dealershipId.toString()) : "none");
        return emitter;
    }

    /**
     * Creates the emitter of one session. The method stays visible to the package so that the
     * registry tests can record which session received an event.
     */
    SseEmitter createEmitter() {
        return new SseEmitter(DEFAULT_TIMEOUT_MS);
    }

    /**
     * Dispatches a named event with payload data to the active emitter for the given phone.
     */
    public boolean send(String phone, String eventName, Object data) {
        if (phone == null) {
            return false;
        }
        Session session = sessions.get(phone);
        if (session == null) {
            return false;
        }
        return send(session, eventName, data);
    }

    /**
     * Dispatches a named event with payload data through the given session. The delivery uses
     * the emitter of the session, so a session keeps the scope that matched the event.
     */
    private boolean send(Session session, String eventName, Object data) {
        try {
            session.emitter().send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (Exception e) {
            log.warn(
                    "Failed to dispatch SSE event '{}' to phone {}. Removing emitter.",
                    eventName,
                    maskPhone(session.phone()),
                    e);
            evict(session, "send failed");
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
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (!session.matchesDealership(dealershipId)) {
                continue;
            }
            results.put(session.phone(), send(session, eventName, data));
        }
        return results;
    }

    /**
     * Returns the staff session count of one dealership. Global ADMIN sessions count for all.
     */
    public int getStaffSessionCount(UUID dealershipId) {
        return (int) sessions.values().stream()
                .filter(session -> session.matchesDealership(dealershipId))
                .count();
    }

    /**
     * Emits a comment ping to all registered clients to prevent idle connection termination.
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeats() {
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            Session session = entry.getValue();
            try {
                session.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                log.debug("Heartbeat failed for phone {}. Evicting.", maskPhone(session.phone()));
                evict(session, "heartbeat failed");
            }
        }
    }

    /**
     * Removes the session of a phone when its emitter still owns the phone. The removal is one
     * atomic map operation, so a reconnect that installed a new session keeps its routing.
     * The method is the single eviction path for send failures, heartbeat failures, and
     * emitter callbacks. It stays visible to the package so that the registry tests can
     * simulate a late callback of a replaced emitter.
     */
    // The identity comparison is intentional. The eviction must target the exact emitter that
    // failed. A value comparison could match another emitter of the same phone.
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    void evict(String phone, SseEmitter emitter, String reason) {
        Session session = sessions.get(phone);
        if (session == null || session.emitter() != emitter) {
            return;
        }
        evict(session, reason);
    }

    /**
     * Removes the session when it still owns the phone. A replacement session stays.
     */
    private void evict(Session session, String reason) {
        if (sessions.remove(session.phone(), session)) {
            log.debug("Evicted SSE emitter {} for phone: {}", reason, maskPhone(session.phone()));
        }
    }

    /**
     * Removes an emitter for the specified phone number.
     */
    public void remove(String phone) {
        if (phone == null) {
            return;
        }
        Session session = sessions.remove(phone);
        if (session != null) {
            try {
                session.emitter().complete();
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
        return sessions.size();
    }
}
