package dev.hata.scheduler.common.security;

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
import org.springframework.stereotype.Service;

/**
 * Issues and reads the stateless HS256 JWT tokens.
 * Cryptographic validation runs in memory without contacting Redis session stores.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration tokenTtl;
    private final String issuer;
    private final String jwtCookieName;

    public JwtService(JwtProperties properties) {
        this.tokenTtl = properties.ttl();
        this.issuer = properties.issuer();
        this.jwtCookieName = properties.cookieName();
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String cookieName() {
        return jwtCookieName;
    }

    public Duration ttl() {
        return tokenTtl;
    }

    /**
     * Signs a stateless token for the caller.
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
                .expiration(Date.from(now.plus(tokenTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new Issued(token, jti, now.plus(tokenTtl));
    }

    /**
     * Reads and cryptographically verifies claims of a signed token.
     */
    public Optional<Claims> read(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
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
     * Validates a token and extracts the authenticated caller principal in memory.
     */
    public Optional<AuthPrincipal> authenticate(String token) {
        return read(token).flatMap(claims -> {
            try {
                UUID userId = UUID.fromString(claims.getSubject());
                String role = claims.get("role", String.class);
                String phone = claims.get("ph", String.class);
                String did = claims.get("did", String.class);
                UUID dealershipId = (did != null && !did.isBlank()) ? UUID.fromString(did) : null;
                String jti = claims.getId();
                return Optional.of(new AuthPrincipal(userId, role, phone, dealershipId, jti));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        });
    }

    public record Issued(String token, String jti, Instant expiresAt) {}
}
