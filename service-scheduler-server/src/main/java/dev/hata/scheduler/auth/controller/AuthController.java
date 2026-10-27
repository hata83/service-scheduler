package dev.hata.scheduler.auth.controller;

import java.time.Duration;

import dev.hata.scheduler.auth.dto.AuthResponse;
import dev.hata.scheduler.auth.dto.IdentityResponse;
import dev.hata.scheduler.auth.dto.OtpSendRequest;
import dev.hata.scheduler.auth.dto.OtpSendResponse;
import dev.hata.scheduler.auth.dto.OtpVerifyRequest;
import dev.hata.scheduler.auth.dto.ProfilePatchRequest;
import dev.hata.scheduler.auth.dto.RegisterRequest;
import dev.hata.scheduler.auth.service.AuthService;
import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.filter.AuthFilter;
import dev.hata.scheduler.common.ratelimit.RateLimited;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.common.security.JwtService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The five auth endpoints: {@code otp/send}, {@code otp/verify},
 * {@code register}, {@code me} (GET and PATCH) and {@code logout} (design D2).
 * Idempotency is handled by {@link dev.hata.scheduler.common.filter.IdempotencyFilter}
 * and rate limits are enforced declaratively via {@link RateLimited}.
 */
@Tag(name = "Authentication", description = "OTP issuance, login, registration, and session management")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final JwtService jwt;

    public AuthController(AuthService auth, JwtService jwt) {
        this.auth = auth;
        this.jwt = jwt;
    }

    /**
     * Issues a purpose-scoped OTP through the Zalo stub.
     */
    @Operation(summary = "Send OTP", description = "Issues a single-use 6-digit OTP for REGISTER or LOGIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "OTP dispatched"),
        @ApiResponse(responseCode = "400", description = "Malformed phone, unknown type, or missing idempotency key"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @RateLimited({
        @RateLimited.Rule(
                policy = "otp-send-phone",
                keyExpression = "#body != null && #body.type() != null && #body.phone() != null "
                        + "&& #body.phone().matches('^\\+[1-9]\\d{7,14}$') "
                        + "? #body.type().trim().toLowerCase() + ':' + #body.phone() : null",
                message = "Too many OTP requests for this phone. Wait before trying again."),
        @RateLimited.Rule(
                policy = "otp-send-ip",
                message = "Too many OTP requests from this IP. Wait before trying again.")
    })
    @PostMapping("/otp/send")
    public ResponseEntity<OtpSendResponse> sendOtp(
            @RequestBody OtpSendRequest body,
            @Parameter(
                            description = "Unique idempotency key to prevent duplicate submissions",
                            example = "11111111-1111-1111-1111-111111111111")
                    @RequestHeader(value = "X-Idempotency-Key", required = false)
                    String idempotencyKey) {
        if (body == null || body.type() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Unknown OTP type", "The type must be REGISTER or LOGIN.");
        }
        OtpSendResponse response;
        try {
            response = auth.sendOtp(body.phone(), body.type());
        } catch (IllegalArgumentException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Unknown OTP type", ex.getMessage(), ex);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Logs a seeded account in with a LOGIN-purpose code and sets the stateless JWT cookie.
     */
    @Operation(
            summary = "Verify OTP and log in",
            description = "Consumes a LOGIN-purpose OTP and sets the httpOnly JWT cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logged in"),
        @ApiResponse(responseCode = "400", description = "Missing idempotency key or invalid phone"),
        @ApiResponse(responseCode = "401", description = "Wrong, expired, or wrong-purpose code, or unknown phone"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @RateLimited({
        @RateLimited.Rule(
                policy = "otp-verify-phone",
                keyExpression = "#body != null && #body.phone() != null "
                        + "&& #body.phone().matches('^\\+[1-9]\\d{7,14}$') "
                        + "? #body.phone() : null",
                message = "Too many verification attempts for this phone. Wait before trying again."),
        @RateLimited.Rule(
                policy = "otp-verify-ip",
                message = "Too many verification attempts from this IP. Wait before trying again.")
    })
    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(
            @RequestBody OtpVerifyRequest body,
            @Parameter(
                            description = "Unique idempotency key to prevent duplicate submissions",
                            example = "22222222-2222-2222-2222-222222222222")
                    @RequestHeader(value = "X-Idempotency-Key", required = false)
                    String idempotencyKey,
            HttpServletResponse response) {
        AuthPrincipal principal = auth.verifyOtp(body.phone(), body.code());
        setSessionCookie(response, jwt.issue(principal).token());
        return ResponseEntity.ok(
                new AuthResponse(principal.userId(), principal.role(), principal.phone(), principal.dealershipId()));
    }

    /**
     * Creates a customer account and sets the stateless JWT cookie in one call.
     */
    @Operation(
            summary = "Register a new customer",
            description = "Verifies a REGISTER-purpose OTP, creates the customer, and sets the session cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Customer created and logged in"),
        @ApiResponse(responseCode = "400", description = "Missing idempotency key or invalid fields"),
        @ApiResponse(responseCode = "401", description = "Wrong, expired, or LOGIN-purpose code"),
        @ApiResponse(responseCode = "403", description = "Role field supplied"),
        @ApiResponse(responseCode = "409", description = "Phone already belongs to a user"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @RateLimited({
        @RateLimited.Rule(
                policy = "register-phone",
                keyExpression = "#body != null && #body.phone() != null "
                        + "&& #body.phone().matches('^\\+[1-9]\\d{7,14}$') "
                        + "? #body.phone() : null",
                message = "Too many registration attempts for this phone. Wait before trying again."),
        @RateLimited.Rule(
                policy = "register-ip",
                message = "Too many registration attempts from this IP. Wait before trying again.")
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody RegisterRequest body,
            @Parameter(
                            description = "Unique idempotency key to prevent duplicate submissions",
                            example = "33333333-3333-3333-3333-333333333333")
                    @RequestHeader(value = "X-Idempotency-Key", required = false)
                    String idempotencyKey,
            HttpServletResponse response) {
        AuthPrincipal principal = auth.register(body);
        setSessionCookie(response, jwt.issue(principal).token());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(
                        principal.userId(), principal.role(), principal.phone(), principal.dealershipId()));
    }

    /**
     * Returns the caller identity.
     */
    @Operation(summary = "Current user identity", description = "Returns the identity of the session cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Caller identity"),
        @ApiResponse(responseCode = "401", description = "No session cookie", content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<IdentityResponse> me(HttpServletRequest request) {
        return ResponseEntity.ok(auth.me(principal(request)));
    }

    /**
     * Updates the editable profile fields.
     */
    @Operation(
            summary = "Update current user profile",
            description = "Updates the email and centralized profile names. The phone never changes.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated identity"),
        @ApiResponse(responseCode = "400", description = "Phone supplied or missing idempotency key"),
        @ApiResponse(responseCode = "401", description = "No session cookie", content = @Content)
    })
    @PatchMapping("/me")
    public ResponseEntity<IdentityResponse> patchMe(
            @RequestBody ProfilePatchRequest body,
            @Parameter(
                            description = "Unique idempotency key to prevent duplicate submissions",
                            example = "44444444-4444-4444-4444-444444444444")
                    @RequestHeader(value = "X-Idempotency-Key", required = false)
                    String idempotencyKey,
            HttpServletRequest request) {
        IdentityResponse updated = auth.patchMe(principal(request), body);
        return ResponseEntity.ok(updated);
    }

    /**
     * Clears the client session cookie.
     */
    @Operation(summary = "Log out", description = "Clears the client session cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Session revoked"),
        @ApiResponse(responseCode = "401", description = "No session cookie", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        AuthPrincipal principal = principal(request);
        jwt.revoke(principal.jti());
        clearSessionCookie(response);
        return ResponseEntity.noContent().build();
    }

    private void setSessionCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(jwt.cookieName(), token)
                .httpOnly(true)
                .secure(jwt.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(jwt.ttl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(jwt.cookieName(), "")
                .httpOnly(true)
                .secure(jwt.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private AuthPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        if (value instanceof AuthPrincipal principal) {
            return principal;
        }
        throw new AuthException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "Send a valid jwt cookie from /api/v1/auth/otp/verify.");
    }
}
