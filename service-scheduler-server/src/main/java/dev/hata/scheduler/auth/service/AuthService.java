package dev.hata.scheduler.auth.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.hata.scheduler.auth.dto.AuthResponse;
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

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The auth use cases: OTP issuance, login, registration, and centralized profile
 * management (design D4/D5/D7/D11/D13). Methods accept input DTOs and value types,
 * returning domain DTO responses without servlet coupling.
 */
@Service
public class AuthService {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OtpService otp;
    private final JwtService jwt;
    private final UserRepository users;
    private final CustomerProfileProvider profiles;
    private final TechnicianProfileProvider employees;
    private final IdempotencyService idempotency;
    private final RateLimitService rateLimits;

    public AuthService(
            OtpService otp,
            JwtService jwt,
            UserRepository users,
            CustomerProfileProvider profiles,
            TechnicianProfileProvider employees,
            IdempotencyService idempotency,
            RateLimitService rateLimits) {
        this.otp = otp;
        this.jwt = jwt;
        this.users = users;
        this.profiles = profiles;
        this.employees = employees;
        this.idempotency = idempotency;
        this.rateLimits = rateLimits;
    }

    /**
     * Issues a purpose-scoped OTP and publishes an event for channel dispatch (design D7).
     * A replayed idempotency key returns the cached response and dispatches nothing.
     */
    public OtpSendResponse sendOtp(String phone, String typeWire, String clientIp, String idempotencyKey) {
        String slot = idempotency.slot("otp-send", phone, idempotencyKey);
        Optional<IdempotencyStore.CachedResponse> cached = idempotency.load(slot);
        if (cached.isPresent()) {
            try {
                return MAPPER.readValue(cached.get().body(), OtpSendResponse.class);
            } catch (Exception ignored) {
                // fallback to re-evaluating on deserialization failure
            }
        }
        requirePhone(phone);
        OtpType type = OtpType.fromWire(typeWire);
        UUID dealershipId = findDealershipId(phone);
        rateLimits.checkOtpSend(phone, type.value(), clientIp, dealershipId);

        String code = otp.generateAndSend(phone, type);
        OtpSendResponse response = new OtpSendResponse(phone, type.name(), otp.isEchoEnabled() ? code : null);
        try {
            idempotency.store(
                    slot, new IdempotencyStore.CachedResponse(202, MAPPER.writeValueAsString(response), null));
        } catch (Exception ignored) {
            // cache store failure does not fail business execution
        }
        return response;
    }

