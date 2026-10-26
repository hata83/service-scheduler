package dev.hata.scheduler.common.otp;

import java.time.Duration;
import java.util.Optional;

/**
 * Storage SPI for managing short-lived OTP verification codes.
 */
public interface OtpStorage {

    /**
     * Persists an OTP code for a phone and type with an expiry.
     */
    void save(String phone, OtpType type, String code, Duration ttl);

    /**
     * Atomically consumes a code if valid, deleting it on match.
     */
    boolean consume(String phone, OtpType type, String code);

    /**
     * Reads the current code for a phone and type if present.
     */
    Optional<String> get(String phone, OtpType type);
}
