package dev.hata.scheduler.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import dev.hata.scheduler.auth.model.AuthPrincipal;
import dev.hata.scheduler.auth.service.AuthService;
import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.exception.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendOtpReturns202() throws Exception {
        when(auth.sendOtp(anyString(), anyString(), anyString(), any()))
                .thenReturn(new AuthService.Result(202, Map.of("phone", "+84900000004", "otpCode", "483920"), null));

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
        when(auth.sendOtp(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("unknown OTP type"));

        mvc.perform(post("/api/v1/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-1")
                        .content("{\"phone\":\"+84900000004\",\"type\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Unknown OTP type"));
    }

    @Test
    void verifyOtpReturnsTheSessionCookie() throws Exception {
        when(auth.verifyOtp(anyString(), anyString(), anyString(), any()))
                .thenReturn(new AuthService.Result(200, Map.of("role", "CUSTOMER"), "jwt=token; Path=/; HttpOnly"));

        mvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-2")
                        .content("{\"phone\":\"+84900000004\",\"code\":\"483920\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("jwt=token")))
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
        when(auth.register(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyString(), any()))
                .thenReturn(new AuthService.Result(201, Map.of("role", "CUSTOMER"), "jwt=token; Path=/; HttpOnly"));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-3")
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Set-Cookie", containsString("jwt=token")));
    }

    @Test
    void registerForwardsTheRoleFlag() throws Exception {
        when(auth.register(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyString(), any()))
                .thenReturn(new AuthService.Result(201, Map.of("role", "CUSTOMER"), null));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-3")
                        .content(REGISTER_WITH_ROLE))
                .andExpect(status().isCreated());

        verify(auth)
                .register(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        org.mockito.ArgumentMatchers.eq(true),
                        anyString(),
                        any());
    }

    @Test
    void registerRejectsAMissingIdempotencyKey() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerAnswers403ForARoleField() throws Exception {
        when(auth.register(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyString(), any()))
                .thenThrow(new AuthException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Role pinning",
                        "Registration always creates a CUSTOMER account."));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-3")
                        .content(REGISTER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void meReturnsTheIdentity() throws Exception {
        when(auth.me(any())).thenReturn(Map.of("id", "id-1", "role", "CUSTOMER"));

        mvc.perform(get("/api/v1/auth/me").requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void meRejectsAMissingSession() throws Exception {
        when(auth.me(any()))
                .thenThrow(new AuthException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "No session",
                        "The request carries no valid session cookie."));

        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void patchMeAppliesTheEditableFields() throws Exception {
        when(auth.patchMe(any(), any(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(Map.of("email", "a@b.c"));

        mvc.perform(patch("/api/v1/auth/me")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-4")
                        .content("{\"firstName\":\"My\",\"lastName\":\"Nguyen\",\"email\":\"a@b.c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@b.c"));
    }

    @Test
    void patchMeForwardsThePhoneFlag() throws Exception {
        when(auth.patchMe(any(), any(), any(), any(), anyBoolean(), anyString()))
                .thenReturn(Map.of());

        mvc.perform(patch("/api/v1/auth/me")
                        .requestAttr(AuthFilter.PRINCIPAL_ATTRIBUTE, principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-4")
                        .content("{\"phone\":\"+84900000888\"}"))
                .andExpect(status().isOk());

        verify(auth).patchMe(any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq(true), anyString());
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
        when(auth.clearedCookie()).thenReturn("jwt=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");

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
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000005"),
                "CUSTOMER",
                "+84900000004",
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "jti-1");
    }
}