    /**
     * Logs in an account by verified OTP. Verify never creates a user row (spec: login by verified OTP).
     * When {@code users.type} is {@code CUSTOMER}, role is {@code CUSTOMER} and dealershipId is null;
     * when {@code users.type} is {@code EMPLOYEE}, role and dealershipId resolve from {@code employees}.
     */
    @Transactional
    public AuthSession verifyOtp(String phone, String code, String clientIp, String idempotencyKey) {
        String slot = idempotency.slot("otp-verify", phone, idempotencyKey);
        Optional<IdempotencyStore.CachedResponse> cached = idempotency.load(slot);
        if (cached.isPresent()) {
            return readCachedSession(cached.get());
        }
        requirePhone(phone);
        UUID dealershipId = findDealershipId(phone);
        rateLimits.checkOtpVerify(clientIp, dealershipId);

        Optional<User> found = users.findByPhone(phone);
        if (found.isEmpty()) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "Account not found",
                    "No account exists for this phone. Register first at POST /api/v1/auth/register.");
        }
        User user = found.get();

        if (!otp.verify(phone, OtpType.LOGIN, code)) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "OTP verification failed",
                    "The code is wrong, expired, or was issued for another purpose.");
        }

        ResolvedScope scope = resolveScope(user);
        AuthSession session = issueSession(user, scope.role(), scope.dealershipId());
        cacheSession(slot, 200, session);
        return session;
    }

    private ResolvedScope resolveScope(User user) {
        if (User.TYPE_EMPLOYEE.equals(user.getType())) {
            TechnicianProfileProvider.EmployeeView emp = employees
                    .findByUserId(user.getId())
                    .orElseThrow(() -> new AuthException(
                            HttpStatus.UNAUTHORIZED,
                            "Employee profile missing",
                            "The employee account has no employee record."));
            return new ResolvedScope(emp.role(), emp.dealershipId());
        }
        return new ResolvedScope(User.TYPE_CUSTOMER, null);
    }

    /**
     * Creates the global customer account ({@code type = CUSTOMER}), centralized profile,
     * and login session in one call (design D11).
     */
    @Transactional
    public AuthSession register(RegisterRequest req, String clientIp, String idempotencyKey) {
        String slot = idempotency.slot("register", req.phone(), idempotencyKey);
        Optional<IdempotencyStore.CachedResponse> cached = idempotency.load(slot);
        if (cached.isPresent()) {
            return readCachedSession(cached.get());
        }
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
        rateLimits.checkRegister(clientIp);

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
        User user = newUser(userId, req.phone(), User.TYPE_CUSTOMER);
        users.save(user);
        profiles.createProfile(userId, req.firstName().trim(), req.lastName().trim());

        AuthSession session = issueSession(user, User.TYPE_CUSTOMER, null);
        cacheSession(slot, 201, session);
        return session;
    }

    /**
     * Returns the caller identity and centralized profile names (spec: session introspection).
     */
    @Transactional(readOnly = true)
    public IdentityResponse me(AuthPrincipal principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() ->
                        new AuthException(HttpStatus.UNAUTHORIZED, "Session invalid", "The session has no account."));
        return buildIdentityResponse(user, principal.role(), principal.dealershipId());
    }

    /**
     * Applies the editable profile fields via the centralized {@code profiles} table
     * for all accounts ({@code CUSTOMER} and {@code EMPLOYEE}).
     */
    @Transactional
    public IdentityResponse patchMe(AuthPrincipal principal, ProfilePatchRequest patch, String idempotencyKey) {
        String slot = idempotency.slot("patch-me", principal.phone(), idempotencyKey);
        Optional<IdempotencyStore.CachedResponse> cached = idempotency.load(slot);
        if (cached.isPresent()) {
            try {
                return MAPPER.readValue(cached.get().body(), IdentityResponse.class);
            } catch (Exception ignored) {
                // fallback to re-evaluating on deserialization failure
            }
        }
        if (patch.phone() != null) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Phone is not editable",
                    "The phone is the login identity and never changes.");
        }
        User user = users.findById(principal.userId())
                .orElseThrow(() ->
                        new AuthException(HttpStatus.UNAUTHORIZED, "Session invalid", "The session has no account."));
        if (patch.email() != null) {
            user.setEmail(patch.email().trim());
            users.save(user);
        }
        if (patch.firstName() != null || patch.lastName() != null) {
            CustomerProfileProvider.ProfileView current = profiles.findByUserId(user.getId())
                    .orElseThrow(() ->
                            new AuthException(HttpStatus.NOT_FOUND, "Profile missing", "The account has no profile."));
            String newFirst = resolveName(patch.firstName(), current.firstName());
            String newLast = resolveName(patch.lastName(), current.lastName());
            profiles.updateNames(user.getId(), newFirst, newLast);
        }
        IdentityResponse response = buildIdentityResponse(user, principal.role(), principal.dealershipId());
        try {
            idempotency.store(
                    slot, new IdempotencyStore.CachedResponse(200, MAPPER.writeValueAsString(response), null));
        } catch (Exception ignored) {
            // cache store failure does not fail business execution
        }
        return response;
    }

    private static String resolveName(String candidate, String fallback) {
        return candidate != null && !candidate.isBlank() ? candidate.trim() : fallback;
    }

    private IdentityResponse buildIdentityResponse(User user, String sessionRole, UUID sessionDealershipId) {
        CustomerProfileProvider.ProfileView profile =
                profiles.findByUserId(user.getId()).orElse(null);
        return new IdentityResponse(
                user.getId().toString(),
                sessionRole,
                user.getPhone(),
                sessionDealershipId != null ? sessionDealershipId.toString() : null,
                user.getEmail(),
                profile != null ? profile.firstName() : null,
                profile != null ? profile.lastName() : null);
    }

    private AuthSession readCachedSession(IdempotencyStore.CachedResponse cached) {
        try {
            AuthResponse authResponse = MAPPER.readValue(cached.body(), AuthResponse.class);
            JwtService.Issued issued = new JwtService.Issued(cached.setCookie(), null, null);
            return new AuthSession(authResponse, issued);
        } catch (Exception ex) {
            throw new AuthException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Idempotency replay failed",
                    "Idempotency replay failed: " + ex.getMessage(),
                    ex);
        }
    }

    private void cacheSession(String slot, int status, AuthSession session) {
        try {
            String bodyJson = MAPPER.writeValueAsString(session.user());
            idempotency.store(
                    slot,
                    new IdempotencyStore.CachedResponse(
                            status, bodyJson, session.session().token()));
        } catch (Exception ignored) {
            // cache store failure does not fail business execution
        }
    }

    private AuthSession issueSession(User user, String role, UUID activeDealershipId) {
        String dealershipIdStr = activeDealershipId != null ? activeDealershipId.toString() : null;
        JwtService.Issued issued = jwt.issue(user.getId().toString(), role, user.getPhone(), dealershipIdStr);
        AuthResponse authResponse = new AuthResponse(user.getId(), role, user.getPhone(), activeDealershipId);
        return new AuthSession(authResponse, issued);
    }

    private UUID findDealershipId(String phone) {
        return users.findByPhone(phone)
                .flatMap(user -> employees.findByUserId(user.getId()))
                .map(TechnicianProfileProvider.EmployeeView::dealershipId)
                .orElse(null);
    }

    private static void requirePhone(String phone) {
        if (phone == null || !E164.matcher(phone).matches()) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid phone number",
                    "The phone must be in E.164 format, for example +84900000004.");
        }
    }

    private User newUser(UUID id, String phone, String type) {
        User user = new User();
        user.assign(id, phone, type);
        return user;
    }

    private record ResolvedScope(String role, UUID dealershipId) {}
}
