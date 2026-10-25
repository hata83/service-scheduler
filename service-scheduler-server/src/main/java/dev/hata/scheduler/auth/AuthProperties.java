package dev.hata.scheduler.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth configuration (design D3/D5/D7). The {@code dev} default profile and
 * {@code test} profile bind echo {@code true}; prod binds {@code echo: false}.
 * {@code jwt.secret} stays prod-mandatory through the prod profile's
 * unresolved-placeholder fail fast.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Jwt jwt, Otp otp) {

    public record Jwt(String secret, String issuer, Duration ttl, String cookieName) {}

    public record Otp(Duration ttl, boolean echo) {}
}
