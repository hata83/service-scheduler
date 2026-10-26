package dev.hata.scheduler.auth.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

/**
 * Issues and reads the HS256 session token (ADR-005 / design D5). The claims
 * carry sub, role, did, ph and jti. The token lifetime comes from the
 * {@code app.auth.jwt.ttl} property and defaults to 8 hours.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtService(AuthProperties properties) {
        this.ttl = properties.jwt().ttl();
        this.issuer = properties.jwt().issuer();
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Signs a session token for the caller.
     *
     * @param subject user id
     * @param role role name
     * @param phone E.164 phone number
     * @param dealershipId dealership UUID, or null when the caller has none
     */
    public Issued issue(String subject, String role, String phone, String dealershipId) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .id(jti)
                .claim("role", role)
                .claim("ph", phone)
                .claim("did", dealershipId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new Issued(token, jti, now.plus(ttl));
    }

    /**
     * Reads the claims of a signed token. The result is empty when the token fails.
     */
    public Optional<Claims> read(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * A signed token with its session id and expiry.
     */
    public record Issued(String token, String jti, Instant expiresAt) {}
}
