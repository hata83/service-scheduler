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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.auth.dto.AuthSession;
import dev.hata.scheduler.auth.dto.IdentityResponse;
import dev.hata.scheduler.auth.dto.OtpSendResponse;
import dev.hata.scheduler.auth.dto.ProfilePatchRequest;
import dev.hata.scheduler.auth.dto.RegisterRequest;
import dev.hata.scheduler.auth.model.User;
import dev.hata.scheduler.auth.repository.UserRepository;
import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.idempotency.IdempotencyService;
import dev.hata.scheduler.common.idempotency.IdempotencyStore;
import dev.hata.scheduler.common.otp.OtpService;
import dev.hata.scheduler.common.otp.OtpType;
import dev.hata.scheduler.common.port.CustomerProfileProvider;
import dev.hata.scheduler.common.port.TechnicianProfileProvider;
import dev.hata.scheduler.common.ratelimit.RateLimitService;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.common.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/**
 * Covers the auth use cases (design D5/D7/D11/D13). Every branch of the service
 * is verified for full branch and line coverage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final UUID DEALERSHIP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID TECH_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String PHONE = "+84900000004";
    private static final String CLIENT_IP = "10.0.0.1";

    @Mock
    private OtpService otp;

    @Mock
    private JwtService jwt;

    @Mock
    private UserRepository users;

    @Mock
    private CustomerProfileProvider profiles;

    @Mock
    private TechnicianProfileProvider employees;

    @Mock
    private IdempotencyService idempotency;

    @Mock
    private RateLimitService rateLimits;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(jwt.cookieName()).thenReturn("jwt");
        when(jwt.ttl()).thenReturn(Duration.ofHours(8));
        when(idempotency.slot(anyString(), anyString(), anyString()))
                .thenAnswer(inv ->
                        "idempotency:" + inv.getArgument(0) + ":" + inv.getArgument(1) + ":" + inv.getArgument(2));

        service = new AuthService(otp, jwt, users, profiles, employees, idempotency, rateLimits);
    }

    private User customer() {
        User user = new User();
        user.assign(CUSTOMER_ID, PHONE, User.TYPE_CUSTOMER);
        return user;
    }

    private User technician() {
        User user = new User();
        user.assign(TECH_ID, "+84900000003", User.TYPE_EMPLOYEE);
        return user;
    }

    private User manager() {
        User user = new User();
        user.assign(MANAGER_ID, "+84900000002", User.TYPE_EMPLOYEE);
        return user;
    }

    private User admin() {
        User user = new User();
        user.assign(ADMIN_ID, "+84900000001", User.TYPE_EMPLOYEE);
        return user;
    }

    private CustomerProfileProvider.ProfileView profile() {
        return new CustomerProfileProvider.ProfileView(CUSTOMER_ID, "My", "Nguyen", "en_US");
    }

    private TechnicianProfileProvider.EmployeeView managerEmployee() {
        return new TechnicianProfileProvider.EmployeeView(
                MANAGER_ID, "MGR-0001", "MANAGER", DEALERSHIP, null, null, List.of());
    }

    private TechnicianProfileProvider.EmployeeView technicianEmployee() {
        return new TechnicianProfileProvider.EmployeeView(
                TECH_ID,
                "TEC-0001",
                "TECHNICIAN",
                DEALERSHIP,
                "SENIOR",
                true,
                List.of(new TechnicianProfileProvider.SkillView("ENGINE", "SENIOR")));
    }

    @Test
    void sendOtpIssuesACodeAndDispatchesIt() {
        when(otp.generateAndSend(PHONE, OtpType.LOGIN)).thenReturn("483920");
        when(otp.isEchoEnabled()).thenReturn(true);

        OtpSendResponse result = service.sendOtp(PHONE, "LOGIN", CLIENT_IP, "key-1");

        assertThat(result.phone()).isEqualTo(PHONE);
        assertThat(result.type()).isEqualTo("LOGIN");
        assertThat(result.otpCode()).isEqualTo("483920");
        verify(otp).generateAndSend(PHONE, OtpType.LOGIN);
        verify(idempotency).store(eq("idempotency:otp-send:" + PHONE + ":key-1"), any());
    }

    @Test
    void sendOtpOmitsEchoWhenDisabledAndResolvesEmployeeDealership() {
        when(users.findByPhone("+84900000002")).thenReturn(Optional.of(manager()));
        when(employees.findByUserId(MANAGER_ID)).thenReturn(Optional.of(managerEmployee()));
        when(otp.generateAndSend("+84900000002", OtpType.LOGIN)).thenReturn("111111");
        when(otp.isEchoEnabled()).thenReturn(false);

        OtpSendResponse mgrResult = service.sendOtp("+84900000002", "LOGIN", CLIENT_IP, "key-mgr-send");
        assertThat(mgrResult.otpCode()).isNull();
        verify(rateLimits).checkOtpSend("+84900000002", "login", CLIENT_IP, DEALERSHIP);
    }

    @Test
    void sendOtpReplaysTheCachedResponse() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(
                202, "{\"phone\":\"+84900000004\",\"type\":\"LOGIN\",\"otpCode\":\"111111\"}", null);
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));

        OtpSendResponse result = service.sendOtp(PHONE, "LOGIN", CLIENT_IP, "key-1");

        assertThat(result.phone()).isEqualTo(PHONE);
        assertThat(result.otpCode()).isEqualTo("111111");
        verify(otp, never()).generateAndSend(anyString(), any());
    }

    @Test
    void sendOtpRejectsAPhoneThatIsNotE164() {
        assertThatThrownBy(() -> service.sendOtp("8490000000", "LOGIN", CLIENT_IP, "key-1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    void sendOtpRejectsAnUnknownType() {
        assertThatThrownBy(() -> service.sendOtp(PHONE, "UNKNOWN", CLIENT_IP, "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendOtpEnforcesRateLimits() {
        doThrow(new AuthException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", "Too many OTP requests"))
                .when(rateLimits)
                .checkOtpSend(eq(PHONE), eq("login"), anyString(), any());

        assertThatThrownBy(() -> service.sendOtp(PHONE, "LOGIN", CLIENT_IP, "key-1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many OTP requests");
    }

    @Test
    void sendOtpFallsBackWhenCachedResponseIsCorrupt() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(200, "not-json", null);
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));
        when(otp.generateAndSend(PHONE, OtpType.LOGIN)).thenReturn("483920");
        when(otp.isEchoEnabled()).thenReturn(true);

        OtpSendResponse response = service.sendOtp(PHONE, "LOGIN", CLIENT_IP, "key-corrupt");

        assertThat(response.otpCode()).isEqualTo("483920");
    }

    @Test
    void sendOtpSucceedsEvenIfIdempotencyStoreFails() {
        when(otp.generateAndSend(PHONE, OtpType.LOGIN)).thenReturn("483920");
        when(otp.isEchoEnabled()).thenReturn(true);
        doThrow(new RuntimeException("redis down")).when(idempotency).store(anyString(), any());

        OtpSendResponse response = service.sendOtp(PHONE, "LOGIN", CLIENT_IP, "key-fail");

        assertThat(response.otpCode()).isEqualTo("483920");
    }

    @Test
    void verifyOtpLogsInCustomerManagerTechnicianAndAdminWithResolvedDealershipScopes() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(customer()));
        when(otp.verify(PHONE, OtpType.LOGIN, "483920")).thenReturn(true);
        when(jwt.issue(anyString(), anyString(), anyString(), any()))
                .thenReturn(
                        new JwtService.Issued("token", "jti-1", Instant.now().plusSeconds(3600)));

        AuthSession customerResult = service.verifyOtp(PHONE, "483920", CLIENT_IP, "key-cust");
        assertThat(customerResult.user().role()).isEqualTo("CUSTOMER");
        assertThat(customerResult.user().dealershipId()).isNull();

        when(users.findByPhone("+84900000002")).thenReturn(Optional.of(manager()));
        when(employees.findByUserId(MANAGER_ID)).thenReturn(Optional.of(managerEmployee()));
        when(otp.verify("+84900000002", OtpType.LOGIN, "111111")).thenReturn(true);

        AuthSession managerResult = service.verifyOtp("+84900000002", "111111", CLIENT_IP, "key-mgr");
        assertThat(managerResult.user().role()).isEqualTo("MANAGER");
        assertThat(managerResult.user().dealershipId()).isEqualTo(DEALERSHIP);

        when(users.findByPhone("+84900000003")).thenReturn(Optional.of(technician()));
        when(employees.findByUserId(TECH_ID)).thenReturn(Optional.of(technicianEmployee()));
        when(otp.verify("+84900000003", OtpType.LOGIN, "222222")).thenReturn(true);

        AuthSession techResult = service.verifyOtp("+84900000003", "222222", CLIENT_IP, "key-tech");
        assertThat(techResult.user().role()).isEqualTo("TECHNICIAN");
        assertThat(techResult.user().dealershipId()).isEqualTo(DEALERSHIP);

        when(users.findByPhone("+84900000001")).thenReturn(Optional.of(admin()));
        when(employees.findByUserId(ADMIN_ID)).thenReturn(Optional.empty());
        when(otp.verify("+84900000001", OtpType.LOGIN, "333333")).thenReturn(true);

        assertThatThrownBy(() -> service.verifyOtp("+84900000001", "333333", CLIENT_IP, "key-admin-missing-emp"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("employee record");
    }

    @Test
    void verifyOtpReplaysTheCachedResponse() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(
                200,
                "{\"id\":\"" + CUSTOMER_ID
                        + "\",\"role\":\"CUSTOMER\",\"phone\":\"+84900000004\",\"dealershipId\":null}",
                "cached-jwt");
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));

        AuthSession result = service.verifyOtp(PHONE, "483920", CLIENT_IP, "key-2");

        assertThat(result.user().role()).isEqualTo("CUSTOMER");
        assertThat(result.session().token()).isEqualTo("cached-jwt");
        verify(otp, never()).verify(anyString(), any(), anyString());
    }

    @Test
    void verifyOtpThrowsWhenCachedResponseIsCorrupt() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(200, "not-json", "cached-jwt");
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "483920", CLIENT_IP, "key-2"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Idempotency replay failed");
    }

    @Test
    void verifyOtpSucceedsEvenIfIdempotencyStoreFails() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(customer()));
        when(otp.verify(PHONE, OtpType.LOGIN, "483920")).thenReturn(true);
        when(jwt.issue(CUSTOMER_ID.toString(), "CUSTOMER", PHONE, null))
                .thenReturn(new JwtService.Issued("tok", "jti-1", Instant.now().plusSeconds(3600)));
        doThrow(new RuntimeException("redis down")).when(idempotency).store(anyString(), any());

        AuthSession session = service.verifyOtp(PHONE, "483920", CLIENT_IP, "key-store-fail");

        assertThat(session.user().phone()).isEqualTo(PHONE);
    }

    @Test
    void verifyOtpRejectsAPhoneThatIsNotE164() {
        assertThatThrownBy(() -> service.verifyOtp(null, "483920", CLIENT_IP, "key-null"))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.verifyOtp("8490000000", "483920", CLIENT_IP, "key-2"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void verifyOtpEnforcesRateLimits() {
        doThrow(new AuthException(
                        HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", "Too many verification requests"))
                .when(rateLimits)
                .checkOtpVerify(anyString(), any());

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "483920", CLIENT_IP, "key-2"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many verification requests");
    }

    @Test
    void verifyOtpNamesRegistrationForAnUnregisteredPhone() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "483920", CLIENT_IP, "key-2"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Register");
    }

    @Test
    void verifyOtpRejectsAWrongCode() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(customer()));
        when(otp.verify(PHONE, OtpType.LOGIN, "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.verifyOtp(PHONE, "000000", CLIENT_IP, "key-2"))
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

        RegisterRequest req = new RegisterRequest("+84900000999", "Tai", "Le", "176482", "");
        AuthSession result = service.register(req, CLIENT_IP, "key-3");

        assertThat(result.user().role()).isEqualTo("CUSTOMER");
        assertThat(result.user().phone()).isEqualTo("+84900000999");
        assertThat(result.user().dealershipId()).isNull();
        verify(users).save(any(User.class));
        verify(profiles).createProfile(any(UUID.class), eq("Tai"), eq("Le"));
    }

    @Test
    void registerReplaysTheCachedResponse() {
        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(
                201,
                "{\"id\":\"" + CUSTOMER_ID
                        + "\",\"role\":\"CUSTOMER\",\"phone\":\"+84900000999\",\"dealershipId\":null}",
                "cached-jwt");
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));

        RegisterRequest req = new RegisterRequest("+84900000999", "Tai", "Le", "176482", null);
        AuthSession result = service.register(req, CLIENT_IP, "key-3");

        assertThat(result.user().role()).isEqualTo("CUSTOMER");
        verify(users, never()).save(any(User.class));
    }

    @Test
    void registerRejectsAPhoneThatIsNotE164() {
        RegisterRequest req = new RegisterRequest("84900000999", "Tai", "Le", "176482", null);
        assertThatThrownBy(() -> service.register(req, CLIENT_IP, "key-3")).isInstanceOf(AuthException.class);
    }

    @Test
    void registerRejectsARoleField() {
        RegisterRequest req = new RegisterRequest("+84900000999", "Tai", "Le", "176482", "ADMIN");
        assertThatThrownBy(() -> service.register(req, CLIENT_IP, "key-3"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("CUSTOMER");
    }

    @Test
    void registerRejectsMissingNames() {
        assertThatThrownBy(() -> service.register(
                        new RegisterRequest("+84900000999", null, "Le", "176482", null), CLIENT_IP, "k1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("firstName");
        assertThatThrownBy(() -> service.register(
                        new RegisterRequest("+84900000999", " ", "Le", "176482", null), CLIENT_IP, "k2"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("firstName");
        assertThatThrownBy(() -> service.register(
                        new RegisterRequest("+84900000999", "Tai", null, "176482", null), CLIENT_IP, "k3"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("firstName");
        assertThatThrownBy(() -> service.register(
                        new RegisterRequest("+84900000999", "Tai", " ", "176482", null), CLIENT_IP, "k4"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("firstName");
    }

    @Test
    void registerEnforcesRateLimits() {
        doThrow(new AuthException(
                        HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", "Too many registration requests"))
                .when(rateLimits)
                .checkRegister(anyString());

        RegisterRequest req = new RegisterRequest("+84900000999", "Tai", "Le", "176482", null);
        assertThatThrownBy(() -> service.register(req, CLIENT_IP, "key-3"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many registration requests");
    }

    @Test
    void registerRejectsADuplicatePhone() {
        when(users.existsByPhone(PHONE)).thenReturn(true);

        RegisterRequest req = new RegisterRequest(PHONE, "Tai", "Le", "176482", null);
        assertThatThrownBy(() -> service.register(req, CLIENT_IP, "key-3"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already belongs");
    }

    @Test
    void registerRejectsAWrongCode() {
        when(users.existsByPhone("+84900000999")).thenReturn(false);
        when(otp.verify("+84900000999", OtpType.REGISTER, "000000")).thenReturn(false);

        RegisterRequest req = new RegisterRequest("+84900000999", "Tai", "Le", "000000", null);
        assertThatThrownBy(() -> service.register(req, CLIENT_IP, "key-3"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("wrong");
    }

    @Test
    void meReturnsTheCentralizedIdentityForAllRoles() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        IdentityResponse identity = service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"));
        assertThat(identity.role()).isEqualTo("CUSTOMER");
        assertThat(identity.phone()).isEqualTo(PHONE);
        assertThat(identity.firstName()).isEqualTo("My");
        assertThat(identity.lastName()).isEqualTo("Nguyen");
        assertThat(identity.dealershipId()).isNull();

        when(users.findById(TECH_ID)).thenReturn(Optional.of(technician()));
        when(profiles.findByUserId(TECH_ID))
                .thenReturn(
                        Optional.of(new CustomerProfileProvider.ProfileView(TECH_ID, "Demo", "Technician", "en_US")));

        IdentityResponse techIdentity =
                service.me(new AuthPrincipal(TECH_ID, "TECHNICIAN", "+84900000003", DEALERSHIP, "jti-2"));
        assertThat(techIdentity.role()).isEqualTo("TECHNICIAN");
        assertThat(techIdentity.dealershipId()).isEqualTo(DEALERSHIP.toString());
        assertThat(techIdentity.firstName()).isEqualTo("Demo");
    }

    @Test
    void meOmitsTheNamesWithoutAProfile() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.empty());

        IdentityResponse identity = service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"));

        assertThat(identity.firstName()).isNull();
        assertThat(identity.lastName()).isNull();
    }

    @Test
    void meRejectsASessionWithoutAnAccount() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1")))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void patchMeUpdatesCentralizedProfileNamesAndEmailForAllRoles() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        ProfilePatchRequest patch = new ProfilePatchRequest("My", "Nguyen", "a@b.c", null);
        IdentityResponse updated =
                service.patchMe(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), patch, "key-4");

        assertThat(updated.email()).isEqualTo("a@b.c");
        verify(users).save(any(User.class));
        verify(profiles).updateNames(eq(CUSTOMER_ID), eq("My"), eq("Nguyen"));

        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin()));
        when(profiles.findByUserId(ADMIN_ID))
                .thenReturn(Optional.of(new CustomerProfileProvider.ProfileView(ADMIN_ID, "Demo", "Admin", "en_US")));

        ProfilePatchRequest adminPatch = new ProfilePatchRequest("Chief", null, null, null);
        IdentityResponse adminUpdated = service.patchMe(
                new AuthPrincipal(ADMIN_ID, "ADMIN", "+84900000001", null, "jti-admin"), adminPatch, "key-admin-patch");

        assertThat(adminUpdated.role()).isEqualTo("ADMIN");
        verify(profiles).updateNames(eq(ADMIN_ID), eq("Chief"), eq("Admin"));
    }

    @Test
    void patchMeKeepsExistingNamesWhenUpdatedNamesAreBlank() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        ProfilePatchRequest patch = new ProfilePatchRequest("   ", "", null, null);
        IdentityResponse updated = service.patchMe(
                new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), patch, "key-blank-names");

        assertThat(updated).isNotNull();
        verify(profiles).updateNames(eq(CUSTOMER_ID), eq("My"), eq("Nguyen"));
    }

    @Test
    void patchMeUpdatesOnlyEmailAndHandlesIdempotency() {
        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin()));

        ProfilePatchRequest patch = new ProfilePatchRequest(null, null, "admin@demo.vn", null);
        IdentityResponse updated = service.patchMe(
                new AuthPrincipal(ADMIN_ID, "ADMIN", "+84900000001", null, "jti-admin"), patch, "key-admin-email");

        assertThat(updated.email()).isEqualTo("admin@demo.vn");

        IdempotencyStore.CachedResponse cached = new IdempotencyStore.CachedResponse(
                200,
                "{\"id\":\"" + ADMIN_ID
                        + "\",\"role\":\"ADMIN\",\"phone\":\"+84900000001\",\"email\":\"cached@demo.vn\"}",
                null);
        when(idempotency.load(anyString())).thenReturn(Optional.of(cached));
        IdentityResponse replayed = service.patchMe(
                new AuthPrincipal(ADMIN_ID, "ADMIN", "+84900000001", null, "jti-admin"), patch, "key-admin-email");
        assertThat(replayed.email()).isEqualTo("cached@demo.vn");

        when(idempotency.load(anyString()))
                .thenReturn(Optional.of(new IdempotencyStore.CachedResponse(200, "not-json", null)));
        doThrow(new RuntimeException("redis down")).when(idempotency).store(anyString(), any());
        IdentityResponse fallback = service.patchMe(
                new AuthPrincipal(ADMIN_ID, "ADMIN", "+84900000001", null, "key-corrupt"), patch, "key-corrupt");
        assertThat(fallback.email()).isEqualTo("admin@demo.vn");
    }

    @Test
    void patchMeRejectsAPhoneField() {
        ProfilePatchRequest patch = new ProfilePatchRequest(null, null, null, "+84900000888");
        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), patch, "key-4"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("never changes");
    }

    @Test
    void patchMeRejectsASessionWithoutAnAccount() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

        ProfilePatchRequest patch = new ProfilePatchRequest("My", "Nguyen", null, null);
        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), patch, "key-4"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void patchMeRejectsAMissingProfile() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.empty());

        ProfilePatchRequest patch = new ProfilePatchRequest("My", "Nguyen", null, null);
        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), patch, "key-4"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("no profile");
    }
}
