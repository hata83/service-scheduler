package dev.hata.scheduler.common.otp;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for OTP issuance and verification.
 */
@ConfigurationProperties(prefix = "app.auth.otp")
public record OtpProperties(Duration ttl, boolean echo, String channel) {

    public OtpProperties {
        if (ttl == null) {
            ttl = Duration.ofMinutes(5);
        }
        if (channel == null || channel.isBlank()) {
            channel = "zalo-stub";
        }
    }
}
