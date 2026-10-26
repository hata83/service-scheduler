package dev.hata.scheduler.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.hata.scheduler.common.AuthException;
import dev.hata.scheduler.common.CustomerProfileProvider;
import dev.hata.scheduler.common.DealershipFeaturesProvider;
import dev.hata.scheduler.common.IdempotencyStorage;
import dev.hata.scheduler.common.RateLimitProperties;
import dev.hata.scheduler.common.RedisWindowCounter;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The auth use cases: OTP issuance, login, registration, identity and logout
 * (design D5/D7/D11). Every write runs the ADR-008 idempotency replay first,
 * then the ADR-007 rate limit, then the business rules.
 */
@Service
public class AuthService {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OtpStorage otp;
    private final OtpChannel channel;
    private final JwtService jwt;
    private final SessionStore sessions;
    private final UserRepository users;
    private final CustomerProfileProvider profiles;
    private final IdempotencyStorage idempotency;
    private final RedisWindowCounter counter;
    private final DealershipFeaturesProvider dealershipFeatures;
    private final RateLimitProperties rateLimits;
    private final AuthProperties properties;

    AuthService(
            OtpStorage otp,
            OtpChannel channel,
            JwtService jwt,
            SessionStore sessions,
            UserRepository users,
            CustomerProfileProvider profiles,
            IdempotencyStorage idempotency,
            RedisWindowCounter counter,
            DealershipFeaturesProvider dealershipFeatures,
            RateLimitProperties rateLimits,
            AuthProperties properties) {
        this.otp = otp;
        this.channel = channel;
        this.jwt = jwt;
        this.sessions = sessions;
        this.users = users;
        this.profiles = profiles;
        this.idempotency = idempotency;
        this.counter = counter;
        this.dealershipFeatures = dealershipFeatures;
        this.rateLimits = rateLimits;
        this.properties = properties;
    }

