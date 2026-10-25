package dev.hata.scheduler.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.AuthException;
import dev.hata.scheduler.common.IdempotencyStorage;
import dev.hata.scheduler.common.RateLimitProperties;
import dev.hata.scheduler.common.RedisWindowCounter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Covers the auth use cases (design D5/D7/D11). Every branch of the service
 * needs a test, because the coverage gate demands full line coverage on the
 * service classes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final UUID DEALERSHIP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String PHONE = "+84900000004";

    @Mock
    private OtpStorage otp;

    @Mock
    private OtpChannel channel;

    @Mock
    private JwtService jwt;

    @Mock
    private SessionStore sessions;

    @Mock
    private UserRepository users;

    @Mock
    private CustomerProfileRepository profiles;

    @Mock
    private IdempotencyStorage idempotency;

    @Mock
    private RedisWindowCounter counter;

    @Mock
    private HttpServletRequest request;

    private AuthService service;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt(
                        "secret-secret-secret-secret-secret-secret-1234",
                        "service-scheduler",
                        Duration.ofHours(8),
                        "jwt"),
                new AuthProperties.Otp(Duration.ofMinutes(5), true, "zalo-stub"),
                new AuthProperties.Registration(DEALERSHIP.toString()));
        RateLimitProperties rateLimits = new RateLimitProperties("classpath:config/rate-limits-test.json", false);
        service = new AuthService(
                otp, channel, jwt, sessions, users, profiles, idempotency, counter, rateLimits, properties);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/test");
        when(counter.tryAcquire(anyString(), anyLong(), any())).thenReturn(true);
    }

    private User customer() {
        User user = new User();
        user.assign(CUSTOMER_ID, PHONE, "CUSTOMER", DEALERSHIP);
        return user;
    }

    private User admin() {
        User user = new User();
        user.assign(ADMIN_ID, "+84900000001", "ADMIN", DEALERSHIP);
        return user;
    }

    private CustomerProfile profile() {
        CustomerProfile profile = new CustomerProfile();
        profile.assign(CUSTOMER_ID, DEALERSHIP, "My", "Nguyen");
        return profile;
    }

    @Test
    void sendOtpIssuesACodeAndDispatchesIt() {
        when(otp.save(PHONE, OtpPurpose.LOGIN)).thenReturn("483920");

        AuthService.Result result = service.sendOtp(PHONE, "LOGIN", "key-1", request);

        assertThat(result.status()).isEqualTo(202);
        assertThat(result.body()).containsEntry("otpCode", "483920").containsEntry("phone", PHONE);
        verify(channel).send(PHONE, "483920");
        verify(idempotency).store(eq("idempotency:otp-send:" + PHONE + ":key-1"), any());
    }

    @Test
    void sendOtpReplaysTheCachedResponse() {
        IdempotencyStorage.CachedResponse cached =
                new IdempotencyStorage.CachedResponse(202, "{\"phone\":\"+84900000004\"}", null);
        when(idempotency.load(anyString())).thenReturn(cached);

        AuthService.Result result = service.sendOtp(PHONE, "LOGIN", "key-1", request);

        assertThat(result.status()).isEqualTo(202);
        verify(channel, never()).send(anyString(), anyString());
    }

    @Test
    void sendOtpRejectsAPhoneThatIsNotE164() {
        assertThatThrownBy(() -> service.sendOtp("8490000000", "LOGIN", "key-1", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    void sendOtpRejectsAnUnknownType() {
        assertThatThrownBy(() -> service.sendOtp(PHONE, "UNKNOWN", "key-1", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendOtpStopsAtThePhonePurposeLimit() {
        when(counter.tryAcquire(anyString(), eq(2L), any())).thenReturn(false);

        assertThatThrownBy(() -> service.sendOtp(PHONE, "LOGIN", "key-1", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many OTP requests");
    }

    @Test
    void sendOtpStopsAtTheIpLimit() {
        when(counter.tryAcquire(startsWith("ratelimit:otp-send:phone"), anyLong(), any()))
                .thenReturn(true);
        when(counter.tryAcquire(startsWith("ratelimit:otp-send:ip"), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.sendOtp(PHONE, "LOGIN", "key-1", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many OTP requests");
    }

    @Test
    void sendOtpTrustsTheForwardedAddressWhenTheProfileAllowsIt() {
        RateLimitProperties forwarding = new RateLimitProperties("classpath:config/rate-limits-test.json", true);
        AuthService forwardingService = new AuthService(
                otp,
                channel,
                jwt,
                sessions,
                users,
                profiles,
                idempotency,
                counter,
                forwarding,
                new AuthProperties(
                        new AuthProperties.Jwt(
                                "secret-secret-secret-secret-secret-secret-1234",
                                "service-scheduler",
                                Duration.ofHours(8),
                                "jwt"),
                        new AuthProperties.Otp(Duration.ofMinutes(5), true, "zalo-stub"),
                        new AuthProperties.Registration(DEALERSHIP.toString())));
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");

        forwardingService.sendOtp(PHONE, "LOGIN", "key-1", request);

        verify(counter).tryAcquire(eq("ratelimit:otp-send:ip:203.0.113.7"), anyLong(), any());
    }

    @Test
    void verifyOtpLogsInAnExistingAccount() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(customer()));
        when(otp.consume(PHONE, OtpPurpose.LOGIN, "483920")).thenReturn(true);
        when(jwt.issue(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(
                        new JwtService.Issued("token", "jti-1", Instant.now().plusSeconds(3600)));

        AuthService.Result result = service.verifyOtp(PHONE, "483920", "key-2", request);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).containsEntry("role", "CUSTOMER");
        assertThat(result.setCookie()).contains("jwt=token").contains("HttpOnly");
        verify(sessions).save(eq("jti-1"), anyString(), any());
    }

    @Test
    void verifyOtpReplaysTheCachedResponse() {
        IdempotencyStorage.CachedResponse cached =
                new IdempotencyStorage.CachedResponse(200, "{\"role\":\"CUSTOMER\"}", "jwt=token; Path=/");
        when(idempotency.load(anyString())).thenReturn(cached);

        AuthService.Result result = service.verifyOtp(PHONE, "483920", "key-2", request);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.setCookie()).isEqualTo("jwt=token; Path=/");
        verify(otp, never()).consume(anyString(), any(), anyString());
    }

    @Test
    void verifyOtpRejectsAPhoneThatIsNotE164() {
        assertThatThrownBy(() -> service.verifyOtp("8490000000", "483920", "key-2", request))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void verifyOtpStopsAtTheIpLimit() {
        when(counter.tryAcquire(startsWith("ratelimit:otp-verify:ip"), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "483920", "key-2", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many verification requests");
    }

    @Test
    void verifyOtpNamesRegistrationForAnUnregisteredPhone() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "483920", "key-2", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Register");
    }

    @Test
    void verifyOtpRejectsAWrongCode() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(customer()));
        when(otp.consume(PHONE, OtpPurpose.LOGIN, "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "000000", "key-2", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("wrong");
    }

    @Test
    void registerCreatesTheCustomerAndTheSession() {
        when(users.existsByPhone("+84900000999")).thenReturn(false);
        when(otp.consume("+84900000999", OtpPurpose.REGISTER, "176482")).thenReturn(true);
        when(jwt.issue(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(
                        new JwtService.Issued("token", "jti-2", Instant.now().plusSeconds(3600)));

        AuthService.Result result = service.register("+84900000999", "Tai", "Le", "176482", false, "key-3", request);

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body()).containsEntry("role", "CUSTOMER").containsEntry("phone", "+84900000999");
        verify(users).save(any(User.class));
        verify(profiles).save(any(CustomerProfile.class));
    }

    @Test
    void registerReplaysTheCachedResponse() {
        IdempotencyStorage.CachedResponse cached =
                new IdempotencyStorage.CachedResponse(201, "{\"role\":\"CUSTOMER\"}", "jwt=token; Path=/");
        when(idempotency.load(anyString())).thenReturn(cached);

        AuthService.Result result = service.register("+84900000999", "Tai", "Le", "176482", false, "key-3", request);

        assertThat(result.status()).isEqualTo(201);
        verify(users, never()).save(any(User.class));
    }

    @Test
    void registerRejectsAPhoneThatIsNotE164() {
        assertThatThrownBy(() -> service.register("84900000999", "Tai", "Le", "176482", false, "key-3", request))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void registerRejectsARoleField() {
        assertThatThrownBy(() -> service.register("+84900000999", "Tai", "Le", "176482", true, "key-3", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("CUSTOMER");
    }

    @Test
    void registerRejectsMissingNames() {
        assertThatThrownBy(() -> service.register("+84900000999", " ", null, "176482", false, "key-3", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("firstName");
    }

    @Test
    void registerStopsAtTheIpLimit() {
        when(counter.tryAcquire(startsWith("ratelimit:register:ip"), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.register("+84900000999", "Tai", "Le", "176482", false, "key-3", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many registration requests");
    }

    @Test
    void registerRejectsADuplicatePhone() {
        when(users.existsByPhone(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> service.register(PHONE, "Tai", "Le", "176482", false, "key-3", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already belongs");
    }

    @Test
    void registerRejectsAWrongCode() {
        when(users.existsByPhone("+84900000999")).thenReturn(false);
        when(otp.consume("+84900000999", OtpPurpose.REGISTER, "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.register("+84900000999", "Tai", "Le", "000000", false, "key-3", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("wrong");
    }

    @Test
    void meReturnsTheIdentity() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        Map<String, Object> identity =
                service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, DEALERSHIP, "jti-1"));

        assertThat(identity).containsEntry("role", "CUSTOMER").containsEntry("phone", PHONE);
        assertThat(identity).containsEntry("firstName", "My").containsEntry("lastName", "Nguyen");
        assertThat(identity.get("dealershipId")).isEqualTo(DEALERSHIP.toString());
    }

    @Test
    void meOmitsTheNamesWithoutAProfile() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.empty());

        Map<String, Object> identity =
                service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, DEALERSHIP, "jti-1"));

        assertThat(identity).doesNotContainKey("firstName");
    }

    @Test
    void meRejectsASessionWithoutAnAccount() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, DEALERSHIP, "jti-1")))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void patchMeUpdatesTheEmailAndTheNames() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        Map<String, Object> updated = service.patchMe(
                new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, DEALERSHIP, "jti-1"),
                "My",
                "Nguyen",
                "a@b.c",
                false,
                "key-4");

        assertThat(updated).containsEntry("email", "a@b.c");
        verify(users).save(any(User.class));
        verify(profiles).save(any(CustomerProfile.class));
    }

    @Test
    void patchMeRejectsAPhoneField() {
        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, DEALERSHIP, "jti-1"),
                        null,
                        null,
                        null,
                        true,
                        "key-4"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("never changes");
    }

    @Test
    void patchMeRejectsASessionWithoutAnAccount() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, DEALERSHIP, "jti-1"),
                        null,
                        null,
                        null,
                        false,
                        "key-4"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void patchMeRejectsNamesForAStaffRole() {
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin()));

        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(ADMIN_ID, "ADMIN", "+84900000001", DEALERSHIP, "jti-3"),
                        "Not",
                        "Editable",
                        null,
                        false,
                        "key-4"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("CUSTOMER");
    }

    @Test
    void patchMeRejectsAMissingProfile() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, DEALERSHIP, "jti-1"),
                        "My",
                        null,
                        null,
                        false,
                        "key-4"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("profile");
    }

    @Test
    void logoutRevokesTheSession() {
        service.logout(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, DEALERSHIP, "jti-1"));

        verify(sessions).revoke("jti-1");
    }

    @Test
    void cookieHelpersBuildTheHeader() {
        assertThat(service.sessionCookie("token", Duration.ofHours(8)))
                .isEqualTo("jwt=token; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800");
        assertThat(service.clearedCookie()).isEqualTo("jwt=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
    }

    @Test
    void resultRoundTripsThroughTheCache() {
        AuthService.Result result = new AuthService.Result(201, Map.of("role", "CUSTOMER"), "jwt=token; Path=/");

        IdempotencyStorage.CachedResponse cached = result.toCached();
        AuthService.Result restored = AuthService.Result.fromCached(cached);

        assertThat(cached.status()).isEqualTo(201);
        assertThat(restored.status()).isEqualTo(201);
        assertThat(restored.body()).containsEntry("role", "CUSTOMER");
        assertThat(restored.setCookie()).isEqualTo("jwt=token; Path=/");
    }

    @Test
    void resultFallsBackWhenTheCachedBodyIsBroken() {
        IdempotencyStorage.CachedResponse cached = new IdempotencyStorage.CachedResponse(200, "not-json", null);

        AuthService.Result restored = AuthService.Result.fromCached(cached);

        assertThat(restored.body()).isEmpty();
        assertThat(restored.setCookie()).isNull();
    }

    private static String startsWith(String prefix) {
        return org.mockito.ArgumentMatchers.startsWith(prefix);
    }
}
