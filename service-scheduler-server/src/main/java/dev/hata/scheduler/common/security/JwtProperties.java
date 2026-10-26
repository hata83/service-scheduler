package dev.hata.scheduler.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT issuance and verification.
 */
@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl, String cookieName) {

    public JwtProperties {
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
