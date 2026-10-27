package dev.hata.scheduler.common.otp;

import java.time.Duration;
import java.util.Optional;

/**
 * Storage SPI for managing short-lived OTP verification codes.
 * Supports tenant-scoped slots to isolate keys across multi-tenant boundaries.
 */
public interface OtpStorage {

    String DEFAULT_TENANT = "global";

    /**
     * Persists an OTP code under the default tenant.
     */
    default void save(String phone, OtpType type, String code, Duration ttl) {
        save(DEFAULT_TENANT, phone, type, code, ttl);
    }

    /**
     * Persists an OTP code for a tenant, phone, and type with an expiry.
     */
    void save(String tenant, String phone, OtpType type, String code, Duration ttl);

    /**
     * Atomically consumes a code under the default tenant if valid.
     */
    default boolean consume(String phone, OtpType type, String code) {
        return consume(DEFAULT_TENANT, phone, type, code);
    }

    /**
     * Atomically consumes a code for a tenant if valid, deleting it on match.
     */
    boolean consume(String tenant, String phone, OtpType type, String code);

    /**
     * Reads the current code under the default tenant if present.
     */
    default Optional<String> get(String phone, OtpType type) {
        return get(DEFAULT_TENANT, phone, type);
    }

    /**
     * Reads the current code for a tenant, phone, and type if present.
     */
    Optional<String> get(String tenant, String phone, OtpType type);
}
