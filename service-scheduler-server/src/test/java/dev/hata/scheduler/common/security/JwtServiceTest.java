package dev.hata.scheduler.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.provider.AccountStateProvider;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Covers the stateless JWT token issuance, Redis-backed revocation,
 * and account state revalidation (ADR-005 / design D5).
 */
class JwtServiceTest {

    private JwtService service() {
        return new JwtService(new JwtProperties(
                "secret-secret-secret-secret-secret-secret-1234",
                "service-scheduler",
                Duration.ofHours(8),
                "jwt",
                false));
    }

    @Test
    void issueAndReadRoundTripTheClaims() {
        JwtService service = service();

        JwtService.Issued issued = service.issue(
                "00000000-0000-0000-0000-000000000005",
                "CUSTOMER",
                "+84900000004",
                "00000000-0000-0000-0000-000000000001");

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.jti()).isNotBlank();
        Claims claims = service.read(issued.token()).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("00000000-0000-0000-0000-000000000005");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get("ph", String.class)).isEqualTo("+84900000004");
        assertThat(claims.get("did", String.class)).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(claims.getIssuer()).isEqualTo("service-scheduler");
    }

    @Test
    void authenticateReturnsPrincipalInStatelessMemory() {
        JwtService service = service();
        String userIdStr = "00000000-0000-0000-0000-000000000005";
        String dealerIdStr = "00000000-0000-0000-0000-000000000001";

        // With dealership id
        JwtService.Issued issuedWithDealer = service.issue(userIdStr, "MANAGER", "+84900000002", dealerIdStr);
        Optional<AuthPrincipal> principalOpt = service.authenticate(issuedWithDealer.token());
        assertThat(principalOpt).isPresent();
        assertThat(principalOpt.get().dealershipId()).isEqualTo(UUID.fromString(dealerIdStr));

        // With null dealership id
        JwtService.Issued issuedNullDealer = service.issue(userIdStr, "CUSTOMER", "+84900000004", null);
        Optional<AuthPrincipal> principalNullDealer = service.authenticate(issuedNullDealer.token());
        assertThat(principalNullDealer).isPresent();
        assertThat(principalNullDealer.get().dealershipId()).isNull();

        // With blank dealership id
        JwtService.Issued issuedBlankDealer = service.issue(userIdStr, "CUSTOMER", "+84900000004", "   ");
        Optional<AuthPrincipal> principalBlankDealer = service.authenticate(issuedBlankDealer.token());
        assertThat(principalBlankDealer).isPresent();
        assertThat(principalBlankDealer.get().dealershipId()).isNull();

        // Issue from AuthPrincipal with and without dealershipId
        AuthPrincipal fullPrincipal = new AuthPrincipal(
                UUID.fromString(userIdStr),
                "MANAGER",
                "+84900000002",
                UUID.fromString(dealerIdStr),
                "mgr@demo.vn",
                "Demo",
                "Manager",
                "jti-custom");
        JwtService.Issued issuedPrincipal = service.issue(fullPrincipal);
        Optional<AuthPrincipal> parsedFull = service.authenticate(issuedPrincipal.token());
        assertThat(parsedFull).isPresent();
        assertThat(parsedFull.get().email()).isEqualTo("mgr@demo.vn");
        assertThat(parsedFull.get().firstName()).isEqualTo("Demo");
        assertThat(parsedFull.get().lastName()).isEqualTo("Manager");

        AuthPrincipal nullDealerPrincipal =
                new AuthPrincipal(UUID.fromString(userIdStr), "CUSTOMER", "+84900000004", null, null);
        assertThat(service.issue(nullDealerPrincipal).token()).isNotBlank();
    }

    @Test
    void authenticateRejectsInvalidSubjectUuid() {
        JwtService service = service();
        JwtService.Issued invalidSub = service.issue("not-a-uuid", "CUSTOMER", "+84900000004", null);
        assertThat(service.authenticate(invalidSub.token())).isEmpty();
    }

    @Test
    void getterPropertiesReportConfiguredState() {
        JwtService service = service();
        assertThat(service.cookieName()).isEqualTo("jwt");
        assertThat(service.cookieSecure()).isFalse();
        assertThat(service.ttl()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void theTokenUsesHs256() {
        JwtService service = service();

        String token = service.issue(
                        "00000000-0000-0000-0000-000000000005",
                        "CUSTOMER",
                        "+84900000004",
                        "00000000-0000-0000-0000-000000000001")
                .token();

        assertThat(token.split("\\.")[0]).isEqualTo("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    void readRejectsEmptyOrBlankTokens() {
        JwtService service = service();
        assertThat(service.read(null)).isEmpty();
        assertThat(service.read("")).isEmpty();
        assertThat(service.read("   ")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
        assertThat(service.authenticate("   ")).isEmpty();
    }

    @Test
    void readRejectsATamperedToken() {
        JwtService service = service();

        String token = service.issue(
                        "00000000-0000-0000-0000-000000000005",
                        "CUSTOMER",
                        "+84900000004",
                        "00000000-0000-0000-0000-000000000001")
                .token();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(service.read(tampered)).isEmpty();
        assertThat(service.authenticate(tampered)).isEmpty();
    }

    @Test
    void readRejectsAGarbageToken() {
        assertThat(service().read("not-a-token")).isEmpty();
        assertThat(service().authenticate("not-a-token")).isEmpty();
    }

    @Test
    void revokeRemovesAValidToken() {
        JwtService service = service();
        JwtService.Issued issued =
                service.issue("00000000-0000-0000-0000-000000000005", "CUSTOMER", "+84900000004", null);
        assertThat(service.authenticate(issued.token())).isPresent();

        service.revoke(issued.jti());
        assertThat(service.authenticate(issued.token())).isEmpty();
    }

    @Test
    void accountStateProviderRevalidatesRoleAndActiveStatus() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        AccountStateProvider provider = mock(AccountStateProvider.class);
        when(provider.getAccountState(userId))
                .thenReturn(Optional.of(new AccountStateProvider.AccountState(true, "CUSTOMER", null)));

        JwtService service = new JwtService(
                new JwtProperties(
                        "secret-secret-secret-secret-secret-secret-1234",
                        "service-scheduler",
                        Duration.ofHours(8),
                        "jwt",
                        false),
                null,
                provider);

        JwtService.Issued issued = service.issue(userId.toString(), "CUSTOMER", "+84900000004", null);
        assertThat(service.authenticate(issued.token())).isPresent();

        // Stale token claiming ADMIN role rejected
        JwtService.Issued elevated = service.issue(userId.toString(), "ADMIN", "+84900000004", null);
        assertThat(service.authenticate(elevated.token())).isEmpty();

        // Deactivated user rejected
        when(provider.getAccountState(userId))
                .thenReturn(Optional.of(new AccountStateProvider.AccountState(false, "CUSTOMER", null)));
        assertThat(service.authenticate(issued.token())).isEmpty();

        // Missing user rejected
        when(provider.getAccountState(userId)).thenReturn(Optional.empty());
        assertThat(service.authenticate(issued.token())).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisBackedSessionRevocationAndStateRevalidationWork() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        AccountStateProvider provider = mock(AccountStateProvider.class);
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID dealerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        JwtService service = new JwtService(
                new JwtProperties(
                        "secret-secret-secret-secret-secret-secret-1234",
                        "service-scheduler",
                        Duration.ofHours(8),
                        "jwt",
                        true),
                redis,
                provider);

        // Issue writes session to Redis
        JwtService.Issued issued = service.issue(userId.toString(), "MANAGER", "+84900000002", dealerId.toString());
        verify(ops).set(eq("session:" + issued.jti()), eq("1"), eq(Duration.ofHours(8)));

        // Revoke deletes session key from Redis
        service.revoke(issued.jti());
        verify(redis).delete("session:" + issued.jti());

        // Deactivate user sets user:active in Redis
        service.deactivateUser(userId);
        verify(ops).set("user:active:" + userId, "false", Duration.ofHours(8));

        // Activate and invalidateUserState delete user:active key
        service.activateUser(userId);
        verify(redis).delete("user:active:" + userId);

        service.invalidateUserState(userId);
        verify(redis, org.mockito.Mockito.times(2)).delete("user:active:" + userId);

        // Authenticate with active Redis session and valid provider state
        when(redis.hasKey("session:" + issued.jti())).thenReturn(true);
        when(ops.get("user:active:" + userId)).thenReturn(null);
        when(provider.getAccountState(userId))
                .thenReturn(Optional.of(new AccountStateProvider.AccountState(true, "MANAGER", dealerId)));
        Optional<AuthPrincipal> authenticated = service.authenticate(issued.token());
        assertThat(authenticated).isPresent();

        // Authenticate when provider returns deactivated state
        when(provider.getAccountState(userId))
                .thenReturn(Optional.of(new AccountStateProvider.AccountState(false, "MANAGER", dealerId)));
        assertThat(service.authenticate(issued.token())).isEmpty();

        // Authenticate when provider returns role mismatch
        when(provider.getAccountState(userId))
                .thenReturn(Optional.of(new AccountStateProvider.AccountState(true, "ADMIN", dealerId)));
        assertThat(service.authenticate(issued.token())).isEmpty();

        // Authenticate when provider returns dealership mismatch
        when(provider.getAccountState(userId))
                .thenReturn(Optional.of(new AccountStateProvider.AccountState(
                        true, "MANAGER", UUID.fromString("00000000-0000-0000-0000-000000000099"))));
        assertThat(service.authenticate(issued.token())).isEmpty();

        // Authenticate with user:active: = false in Redis
        when(ops.get("user:active:" + userId)).thenReturn("false");
        assertThat(service.authenticate(issued.token())).isEmpty();

        // Authenticate with revoked session (hasKey returns false)
        when(ops.get("user:active:" + userId)).thenReturn(null);
        when(redis.hasKey("session:" + issued.jti())).thenReturn(false);
        assertThat(service.authenticate(issued.token())).isEmpty();
    }

    @Test
    void revokeIgnoresBlankJti() {
        JwtService service = service();
        service.revoke(null);
        service.revoke("");
        service.revoke("   ");
        service.deactivateUser(null);
        service.invalidateUserState(null);
    }

    @Test
    void jwtPropertiesValidatesSecretLength() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(null, "issuer", Duration.ofHours(8), "jwt", true));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties("   ", "issuer", Duration.ofHours(8), "jwt", true));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties("too-short-secret", "issuer", Duration.ofHours(8), "jwt", true));
    }
}
