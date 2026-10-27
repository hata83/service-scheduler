package dev.hata.scheduler.common.otp;

import java.security.SecureRandom;
import java.time.Duration;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Service for OTP generation, persistence, and verification.
 * Publishes {@link OtpGeneratedEvent} to trigger asynchronous dispatch
 * across delivery channels without coupling to specific notification providers.
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpStorage storage;
    private final ApplicationEventPublisher eventPublisher;
    private final OtpProperties properties;

    public OtpService(OtpStorage storage, ApplicationEventPublisher eventPublisher, OtpProperties properties) {
        this.storage = storage;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Generates a 6-digit code, saves it to storage with configured TTL,
     * and publishes an application event for channel dispatch.
     *
     * @return the generated code
     */
    public String generateAndSend(String phone, OtpType type) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Duration ttl = properties.ttl();
        storage.save(phone, type, code, ttl);
        eventPublisher.publishEvent(new OtpGeneratedEvent(phone, code, type, ttl));
        return code;
    }

    /**
     * Atomically consumes and verifies the submitted code.
     */
    public boolean verify(String phone, OtpType type, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return storage.consume(phone, type, code.trim());
    }

    /**
     * Returns whether OTP codes should be echoed in responses (non-prod only).
     */
    public boolean isEchoEnabled() {
        return properties.echo();
    }
}
