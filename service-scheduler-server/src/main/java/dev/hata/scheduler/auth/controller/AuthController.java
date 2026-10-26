package dev.hata.scheduler.auth.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.hata.scheduler.auth.dto.OtpSendRequest;
import dev.hata.scheduler.auth.dto.OtpVerifyRequest;
import dev.hata.scheduler.auth.dto.ProfilePatchRequest;
import dev.hata.scheduler.auth.dto.RegisterRequest;
import dev.hata.scheduler.auth.model.AuthPrincipal;
import dev.hata.scheduler.auth.service.AuthService;
import dev.hata.scheduler.common.exception.AuthException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The auth endpoints (design D2/D5/D6/D11). Every error response carries an
 * {@code application/problem+json} body (ADR-006).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "OTP login, customer registration, and profile")
public class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    /**
     * Issues a purpose-scoped OTP and dispatches it through the Zalo stub.
     */
    @Operation(
            summary = "Send an OTP code",
            description = "Issues a single-use six-digit code for the REGISTER or LOGIN purpose.")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Code issued and dispatched"),
        @ApiResponse(responseCode = "400", description = "Invalid phone or unknown type", content = @Content),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @PostMapping("/otp/send")
    public ResponseEntity<Map<String, Object>> sendOtp(
            @RequestBody OtpSendRequest body,
            @Parameter(
                            description = "Unique idempotency key to prevent duplicate submissions",
                            example = "11111111-1111-1111-1111-111111111111")
                    @RequestHeader(value = "X-Idempotency-Key", required = false)
                    String idempotencyKey,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireKey(idempotencyKey);
        if (body == null || body.type() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Unknown OTP type", "The type must be REGISTER or LOGIN.");
        }
        AuthService.Result result;
        try {
            result = auth.sendOtp(body.phone(), body.type(), idempotencyKey, request);
        } catch (IllegalArgumentException ex) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Unknown OTP type", ex.getMessage(), ex);
        }
        return respond(result, response);
    }
    /**
     * Logs a seeded account in with a LOGIN-purpose code.
     */
    @Operation(
            summary = "Verify an OTP code",
            description = "Logs in an existing account. The call never creates a user row.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Session created"),
        @ApiResponse(responseCode = "400", description = "Missing idempotency key", content = @Content),
        @ApiResponse(responseCode = "401", description = "Wrong, expired, or wrong-purpose code", content = @Content),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, Object>> verifyOtp(
            @RequestBody OtpVerifyRequest body,
            @Parameter(
                            description = "Unique idempotency key to prevent duplicate submissions",
                            example = "22222222-2222-2222-2222-222222222222")
                    @RequestHeader(value = "X-Idempotency-Key", required = false)
                    String idempotencyKey,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireKey(idempotencyKey);
        AuthService.Result result = auth.verifyOtp(body.phone(), body.code(), idempotencyKey, request);
        return respond(result, response);
    }

    /**
     * Creates the customer account and the session in one call.
     */
    @Operation(
            summary = "Register a customer",
            description =
                    "Verifies the REGISTER-purpose code, creates the account and the profile, and issues the session.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Customer created and logged in"),
        @ApiResponse(responseCode = "400", description = "Missing idempotency key or invalid body", content = @Content),
        @ApiResponse(responseCode = "401", description = "Wrong, expired, or wrong-purpose code", content = @Content),
        @ApiResponse(responseCode = "403", description = "Role field supplied", content = @Content),
        @ApiResponse(responseCode = "409", description = "Phone already registered", content = @Content),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @Content)
    })
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestBody RegisterRequest body,
            @Parameter(
                            description = "Unique idempotency key to prevent duplicate submissions",
                            example = "33333333-3333-3333-3333-333333333333")
                    @RequestHeader(value = "X-Idempotency-Key", required = false)
                    String idempotencyKey,
            HttpServletRequest request,
            HttpServletResponse response) {
        requireKey(idempotencyKey);
        boolean roleSupplied = body != null && body.role() != null;
        AuthService.Result result = auth.register(
                body.phone(), body.firstName(), body.lastName(), body.code(), roleSupplied, idempotencyKey, request);
        return respond(result, response);
    }

    /**
     * Returns the caller identity.
     */
    @Operation(
            summary = "Read the caller identity",
            description = "Returns the user id, role, phone, dealership, email and the profile names.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Identity returned"),
        @ApiResponse(responseCode = "401", description = "No session cookie", content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.ok(auth.me(principal(request)));
    }

    /**
     * Applies the editable profile fields.
     */
    @Operation(
            summary = "Edit the caller profile",
            description = "Edits the email for every role and the names for a CUSTOMER or a TECHNICIAN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Profile updated"),
        @ApiResponse(
                responseCode = "400",
                description = "Phone field supplied or names sent by a staff role",
                content = @Content),
        @ApiResponse(responseCode = "401", description = "No session cookie", content = @Content)
    })
    @PatchMapping("/me")
    public ResponseEntity<Map<String, Object>> patchMe(
            @RequestBody ProfilePatchRequest body,
            @Parameter(
                            description = "Unique idempotency key to prevent duplicate submissions",
                            example = "44444444-4444-4444-4444-444444444444")
                    @RequestHeader(value = "X-Idempotency-Key", required = false)
                    String idempotencyKey,
            jakarta.servlet.http.HttpServletRequest request) {
        requireKey(idempotencyKey);
        boolean phoneSupplied = body != null && body.phone() != null;
        Map<String, Object> updated = auth.patchMe(
                principal(request), body.firstName(), body.lastName(), body.email(), phoneSupplied, idempotencyKey);
        return ResponseEntity.ok(updated);
    }

    /**
     * Revokes the session and clears the cookie.
     */
    @Operation(summary = "Log out", description = "Deletes the session record and clears the cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Session revoked"),
        @ApiResponse(responseCode = "401", description = "No session cookie", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response) {
        auth.logout(principal(request));
        response.addHeader("Set-Cookie", auth.clearedCookie());
        return ResponseEntity.noContent().build();
    }

    private AuthPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        if (value instanceof AuthPrincipal principal) {
            return principal;
        }
        throw new AuthException(HttpStatus.UNAUTHORIZED, "No session", "The request carries no valid session cookie.");
    }

    private void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new AuthException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency key missing",
                    "Request must include an X-Idempotency-Key header.");
        }
    }

    private ResponseEntity<Map<String, Object>> respond(AuthService.Result result, HttpServletResponse response) {
        if (result.setCookie() != null) {
            response.addHeader("Set-Cookie", result.setCookie());
        }
        Map<String, Object> body = new LinkedHashMap<>(result.body());
        return ResponseEntity.status(result.status()).body(body);
    }
}
