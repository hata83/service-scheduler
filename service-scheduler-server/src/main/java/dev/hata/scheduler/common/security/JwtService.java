package dev.hata.scheduler.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;

import dev.hata.scheduler.common.provider.AccountStateProvider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Issues, reads, and revokes HS256 JWT tokens with embedded caller identity claims.
 * Session revocation is stored in Redis (distributed, survives restarts) when
 * available; falls back to an in-memory map for unit tests.
 * Account state and privileges are verified server-side via {@link AccountStateProvider}.
 */
@Service
public class JwtService {

    private static final String SESSION_PREFIX = "session:";
    private static final String USER_ACTIVE_PREFIX = "user:active:";
    private static final String DEACTIVATED_FLAG = "false";

    private final SecretKey key;
    private final Duration tokenTtl;
    private final String issuer;
    private final String jwtCookieName;
    private final boolean secureCookie;
    private final StringRedisTemplate redis; // nullable in tests
    private final AccountStateProvider accountStateProvider; // nullable in unit tests

    // In-memory fallback when Redis is unavailable (unit tests only)
    private final Map<String, Instant> inMemorySessions = new ConcurrentHashMap<>();

    public JwtService(JwtProperties properties) {
        this(properties, null, null);
    }

    public JwtService(JwtProperties properties, StringRedisTemplate redis) {
        this(properties, redis, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtService(
            JwtProperties properties,
            StringRedisTemplate redis,
            @org.springframework.context.annotation.Lazy AccountStateProvider accountStateProvider) {
        this.tokenTtl = properties.ttl();
        this.issuer = properties.issuer();
        this.jwtCookieName = properties.cookieName();
        this.secureCookie = properties.cookieSecure();
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.redis = redis;
        this.accountStateProvider = accountStateProvider;
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
        storeSession(jti, now.plus(tokenTtl));
        return new Issued(token, jti, now.plus(tokenTtl));
    }

    /**
     * Revokes the given token identifier. Uses Redis (distributed) when available,
     * otherwise falls back to the in-memory map (unit tests).
     */
    public void revoke(String jti) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        if (redis != null) {
            redis.delete(SESSION_PREFIX + jti);
        } else {
            inMemorySessions.remove(jti);
        }
    }

    /**
     * Marks a user as deactivated so that existing sessions are rejected on
     * the next {@link #authenticate} call, even before the 8-hour TTL expires.
     */
    public void deactivateUser(UUID userId) {
        if (userId == null) {
            return;
        }
        if (redis != null) {
            redis.opsForValue().set(USER_ACTIVE_PREFIX + userId, DEACTIVATED_FLAG, tokenTtl);
        }
    }

    /**
     * Reactivates an account or clears cached user flags when state changes.
     */
    public void activateUser(UUID userId) {
        invalidateUserState(userId);
    }

    /**
     * Invalidates cached user state in Redis when privileges or roles change.
     */
    public void invalidateUserState(UUID userId) {
        if (userId != null && redis != null) {
            redis.delete(USER_ACTIVE_PREFIX + userId);
        }
    }

    private boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        if (redis != null) {
            try {
                Boolean exists = redis.hasKey(SESSION_PREFIX + jti);
                return !Boolean.TRUE.equals(exists);
            } catch (Exception ex) {
                throw new RedisUnavailableException("Redis unavailable for session validation", ex);
            }
        }
        Instant exp = inMemorySessions.get(jti);
        if (exp == null) {
            return true; // not in map → revoked/unknown
        }
        if (!exp.isAfter(Instant.now())) {
            inMemorySessions.remove(jti);
            return true;
        }
        return false;
    }

    private boolean validateAccountState(UUID userId, String claimedRole, UUID claimedDealershipId) {
        if (userId == null) {
            return false;
        }
        if (redis != null) {
            try {
                String activeFlag = redis.opsForValue().get(USER_ACTIVE_PREFIX + userId);
                if (DEACTIVATED_FLAG.equals(activeFlag)) {
                    return false;
                }
            } catch (Exception ex) {
                throw new RedisUnavailableException("Redis unavailable for account state validation", ex);
            }
        }
        if (accountStateProvider == null) {
            return true;
        }

        Optional<AccountStateProvider.AccountState> stateOpt = accountStateProvider.getAccountState(userId);
        if (stateOpt.isEmpty()) {
            return false;
        }
        AccountStateProvider.AccountState state = stateOpt.get();
        if (!state.active()) {
            return false;
        }
        boolean roleMatches = state.role() != null && state.role().equals(claimedRole);
        boolean dealerMatches = Objects.equals(state.dealershipId(), claimedDealershipId);
        return roleMatches && dealerMatches;
    }

    private void storeSession(String jti, Instant expiresAt) {
        if (redis != null) {
            redis.opsForValue().set(SESSION_PREFIX + jti, "1", tokenTtl);
        } else {
            inMemorySessions.put(jti, expiresAt);
        }
    }

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

    public Optional<AuthPrincipal> authenticate(String token) {
        return read(token).flatMap(claims -> {
            try {
                String jti = claims.getId();
                if (isRevoked(jti)) {
                    return Optional.empty();
                }
                UUID userId = UUID.fromString(claims.getSubject());
                String role = claims.get("role", String.class);
                String phone = claims.get("ph", String.class);
                if (role == null || role.isBlank() || phone == null || phone.isBlank()) {
                    return Optional.empty();
                }
                String did = claims.get("did", String.class);
                UUID dealershipId = (did != null && !did.isBlank()) ? UUID.fromString(did) : null;

                if (!validateAccountState(userId, role, dealershipId)) {
                    return Optional.empty();
                }

                String email = claims.get("email", String.class);
                String firstName = claims.get("fn", String.class);
                String lastName = claims.get("ln", String.class);
                return Optional.of(
                        new AuthPrincipal(userId, role, phone, dealershipId, email, firstName, lastName, jti));
            } catch (IllegalArgumentException | NullPointerException ex) {
                return Optional.empty();
            }
        });
    }

    public record Issued(String token, String jti, Instant expiresAt) {}
}
