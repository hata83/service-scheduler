package dev.hata.scheduler.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.hata.scheduler.auth.dto.IdentityResponse;
import dev.hata.scheduler.auth.dto.OtpSendResponse;
import dev.hata.scheduler.auth.dto.ProfilePatchRequest;
import dev.hata.scheduler.auth.dto.RegisterRequest;
import dev.hata.scheduler.auth.service.AuthService;
import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.exception.GlobalExceptionHandler;
import dev.hata.scheduler.common.filter.AuthFilter;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Covers the auth endpoints (design D2/D5/D6/D11). The controller answers
 * every failure with an {@code application/problem+json} body (ADR-006).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    private static final String REGISTER_BODY =
            "{\"phone\":\"+84900000999\",\"firstName\":\"Tai\",\"lastName\":\"Le\",\"code\":\"176482\"}";
    private static final String REGISTER_WITH_ROLE =
            "{\"phone\":\"+84900000999\",\"firstName\":\"Tai\",\"lastName\":\"Le\","
                    + "\"code\":\"176482\",\"role\":\"ADMIN\"}";

    @Mock
    private AuthService auth;

    @Mock
    private JwtService jwt;

    @Mock
    private dev.hata.scheduler.common.idempotency.IdempotencyStore idempotencyStore;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(jwt.cookieName()).thenReturn("jwt");
        when(jwt.ttl()).thenReturn(Duration.ofHours(8));
        when(idempotencyStore.slot(anyString(), anyString(), anyString()))
                .thenAnswer(inv ->
                        "idempotency:" + inv.getArgument(0) + ":" + inv.getArgument(1) + ":" + inv.getArgument(2));
        when(idempotencyStore.tryClaim(anyString(), anyString())).thenReturn(true);

        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, jwt))
                .addFilters(new dev.hata.scheduler.common.filter.IdempotencyFilter(idempotencyStore))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendOtpReturns202() throws Exception {
        when(auth.sendOtp(eq("+84900000004"), eq("LOGIN")))
                .thenReturn(new OtpSendResponse("+84900000004", "LOGIN", "483920"));

        mvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-1")
                        .content("{\"phone\":\"+84900000004\",\"type\":\"LOGIN\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.otpCode").value("483920"));
    }

    @Test
    void sendOtpRejectsAMissingIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+84900000004\",\"type\":\"LOGIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Idempotency key missing"));
    }

    @Test
    void sendOtpRejectsAMissingType() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-1")
                        .content("{\"phone\":\"+84900000004\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unknown OTP type"));
    }

    @Test
    void sendOtpRejectsAnUnknownType() throws Exception {
        when(auth.sendOtp(anyString(), eq("NOPE"))).thenThrow(new IllegalArgumentException("unknown OTP type"));

        mvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-1")
                        .content("{\"phone\":\"+84900000004\",\"type\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unknown OTP type"));
    }

    @Test
    void verifyOtpReturnsTheSessionCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(userId, "CUSTOMER", "+84900000004", null, "jti-1");
        JwtService.Issued issued =
                new JwtService.Issued("token-xyz", "jti-1", Instant.now().plus(Duration.ofHours(8)));
        when(auth.verifyOtp(eq("+84900000004"), eq("483920"))).thenReturn(principal);
        when(jwt.issue(principal)).thenReturn(issued);

        mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-2")
                        .content("{\"phone\":\"+84900000004\",\"code\":\"483920\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("jwt=token-xyz")))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void verifyOtpRejectsAMissingIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+84900000004\",\"code\":\"483920\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturns201() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(userId, "CUSTOMER", "+84900000999", null, "jti-2");
        JwtService.Issued issued =
                new JwtService.Issued("token-reg", "jti-2", Instant.now().plus(Duration.ofHours(8)));
        when(auth.register(any(RegisterRequest.class))).thenReturn(principal);
        when(jwt.issue(principal)).thenReturn(issued);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-3")
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", containsString("jwt=token-reg")))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void registerAnswers403ForARoleField() throws Exception {
        when(auth.register(any(RegisterRequest.class)))
                .thenThrow(new AuthException(
                        HttpStatus.FORBIDDEN, "Role pinning", "Registration always creates a CUSTOMER account."));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-3")
                        .content(REGISTER_WITH_ROLE))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void meReturnsTheIdentity() throws Exception {
        IdentityResponse identity =
                new IdentityResponse("id-1", "CUSTOMER", "+84900000004", null, "c@demo.vn", "My", "Nguyen");
        when(auth.me(any())).thenReturn(identity);

        mvc.perform(get("/api/v1/auth/me").requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.firstName").value("My"));
    }

    @Test
    void meRejectsAMissingSession() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void patchMeAppliesTheEditableFields() throws Exception {
        IdentityResponse updated =
                new IdentityResponse("id-1", "CUSTOMER", "+84900000004", null, "a@b.c", "My", "Nguyen");
        when(auth.patchMe(any(), any(ProfilePatchRequest.class))).thenReturn(updated);

        mvc.perform(patch("/api/v1/auth/me")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-4")
                        .content("{\"firstName\":\"My\",\"lastName\":\"Nguyen\",\"email\":\"a@b.c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@b.c"));
    }

    @Test
    void patchMeRejectsAMissingIdempotencyKey() throws Exception {
        mvc.perform(patch("/api/v1/auth/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutClearsTheCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void logoutRejectsAMissingSession() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }

    private static AuthPrincipal principal() {
        return new AuthPrincipal(
                UUID.fromString("00000000-0000-0000-0000-000000000005"), "CUSTOMER", "+84900000004", null, "jti-1");
    }
}
