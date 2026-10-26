package dev.hata.scheduler.auth.application;

import java.time.Duration;

import dev.hata.scheduler.auth.domain.OtpPurpose;

/**
 * Holds the single-use OTP code of one purpose (design D7). The
 * implementation keeps the code in Redis, so the slot is shared by all pods.
 */
public interface OtpStorage {

    /**
     * Generates the code, replaces any previous code in the purpose slot,
     * and applies the configured TTL.
     */
    String save(String phone, OtpPurpose purpose);

    /**
     * Atomically consumes the code only on a match. A wrong guess leaves the
     * slot untouched, and a matching code can never satisfy a second call.
     */
    boolean consume(String phone, OtpPurpose purpose, String candidate);

    /**
     * The time-to-live of one code slot.
     */
    Duration ttl();
}
