package dev.hata.scheduler.auth.application;

import java.time.Duration;

/**
 * Holds the revocable session records (ADR-005). The implementation keeps the
 * records in Redis, so a logout revokes the session for every pod.
 */
public interface SessionStore {

    /**
     * Stores the session record.
     */
    void save(String jti, String subject, Duration ttl);

    /**
     * Reports whether the session record still exists.
     */
    boolean exists(String jti);

    /**
     * Deletes the session record. Logout calls it.
     */
    void revoke(String jti);
}
