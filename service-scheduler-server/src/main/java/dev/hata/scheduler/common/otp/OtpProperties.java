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
        // Safety: never echo OTP codes when using a real (non-stub) channel
        if (echo && !"zalo-stub".equals(channel)) {
            throw new IllegalStateException("otp.echo must be false when channel is not zalo-stub (would leak codes)");
        }
    }
}
