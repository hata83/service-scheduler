package dev.hata.scheduler.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT issuance and verification.
 */
@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl, String cookieName, boolean cookieSecure) {

    private static final int MIN_SECRET_LENGTH = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("app.auth.jwt.secret must be configured with at least 32 characters");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(8);
        }
        if (cookieName == null || cookieName.isBlank()) {
            cookieName = "jwt";
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "service-scheduler";
        }
    }
}