    /**
     * Issues a purpose-scoped OTP and dispatches it through the channel stub
     * (design D7). A replayed idempotency key returns the cached 202 body and
     * dispatches nothing.
     */
    public Result sendOtp(String phone, String type, String idempotencyKey, HttpServletRequest request) {
        String slot = IdempotencyStorage.slot("otp-send", phone, idempotencyKey);
        Optional<IdempotencyStorage.CachedResponse> cached = Optional.ofNullable(idempotency.load(slot));
        if (cached.isPresent()) {
            return Result.fromCached(cached.get());
        }
        requirePhone(phone);
        OtpPurpose purpose = OtpPurpose.fromWire(type);
        String ip = clientIp(request);

        RateLimitProperties.Resolved limits = limitsFor(phone);
        if (!counter.tryAcquire(
                "ratelimit:otp-send:phone:" + phone + ":" + purpose.value(),
                limits.otpSendPerPhonePurpose(),
                limits.otpSendPerPhonePurposeWindow())) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    "Too many OTP requests for this phone and purpose. Try again later.");
        }
        if (!counter.tryAcquire("ratelimit:otp-send:ip:" + ip, limits.otpSendPerIp(), limits.otpSendPerIpWindow())) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    "Too many OTP requests from this address. Try again later.");
        }

        String code = otp.save(phone, purpose);
        channel.send(phone, code);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("type", purpose.name());
        if (properties.otp().echo()) {
            body.put("otpCode", code);
        }
        Result result = new Result(202, body, null);
        idempotency.store(slot, result.toCached());
        return result;
    }

    /**
     * Logs in a seeded account. Verify never creates a user row (spec: login by
     * verified OTP).
     */
    @Transactional
    public Result verifyOtp(String phone, String code, String idempotencyKey, HttpServletRequest request) {
        String slot = IdempotencyStorage.slot("otp-verify", phone, idempotencyKey);
        Optional<IdempotencyStorage.CachedResponse> cached = Optional.ofNullable(idempotency.load(slot));
        if (cached.isPresent()) {
            return Result.fromCached(cached.get());
        }
        requirePhone(phone);
        String ip = clientIp(request);

        RateLimitProperties.Resolved limits = limitsFor(phone);
        if (!counter.tryAcquire(
                "ratelimit:otp-verify:ip:" + ip, limits.otpVerifyPerIp(), limits.otpVerifyPerIpWindow())) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    "Too many verification requests from this address. Try again later.");
        }

        Optional<User> found = users.findByPhone(phone);
        if (found.isEmpty()) {
            otp.consume(phone, OtpPurpose.LOGIN, code);
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "Phone is not registered",
                    "This phone has no account. Register the customer first, then log in.");
        }
        User user = found.get();
        if (!otp.consume(phone, OtpPurpose.LOGIN, code)) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "OTP verification failed",
                    "The code is wrong, expired, or was issued for another purpose.");
        }
        Result result = issueSession(user, 200);
        idempotency.store(slot, result.toCached());
        return result;
    }

    /**
     * Creates the customer account and the login session in one call
     * (design D11 / spec: one-shot registration).
     */
    @Transactional
    public Result register(
            String phone,
            String firstName,
            String lastName,
            String code,
            boolean roleSupplied,
            String idempotencyKey,
            HttpServletRequest request) {
        String slot = IdempotencyStorage.slot("register", phone, idempotencyKey);
        Optional<IdempotencyStorage.CachedResponse> cached = Optional.ofNullable(idempotency.load(slot));
        if (cached.isPresent()) {
            return Result.fromCached(cached.get());
        }
        requirePhone(phone);
        if (roleSupplied) {
            throw new AuthException(
                    HttpStatus.FORBIDDEN,
                    "Role pinning",
                    "Registration always creates a CUSTOMER account and rejects a role field.");
        }
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST, "Invalid request body", "The request needs a firstName and a lastName.");
        }
        String ip = clientIp(request);

        RateLimitProperties.Resolved limits = limitsFor(null);
        if (!counter.tryAcquire("ratelimit:register:ip:" + ip, limits.registerPerIp(), limits.registerPerIpWindow())) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    "Too many registration requests from this address. Try again later.");
        }

        if (users.existsByPhone(phone)) {
            throw new AuthException(
                    HttpStatus.CONFLICT,
                    "Phone already registered",
                    "This phone already belongs to an account. Use the login flow instead.");
        }
        if (!otp.consume(phone, OtpPurpose.REGISTER, code)) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "OTP verification failed",
                    "The code is wrong, expired, or was issued for another purpose.");
        }

        UUID dealershipId = UUID.fromString(properties.registration().defaultDealershipId());
        UUID userId = UUID.randomUUID();
        User user = newUser(userId, phone, "CUSTOMER", dealershipId);
        users.save(user);
        profiles.createProfile(userId, dealershipId, firstName.trim(), lastName.trim());

        Result result = issueSession(user, 201);
        idempotency.store(slot, result.toCached());
        return result;
    }

    /**
     * Returns the caller identity (spec: session introspection).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> me(AuthPrincipal principal) {
        User user = users.findById(principal.userId())
                .orElseThrow(() ->
                        new AuthException(HttpStatus.UNAUTHORIZED, "Session invalid", "The session has no account."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId().toString());
        body.put("role", user.getRole());
        body.put("phone", user.getPhone());
        body.put(
                "dealershipId",
                user.getDealershipId() == null ? null : user.getDealershipId().toString());
        body.put("email", user.getEmail());
        profiles.findByUserId(user.getId()).ifPresent(profile -> {
            body.put("firstName", profile.firstName());
            body.put("lastName", profile.lastName());
        });
        return body;
    }

    /**
     * Applies the editable profile fields (spec: profile edit after login).
     * The phone never changes, and staff roles carry no editable name fields.
     */
    @Transactional
    public Map<String, Object> patchMe(
            AuthPrincipal principal,
            String firstName,
            String lastName,
            String email,
            boolean phoneSupplied,
            String idempotencyKey) {
        if (phoneSupplied) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Phone is not editable",
                    "The phone number is the login identity and never changes.");
        }
        User user = users.findById(principal.userId())
                .orElseThrow(() ->
                        new AuthException(HttpStatus.UNAUTHORIZED, "Session invalid", "The session has no account."));
        boolean staff = "CUSTOMER".equals(user.getRole()) || "TECHNICIAN".equals(user.getRole());
        boolean namesSupplied = firstName != null || lastName != null;
        if (namesSupplied && !staff) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Names are not editable for this role",
                    "Only a CUSTOMER or a TECHNICIAN can change the profile names.");
        }
        if (email != null) {
            user.setEmail(email.trim());
            users.save(user);
        }
        if (namesSupplied) {
            CustomerProfileProvider.ProfileView current = profiles.findByUserId(user.getId())
                    .orElseThrow(() -> new AuthException(
                            HttpStatus.NOT_FOUND, "Profile missing", "The account has no customer profile."));
            String newFirst = firstName != null && !firstName.isBlank() ? firstName.trim() : current.firstName();
            String newLast = lastName != null && !lastName.isBlank() ? lastName.trim() : current.lastName();
            profiles.updateNames(user.getId(), newFirst, newLast);
        }
        return me(principal);
    }

    /**
     * Revokes the session and clears the cookie (spec: logout revocation).
     */
    public void logout(AuthPrincipal principal) {
        sessions.revoke(principal.jti());
    }

    /**
     * Builds the session cookie header.
     */
    public String sessionCookie(String token, Duration maxAge) {
        return properties.jwt().cookieName() + "=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
                + maxAge.toSeconds();
    }

    /**
     * Builds the cookie header that clears the session cookie.
     */
    public String clearedCookie() {
        return properties.jwt().cookieName() + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0";
    }

    private Result issueSession(User user, int status) {
        JwtService.Issued issued = jwt.issue(
                user.getId().toString(),
                user.getRole(),
                user.getPhone(),
                user.getDealershipId() == null ? null : user.getDealershipId().toString());
        sessions.save(issued.jti(), user.getId().toString(), Duration.between(Instant.now(), issued.expiresAt()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", user.getId().toString());
        body.put("role", user.getRole());
        body.put("phone", user.getPhone());
        body.put(
                "dealershipId",
                user.getDealershipId() == null ? null : user.getDealershipId().toString());
        return new Result(
                status, body, sessionCookie(issued.token(), properties.jwt().ttl()));
    }

    private RateLimitProperties.Resolved limitsFor(String phone) {
        UUID dealershipId = null;
        if (phone != null) {
            dealershipId = users.findByPhone(phone).map(User::getDealershipId).orElse(null);
        }
        return dealershipFeatures.resolveRateLimits(dealershipId);
    }

    private String clientIp(HttpServletRequest request) {
        if (rateLimits.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void requirePhone(String phone) {
        if (phone == null || !E164.matcher(phone).matches()) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid phone number",
                    "The phone number must use the E.164 format, for example +84900000004.");
        }
    }

    private User newUser(UUID id, String phone, String role, UUID dealershipId) {
        User user = new User();
        user.assign(id, phone, role, dealershipId);
        return user;
    }

    /**
     * A response with its status, body and optional session cookie.
     */
    public record Result(int status, Map<String, Object> body, String setCookie) {

        public static Result fromCached(IdempotencyStorage.CachedResponse cached) {
            Map<String, Object> body;
            try {
                body = MAPPER.readValue(cached.body(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                body = Map.of();
            }
            return new Result(cached.status(), body, cached.hasSetCookie() ? cached.setCookie() : null);
        }

        public IdempotencyStorage.CachedResponse toCached() {
            String raw;
            try {
                raw = MAPPER.writeValueAsString(body);
            } catch (Exception ignored) {
                raw = "{}";
            }
            return new IdempotencyStorage.CachedResponse(status, raw, setCookie);
        }
    }
}
