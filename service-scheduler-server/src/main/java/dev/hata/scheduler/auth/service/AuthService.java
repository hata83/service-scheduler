package dev.hata.scheduler.auth.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

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

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stateless auth use cases: OTP issuance, login verification, registration, and
 * centralized profile management (design D4/D5/D7/D11/D13). Cross-cutting
 * idempotency and rate limits are handled declaratively by filters and AOP aspects.
 */
@Service
public class AuthService {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final OtpService otp;
    private final UserRepository users;
    private final UserProfileProvider profiles;
    private final EmployeeProfileProvider employees;

    public AuthService(
            OtpService otp, UserRepository users, UserProfileProvider profiles, EmployeeProfileProvider employees) {
        this.otp = otp;
        this.users = users;
        this.profiles = profiles;
        this.employees = employees;
    }

    /**
     * Issues a purpose-scoped OTP and publishes an event for channel dispatch (design D7).
     */
    public OtpSendResponse sendOtp(String phone, String typeWire) {
        requirePhone(phone);
        OtpType type = OtpType.fromWire(typeWire);
        String code = otp.generateAndSend(phone, type);
        return new OtpSendResponse(phone, type.name(), otp.isEchoEnabled() ? code : null);
    }

    /**
     * Verifies a LOGIN-purpose OTP and resolves the caller's stateless {@link AuthPrincipal}
     * to be embedded into the {@code httpOnly} JWT cookie.
     */
    @Transactional
    public AuthPrincipal verifyOtp(String phone, String code) {
        requirePhone(phone);
        Optional<User> found = users.findByPhone(phone);
        if (found.isEmpty()) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "Account not found",
                    "No account exists for this phone. Register first at POST /api/v1/auth/register.");
        }
        User user = found.get();
        requireActive(user);

        if (!otp.verify(phone, OtpType.LOGIN, code)) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "OTP verification failed",
                    "The code is wrong, expired, or was issued for another purpose.");
        }

        ResolvedScope scope = resolveScope(user);
        UserProfileProvider.ProfileView profile =
                profiles.findByUserId(user.getId()).orElse(null);
        return new AuthPrincipal(
                user.getId(),
                scope.role(),
                user.getPhone(),
                scope.dealershipId(),
                user.getEmail(),
                profile != null ? profile.firstName() : null,
                profile != null ? profile.lastName() : null,
                UUID.randomUUID().toString());
    }

    private ResolvedScope resolveScope(User user) {
        if (user.getType() == UserType.EMPLOYEE) {
            EmployeeProfileProvider.EmployeeView emp = employees
                    .findByUserId(user.getId())
                    .orElseThrow(() -> new AuthException(
                            HttpStatus.UNAUTHORIZED,
                            "Employee profile missing",
                            "The employee account has no employee record."));
            return new ResolvedScope(emp.role(), emp.dealershipId());
        }
        return new ResolvedScope(UserType.CUSTOMER.name(), null);
    }

    /**
     * Creates the global customer account ({@code type = CUSTOMER}), centralized profile,
     * and returns the caller's {@link AuthPrincipal} to be embedded in the JWT cookie.
     */
    @Transactional
    public AuthPrincipal register(RegisterRequest req) {
        requirePhone(req.phone());
        if (req.role() != null && !req.role().isBlank()) {
            throw new AuthException(
                    HttpStatus.FORBIDDEN,
                    "Role pinning",
                    "Registration always creates a CUSTOMER account and rejects a role field.");
        }
        if (req.firstName() == null
                || req.firstName().isBlank()
                || req.lastName() == null
                || req.lastName().isBlank()) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST, "Invalid request body", "The request needs a firstName and a lastName.");
        }
        if (users.existsByPhone(req.phone())) {
            throw new AuthException(
                    HttpStatus.CONFLICT,
                    "Phone already registered",
                    "This phone already belongs to an account. Use the login flow instead.");
        }
        if (!otp.verify(req.phone(), OtpType.REGISTER, req.code())) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "OTP verification failed",
                    "The code is wrong, expired, or was issued for another purpose.");
        }

        UUID userId = UUID.randomUUID();
        String firstName = req.firstName().trim();
        String lastName = req.lastName().trim();
        User user = newUser(userId, req.phone(), UserType.CUSTOMER);
        users.save(user);
        profiles.createProfile(userId, firstName, lastName);

        return new AuthPrincipal(
                userId,
                UserType.CUSTOMER.name(),
                req.phone(),
                null,
                null,
                firstName,
                lastName,
                UUID.randomUUID().toString());
    }

    /**
     * Returns the caller identity and centralized profile names (spec: session introspection).
     */
    @Transactional(readOnly = true)
    public IdentityResponse me(AuthPrincipal principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() ->
                        new AuthException(HttpStatus.UNAUTHORIZED, "Session invalid", "The session has no account."));
        requireActive(user);
        return buildIdentityResponse(user, principal);
    }

    /**
     * Applies the editable profile fields via the centralized {@code profiles} table
     * for all accounts ({@code CUSTOMER} and {@code EMPLOYEE}).
     */
    @Transactional
    public IdentityResponse patchMe(AuthPrincipal principal, ProfilePatchRequest patch) {
        if (patch.phone() != null) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Phone is not editable",
                    "The phone is the login identity and never changes.");
        }
        User user = users.findById(principal.userId())
                .orElseThrow(() ->
                        new AuthException(HttpStatus.UNAUTHORIZED, "Session invalid", "The session has no account."));
        requireActive(user);
        if (patch.email() != null) {
            user.setEmail(patch.email().trim());
            users.save(user);
        }
        if (patch.firstName() != null || patch.lastName() != null) {
            UserProfileProvider.ProfileView current = profiles.findByUserId(user.getId())
                    .orElseThrow(() ->
                            new AuthException(HttpStatus.NOT_FOUND, "Profile missing", "The account has no profile."));
            String newFirst = resolveName(patch.firstName(), current.firstName());
            String newLast = resolveName(patch.lastName(), current.lastName());
            profiles.updateNames(user.getId(), newFirst, newLast);
        }
        return buildIdentityResponse(user, principal);
    }

    private static String resolveName(String candidate, String fallback) {
        return candidate != null && !candidate.isBlank() ? candidate.trim() : fallback;
    }

    private IdentityResponse buildIdentityResponse(User user, AuthPrincipal principal) {
        UserProfileProvider.ProfileView profile =
                profiles.findByUserId(user.getId()).orElse(null);
        String firstName = profile != null ? profile.firstName() : principal.firstName();
        String lastName = profile != null ? profile.lastName() : principal.lastName();
        String email = user.getEmail() != null ? user.getEmail() : principal.email();
        return new IdentityResponse(
                user.getId().toString(),
                principal.role(),
                user.getPhone(),
                principal.dealershipId() != null ? principal.dealershipId().toString() : null,
                email,
                firstName,
                lastName);
    }

    private static void requireActive(User user) {
        if (!user.isActive()) {
            throw new AuthException(
                    HttpStatus.FORBIDDEN, "Account inactive", "This account is deactivated and cannot sign in.");
        }
    }

    private static void requirePhone(String phone) {
        if (phone == null || !E164.matcher(phone).matches()) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid phone number",
                    "The phone must be in E.164 format, for example +84900000004.");
        }
    }

    private User newUser(UUID id, String phone, UserType type) {
        User user = new User();
        user.assign(id, phone, type);
        return user;
    }

    private record ResolvedScope(String role, UUID dealershipId) {}
}
