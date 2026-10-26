package dev.hata.scheduler.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.auth.model.User;
import dev.hata.scheduler.auth.repository.UserRepository;
import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.idempotency.IdempotencyService;
import dev.hata.scheduler.common.idempotency.IdempotencyStore;
import dev.hata.scheduler.common.otp.OtpService;
import dev.hata.scheduler.common.otp.OtpType;
import dev.hata.scheduler.common.port.CustomerProfileProvider;
import dev.hata.scheduler.common.ratelimit.RateLimitService;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.common.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/**
 * Covers the auth use cases (design D5/D7/D11). Every branch of the service
 * needs a test to ensure full line coverage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final UUID DEALERSHIP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String PHONE = "+84900000004";

    @Mock
    private OtpService otp;

    @Mock
    private JwtService jwt;

    @Mock
    private UserRepository users;

    @Mock
    private CustomerProfileProvider profiles;

    @Mock
    private IdempotencyService idempotency;

    @Mock
    private RateLimitService rateLimits;

    @Mock
    private HttpServletRequest request;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(jwt.cookieName()).thenReturn("jwt");
        when(jwt.ttl()).thenReturn(Duration.ofHours(8));
        when(rateLimits.clientIp(request)).thenReturn("10.0.0.1");
        when(idempotency.slot(anyString(), anyString(), anyString()))
                .thenAnswer(inv ->
                        "idempotency:" + inv.getArgument(0) + ":" + inv.getArgument(1) + ":" + inv.getArgument(2));

        service = new AuthService(otp, jwt, users, profiles, idempotency, rateLimits);
    }

    private User customer() {
        User user = new User();
        user.assign(CUSTOMER_ID, PHONE, "CUSTOMER", null);
        return user;
    }

    private User admin() {
        User user = new User();
        user.assign(ADMIN_ID, "+84900000001", "ADMIN", null);
        return user;
    }

    private CustomerProfileProvider.ProfileView profile() {
        return new CustomerProfileProvider.ProfileView(CUSTOMER_ID, null, "My", "Nguyen", "en_US");
    }

    @Test
    void sendOtpIssuesACodeAndDispatchesIt() {
        when(otp.generateAndSend(PHONE, OtpType.LOGIN)).thenReturn("483920");
        when(otp.isEchoEnabled()).thenReturn(true);

        AuthService.Result result = service.sendOtp(PHONE, "LOGIN", "key-1", request);

        assertThat(result.status()).isEqualTo(202);
        assertThat(result.body()).containsEntry("otpCode", "483920").containsEntry("phone", PHONE);
        verify(otp).generateAndSend(PHONE, OtpType.LOGIN);
        verify(idempotency).store(eq("idempotency:otp-send:" + PHONE + ":key-1"), any());
    }

    @Test
    void sendOtpReplaysTheCachedResponse() {
        IdempotencyStore.CachedResponse cached =
                new IdempotencyStore.CachedResponse(202, "{\"phone\":\"+84900000004\"}", null);
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));

        AuthService.Result result = service.sendOtp(PHONE, "LOGIN", "key-1", request);

        assertThat(result.status()).isEqualTo(202);
        verify(otp, never()).generateAndSend(anyString(), any());
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
    void sendOtpEnforcesRateLimits() {
        doThrow(new AuthException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", "Too many OTP requests"))
                .when(rateLimits)
                .checkOtpSend(eq(PHONE), eq("login"), anyString(), any());

        assertThatThrownBy(() -> service.sendOtp(PHONE, "LOGIN", "key-1", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many OTP requests");
    }

    @Test
    void verifyOtpLogsInAnExistingAccount() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(customer()));
        when(otp.verify(PHONE, OtpType.LOGIN, "483920")).thenReturn(true);
        when(jwt.issue(anyString(), anyString(), anyString(), any()))
                .thenReturn(
                        new JwtService.Issued("token", "jti-1", Instant.now().plusSeconds(3600)));

        AuthService.Result result = service.verifyOtp(PHONE, "483920", "key-2", request);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).containsEntry("role", "CUSTOMER");
        assertThat(result.setCookie()).contains("jwt=token").contains("HttpOnly");
    }

    @Test
    void verifyOtpLogsInAnAccountWithDealership() {
        User manager = new User();
        manager.assign(UUID.randomUUID(), "+84900000002", "MANAGER", DEALERSHIP);
        when(users.findByPhone("+84900000002")).thenReturn(Optional.of(manager));
        when(otp.verify("+84900000002", OtpType.LOGIN, "111111")).thenReturn(true);
        when(jwt.issue(anyString(), anyString(), anyString(), any()))
                .thenReturn(new JwtService.Issued(
                        "token", "jti-manager", Instant.now().plusSeconds(3600)));

        AuthService.Result result = service.verifyOtp("+84900000002", "111111", "key-manager", request);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).containsEntry("role", "MANAGER");
        assertThat(result.body()).containsEntry("dealershipId", DEALERSHIP.toString());
    }

    @Test
    void patchMeKeepsExistingNamesWhenUpdatedNamesAreBlank() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        Map<String, Object> updated = service.patchMe(
                new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"),
                "   ",
                "",
                null,
                false,
                "key-blank-names");

        assertThat(updated).isNotNull();
        verify(profiles).updateNames(eq(CUSTOMER_ID), eq("My"), eq("Nguyen"));
    }

    @Test
    void meIncludesDealershipWhenPresent() {
        User manager = new User();
        manager.assign(UUID.randomUUID(), "+84900000002", "MANAGER", DEALERSHIP);
        when(users.findById(manager.getId())).thenReturn(Optional.of(manager));

        Map<String, Object> identity =
                service.me(new AuthPrincipal(manager.getId(), "MANAGER", "+84900000002", DEALERSHIP, "jti-2"));

        assertThat(identity).containsEntry("dealershipId", DEALERSHIP.toString());
    }

    @Test
    void verifyOtpReplaysTheCachedResponse() {
        IdempotencyStore.CachedResponse cached =
                new IdempotencyStore.CachedResponse(200, "{\"role\":\"CUSTOMER\"}", "jwt=token; Path=/");
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));

        AuthService.Result result = service.verifyOtp(PHONE, "483920", "key-2", request);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.setCookie()).isEqualTo("jwt=token; Path=/");
        verify(otp, never()).verify(anyString(), any(), anyString());
    }

    @Test
    void verifyOtpRejectsAPhoneThatIsNotE164() {
        assertThatThrownBy(() -> service.verifyOtp("8490000000", "483920", "key-2", request))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void verifyOtpEnforcesRateLimits() {
        doThrow(new AuthException(
                        HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", "Too many verification requests"))
                .when(rateLimits)
                .checkOtpVerify(anyString(), any());

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
        when(otp.verify(PHONE, OtpType.LOGIN, "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "000000", "key-2", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("wrong");
    }

    @Test
    void registerCreatesTheCustomerAndTheSession() {
        when(users.existsByPhone("+84900000999")).thenReturn(false);
        when(otp.verify("+84900000999", OtpType.REGISTER, "176482")).thenReturn(true);
        when(jwt.issue(anyString(), anyString(), anyString(), any()))
                .thenReturn(
                        new JwtService.Issued("token", "jti-2", Instant.now().plusSeconds(3600)));

        AuthService.Result result = service.register("+84900000999", "Tai", "Le", "176482", false, "key-3", request);

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body()).containsEntry("role", "CUSTOMER").containsEntry("phone", "+84900000999");
        verify(users).save(any(User.class));
        verify(profiles).createProfile(any(UUID.class), eq(null), eq("Tai"), eq("Le"));
    }

    @Test
    void registerReplaysTheCachedResponse() {
        IdempotencyStore.CachedResponse cached =
                new IdempotencyStore.CachedResponse(201, "{\"role\":\"CUSTOMER\"}", "jwt=token; Path=/");
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));

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
    void registerEnforcesRateLimits() {
        doThrow(new AuthException(
                        HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", "Too many registration requests"))
                .when(rateLimits)
                .checkRegister(anyString());

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
        when(otp.verify("+84900000999", OtpType.REGISTER, "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.register("+84900000999", "Tai", "Le", "000000", false, "key-3", request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("wrong");
    }

    @Test
    void meReturnsTheIdentity() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        Map<String, Object> identity = service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"));

        assertThat(identity).containsEntry("role", "CUSTOMER").containsEntry("phone", PHONE);
        assertThat(identity).containsEntry("firstName", "My").containsEntry("lastName", "Nguyen");
        assertThat(identity.get("dealershipId")).isNull();
    }

    @Test
    void meOmitsTheNamesWithoutAProfile() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.empty());

        Map<String, Object> identity = service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"));

        assertThat(identity).doesNotContainKey("firstName");
    }

    @Test
    void meRejectsASessionWithoutAnAccount() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1")))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void patchMeUpdatesTheEmailAndTheNames() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        Map<String, Object> updated = service.patchMe(
                new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"),
                "My",
                "Nguyen",
                "a@b.c",
                false,
                "key-4");

        assertThat(updated).containsEntry("email", "a@b.c");
        verify(users).save(any(User.class));
        verify(profiles).updateNames(eq(CUSTOMER_ID), eq("My"), eq("Nguyen"));
    }

    @Test
    void patchMeRejectsAPhoneField() {
        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"),
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
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"),
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
                        new AuthPrincipal(ADMIN_ID, "ADMIN", "+84900000001", null, "jti-3"),
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
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"),
                        "My",
                        null,
                        null,
                        false,
                        "key-4"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("profile");
    }

    @Test
    void logoutExecutesWithoutError() {
        service.logout(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"));
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

        IdempotencyStore.CachedResponse cached = result.toCached();
        AuthService.Result restored = AuthService.Result.fromCached(cached);

        assertThat(cached.status()).isEqualTo(201);
        assertThat(restored.status()).isEqualTo(201);
        assertThat(restored.body()).containsEntry("role", "CUSTOMER");
        assertThat(restored.setCookie()).isEqualTo("jwt=token; Path=/");
    }

    @Test
    void resultFallsBackWhenTheCachedBodyIsBroken() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(200, "not-json", null);

        AuthService.Result restored = AuthService.Result.fromCached(cached);

        assertThat(restored.body()).isEmpty();
        assertThat(restored.setCookie()).isNull();

        java.util.Map<String, Object> cyclic = new java.util.HashMap<>();
        cyclic.put("self", cyclic);
        AuthService.Result unmarshallable = new AuthService.Result(200, cyclic, null);
        assertThat(unmarshallable.toCached().body()).isEqualTo("{}");
    }
}
