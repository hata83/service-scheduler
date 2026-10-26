package dev.hata.scheduler.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.auth.dto.IdentityResponse;
import dev.hata.scheduler.auth.dto.OtpSendResponse;
import dev.hata.scheduler.auth.dto.ProfilePatchRequest;
import dev.hata.scheduler.auth.dto.RegisterRequest;
import dev.hata.scheduler.auth.model.User;
import dev.hata.scheduler.auth.model.UserType;
import dev.hata.scheduler.auth.repository.UserRepository;
import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.otp.OtpService;
import dev.hata.scheduler.common.otp.OtpType;
import dev.hata.scheduler.common.provider.EmployeeProfileProvider;
import dev.hata.scheduler.common.provider.UserProfileProvider;
import dev.hata.scheduler.common.security.AuthPrincipal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Covers the stateless auth use cases (design D4/D5/D7/D11/D13). Every branch
 * of the service is verified for 100% line coverage.
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

    @Mock
    private OtpService otp;

    @Mock
    private UserRepository users;

    @Mock
    private UserProfileProvider profiles;

    @Mock
    private EmployeeProfileProvider employees;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(otp, users, profiles, employees);
    }

    private User customer() {
        User user = new User();
        user.assign(CUSTOMER_ID, PHONE, UserType.CUSTOMER);
        return user;
    }

    private User technician() {
        User user = new User();
        user.assign(TECH_ID, "+84900000003", UserType.EMPLOYEE);
        return user;
    }

    private User manager() {
        User user = new User();
        user.assign(MANAGER_ID, "+84900000002", UserType.EMPLOYEE);
        return user;
    }

    private User admin() {
        User user = new User();
        user.assign(ADMIN_ID, "+84900000001", UserType.EMPLOYEE);
        return user;
    }

    private UserProfileProvider.ProfileView profile() {
        return new UserProfileProvider.ProfileView(CUSTOMER_ID, "My", "Nguyen", "en_US");
    }

    private EmployeeProfileProvider.EmployeeView managerEmployee() {
        return new EmployeeProfileProvider.EmployeeView(
                MANAGER_ID, "MGR-0001", "MANAGER", DEALERSHIP, null, null, List.of());
    }

    private EmployeeProfileProvider.EmployeeView technicianEmployee() {
        return new EmployeeProfileProvider.EmployeeView(
                TECH_ID,
                "TEC-0001",
                "TECHNICIAN",
                DEALERSHIP,
                "SENIOR",
                true,
                List.of(new EmployeeProfileProvider.SkillView("ENGINE", "SENIOR")));
    }

    @Test
    void sendOtpIssuesACodeAndRespectsEchoSetting() {
        when(otp.generateAndSend(PHONE, OtpType.LOGIN)).thenReturn("483920");
        when(otp.isEchoEnabled()).thenReturn(true);

        OtpSendResponse result = service.sendOtp(PHONE, "LOGIN");

        assertThat(result.phone()).isEqualTo(PHONE);
        assertThat(result.type()).isEqualTo("LOGIN");
        assertThat(result.otpCode()).isEqualTo("483920");

        when(otp.isEchoEnabled()).thenReturn(false);
        OtpSendResponse withoutEcho = service.sendOtp(PHONE, "LOGIN");
        assertThat(withoutEcho.otpCode()).isNull();
    }

    @Test
    void sendOtpRejectsAPhoneThatIsNotE164OrUnknownType() {
        assertThatThrownBy(() -> service.sendOtp("8490000000", "LOGIN"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("E.164");
        assertThatThrownBy(() -> service.sendOtp(PHONE, "UNKNOWN")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyOtpResolvesPrincipalForCustomerManagerTechnicianAndHandlesMissingEmployee() {
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));
        when(otp.verify(PHONE, OtpType.LOGIN, "483920")).thenReturn(true);

        AuthPrincipal customerPrincipal = service.verifyOtp(PHONE, "483920");
        assertThat(customerPrincipal.role()).isEqualTo("CUSTOMER");
        assertThat(customerPrincipal.dealershipId()).isNull();
        assertThat(customerPrincipal.firstName()).isEqualTo("My");

        when(users.findByPhone("+84900000002")).thenReturn(Optional.of(manager()));
        when(employees.findByUserId(MANAGER_ID)).thenReturn(Optional.of(managerEmployee()));
        when(profiles.findByUserId(MANAGER_ID)).thenReturn(Optional.empty());
        when(otp.verify("+84900000002", OtpType.LOGIN, "111111")).thenReturn(true);

        AuthPrincipal managerPrincipal = service.verifyOtp("+84900000002", "111111");
        assertThat(managerPrincipal.role()).isEqualTo("MANAGER");
        assertThat(managerPrincipal.dealershipId()).isEqualTo(DEALERSHIP);
        assertThat(managerPrincipal.firstName()).isNull();

        when(users.findByPhone("+84900000003")).thenReturn(Optional.of(technician()));
        when(employees.findByUserId(TECH_ID)).thenReturn(Optional.of(technicianEmployee()));
        when(otp.verify("+84900000003", OtpType.LOGIN, "222222")).thenReturn(true);

        AuthPrincipal techPrincipal = service.verifyOtp("+84900000003", "222222");
        assertThat(techPrincipal.role()).isEqualTo("TECHNICIAN");
        assertThat(techPrincipal.dealershipId()).isEqualTo(DEALERSHIP);

        when(users.findByPhone("+84900000001")).thenReturn(Optional.of(admin()));
        when(employees.findByUserId(ADMIN_ID)).thenReturn(Optional.empty());
        when(otp.verify("+84900000001", OtpType.LOGIN, "333333")).thenReturn(true);

        assertThatThrownBy(() -> service.verifyOtp("+84900000001", "333333"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("employee record");
    }

    @Test
    void verifyOtpRejectsInvalidPhoneUnregisteredPhoneAndWrongCode() {
        assertThatThrownBy(() -> service.verifyOtp(null, "483920")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.verifyOtp("8490000000", "483920")).isInstanceOf(AuthException.class);

        when(users.findByPhone(PHONE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verifyOtp(PHONE, "483920"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Register");

        when(users.findByPhone(PHONE)).thenReturn(Optional.of(customer()));
        when(otp.verify(PHONE, OtpType.LOGIN, "000000")).thenReturn(false);
        assertThatThrownBy(() -> service.verifyOtp(PHONE, "000000"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("wrong");

        User inactive = customer();
        inactive.setActive(false);
        when(users.findByPhone(PHONE)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.verifyOtp(PHONE, "483920"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("deactivated");
    }

    @Test
    void registerCreatesTheCustomerAndReturnsPrincipal() {
        when(users.existsByPhone("+84900000999")).thenReturn(false);
        when(otp.verify("+84900000999", OtpType.REGISTER, "176482")).thenReturn(true);

        RegisterRequest req = new RegisterRequest("+84900000999", "Tai", "Le", "176482", "");
        AuthPrincipal result = service.register(req);

        assertThat(result.role()).isEqualTo("CUSTOMER");
        assertThat(result.phone()).isEqualTo("+84900000999");
        assertThat(result.dealershipId()).isNull();
        assertThat(result.firstName()).isEqualTo("Tai");
        assertThat(result.lastName()).isEqualTo("Le");
        verify(users).save(any(User.class));
        verify(profiles).createProfile(any(UUID.class), eq("Tai"), eq("Le"));
    }

    @Test
    void registerRejectsInvalidRequests() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("84900000999", "Tai", "Le", "176482", null)))
                .isInstanceOf(AuthException.class);

        assertThatThrownBy(() -> service.register(new RegisterRequest("+84900000999", "Tai", "Le", "176482", "ADMIN")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("CUSTOMER");

        assertThatThrownBy(() -> service.register(new RegisterRequest("+84900000999", null, "Le", "176482", null)))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.register(new RegisterRequest("+84900000999", " ", "Le", "176482", null)))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.register(new RegisterRequest("+84900000999", "Tai", null, "176482", null)))
                .isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> service.register(new RegisterRequest("+84900000999", "Tai", " ", "176482", null)))
                .isInstanceOf(AuthException.class);

        when(users.existsByPhone(PHONE)).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest(PHONE, "Tai", "Le", "176482", null)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already belongs");

        when(users.existsByPhone("+84900000999")).thenReturn(false);
        when(otp.verify("+84900000999", OtpType.REGISTER, "000000")).thenReturn(false);
        assertThatThrownBy(() -> service.register(new RegisterRequest("+84900000999", "Tai", "Le", "000000", null)))
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
                .thenReturn(Optional.of(new UserProfileProvider.ProfileView(TECH_ID, "Demo", "Technician", "en_US")));

        IdentityResponse techIdentity =
                service.me(new AuthPrincipal(TECH_ID, "TECHNICIAN", "+84900000003", DEALERSHIP, "jti-2"));
        assertThat(techIdentity.role()).isEqualTo("TECHNICIAN");
        assertThat(techIdentity.dealershipId()).isEqualTo(DEALERSHIP.toString());
        assertThat(techIdentity.firstName()).isEqualTo("Demo");

        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.empty());
        IdentityResponse withoutProfile = service.me(
                new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "emb@demo.vn", "EmbFirst", "EmbLast", "jti-1"));
        assertThat(withoutProfile.firstName()).isEqualTo("EmbFirst");
        assertThat(withoutProfile.email()).isEqualTo("emb@demo.vn");

        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.me(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1")))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void patchMeUpdatesCentralizedProfileNamesAndEmailAndValidatesInputs() {
        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile()));

        ProfilePatchRequest patch = new ProfilePatchRequest("My", "Nguyen", "a@b.c", null);
        IdentityResponse updated =
                service.patchMe(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), patch);

        assertThat(updated.email()).isEqualTo("a@b.c");
        verify(users).save(any(User.class));
        verify(profiles).updateNames(eq(CUSTOMER_ID), eq("My"), eq("Nguyen"));

        ProfilePatchRequest blankPatch = new ProfilePatchRequest("   ", "", null, null);
        service.patchMe(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), blankPatch);

        when(users.findById(ADMIN_ID)).thenReturn(Optional.of(admin()));
        when(profiles.findByUserId(ADMIN_ID))
                .thenReturn(Optional.of(new UserProfileProvider.ProfileView(ADMIN_ID, "Demo", "Admin", "en_US")));

        ProfilePatchRequest adminPatch = new ProfilePatchRequest("Chief", null, null, null);
        IdentityResponse adminUpdated =
                service.patchMe(new AuthPrincipal(ADMIN_ID, "ADMIN", "+84900000001", null, "jti-admin"), adminPatch);
        assertThat(adminUpdated.role()).isEqualTo("ADMIN");

        ProfilePatchRequest emailOnly = new ProfilePatchRequest(null, null, "admin@demo.vn", null);
        service.patchMe(new AuthPrincipal(ADMIN_ID, "ADMIN", "+84900000001", null, "jti-admin"), emailOnly);

        assertThatThrownBy(() -> service.patchMe(
                        new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"),
                        new ProfilePatchRequest(null, null, null, "+84900000888")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("never changes");

        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(
                        () -> service.patchMe(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), patch))
                .isInstanceOf(AuthException.class);

        when(users.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer()));
        when(profiles.findByUserId(CUSTOMER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(
                        () -> service.patchMe(new AuthPrincipal(CUSTOMER_ID, "CUSTOMER", PHONE, null, "jti-1"), patch))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("no profile");
    }
}
