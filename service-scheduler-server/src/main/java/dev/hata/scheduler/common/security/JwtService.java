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
 * Issues and reads stateless HS256 JWT tokens with embedded caller identity claims.
 * Cryptographic validation runs in memory without contacting Redis session stores.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration tokenTtl;
    private final String issuer;
    private final String jwtCookieName;
    private final boolean secureCookie;

    public JwtService(JwtProperties properties) {
        this.tokenTtl = properties.ttl();
        this.issuer = properties.issuer();
        this.jwtCookieName = properties.cookieName();
        this.secureCookie = properties.cookieSecure();
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String cookieName() {
        return jwtCookieName;
    }

    public boolean cookieSecure() {
        return secureCookie;
    }

    public Duration ttl() {
        return tokenTtl;
    }

    /**
     * Signs a stateless token embedding the full caller principal claims.
     */
    public Issued issue(AuthPrincipal principal) {
        return issueToken(
                principal.userId().toString(),
                principal.role(),
                principal.phone(),
                principal.dealershipId() != null ? principal.dealershipId().toString() : null,
                principal.email(),
                principal.firstName(),
                principal.lastName(),
                principal.jti());
    }

    /**
     * Signs a stateless token for the given basic identity fields.
     */
    public Issued issue(String subject, String role, String phone, String dealershipId) {
        return issueToken(subject, role, phone, dealershipId, null, null, null, null);
    }

    private Issued issueToken(
            String subject,
            String role,
            String phone,
            String dealershipId,
            String email,
            String firstName,
            String lastName,
            String jtiOverride) {
        Instant now = Instant.now();
        String jti = jtiOverride != null ? jtiOverride : UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .id(jti)
                .claim("role", role)
                .claim("ph", phone)
                .claim("did", dealershipId)
                .claim("email", email)
                .claim("fn", firstName)
                .claim("ln", lastName)
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
                String email = claims.get("email", String.class);
                String firstName = claims.get("fn", String.class);
                String lastName = claims.get("ln", String.class);
                String jti = claims.getId();
                return Optional.of(
                        new AuthPrincipal(userId, role, phone, dealershipId, email, firstName, lastName, jti));
            } catch (IllegalArgumentException ex) {
                return Optional.empty();
            }
        });
    }

    public record Issued(String token, String jti, Instant expiresAt) {}
}
