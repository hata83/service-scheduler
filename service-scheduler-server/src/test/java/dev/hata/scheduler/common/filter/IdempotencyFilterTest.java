package dev.hata.scheduler.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import dev.hata.scheduler.common.idempotency.IdempotencyStore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Covers the idempotency filter (ADR-008). Validates header presence, binds
 * slots to the SHA-256 request body hash, claims atomic in-flight locks,
 * caches 2xx responses, and replays cached responses.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyFilterTest {

    @Mock
    private FilterChain chain;

    @Mock
    private IdempotencyStore store;

    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        when(store.slot(anyString(), anyString(), anyString()))
                .thenAnswer(inv ->
                        "idempotency:" + inv.getArgument(0) + ":" + inv.getArgument(1) + ":" + inv.getArgument(2));
        when(store.tryClaim(anyString(), anyString())).thenReturn(true);
        filter = new IdempotencyFilter(
                store,
                new ClientIpResolver(
                        new dev.hata.scheduler.common.ratelimit.RateLimitProperties(false, java.util.Map.of())));
    }

    @Test
    void rejectsAWriteWithoutTheHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString()).contains("Idempotency key missing");
    }

    @Test
    void rejectsAWriteWithABlankHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/auth/me");
        request.addHeader("X-Idempotency-Key", "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void cachesA2xxWriteResponsePreservesBodyStreamAndSurvivesStoreFailure() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/otp/send");
        request.addHeader("X-Idempotency-Key", "key-1");
        request.setContent("{\"phone\":\"+84900000004\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
                    HttpServletRequest req = inv.getArgument(0);
                    assertThat(req.getReader().readLine()).contains("+84900000004");
                    jakarta.servlet.ServletInputStream stream = req.getInputStream();
                    assertThat(stream.isReady()).isTrue();
                    stream.setReadListener(null);
                    assertThat(stream.readAllBytes()).isNotEmpty();
                    assertThat(stream.isFinished()).isTrue();
                    HttpServletResponse resp = inv.getArgument(1);
                    resp.setStatus(HttpServletResponse.SC_ACCEPTED);
                    resp.addHeader("Set-Cookie", "jwt=tok; HttpOnly");
                    resp.getWriter().write("{\"phone\":\"+84900000004\"}");
                    return null;
                })
                .when(chain)
                .doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_ACCEPTED);
        assertThat(response.getContentAsString()).contains("+84900000004");
        verify(store).store(anyString(), eq(new IdempotencyStore.CachedResponse(202, "{\"phone\":\"+84900000004\"}")));
        verify(store).releaseClaim(anyString(), anyString());

        doThrow(new RuntimeException("redis down")).when(store).store(anyString(), any());
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("POST", "/api/v1/auth/otp/send");
        secondRequest.addHeader("X-Idempotency-Key", "key-1");
        secondRequest.setContent("{\"phone\":\"+84900000004\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, chain);
        assertThat(secondResponse.getStatus()).isEqualTo(HttpServletResponse.SC_ACCEPTED);
    }

    @Test
    void replaysACachedResponseWithoutInvokingFilterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.addHeader("X-Idempotency-Key", "key-replay");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(store.load(anyString()))
                .thenReturn(new IdempotencyStore.CachedResponse(
                        201, "{\"role\":\"CUSTOMER\"}", "application/json;charset=UTF-8"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"role\":\"CUSTOMER\"}");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsConcurrentInFlightDuplicateWith409() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/otp/send");
        request.addHeader("X-Idempotency-Key", "key-inflight");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(store.tryClaim(anyString(), anyString())).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(response.getContentType()).contains("application/problem+json");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doesNotCacheNon2xxWriteResponse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/auth/me");
        request.addHeader("X-Idempotency-Key", "key-err");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doAnswer(inv -> {
                    HttpServletResponse resp = inv.getArgument(1);
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return null;
                })
                .when(chain)
                .doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(store, never()).store(anyString(), any());
    }

    @Test
    void passesAReadAndLogoutWithoutTheHeader() throws ServletException, IOException {
        MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse getResponse = new MockHttpServletResponse();
        filter.doFilter(getRequest, getResponse, chain);
        assertThat(getResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);

        MockHttpServletRequest logoutRequest = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        MockHttpServletResponse logoutResponse = new MockHttpServletResponse();
        filter.doFilter(logoutRequest, logoutResponse, chain);
        assertThat(logoutResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);

        MockHttpServletRequest actuatorRequest = new MockHttpServletRequest("POST", "/actuator/health");
        MockHttpServletResponse actuatorResponse = new MockHttpServletResponse();
        filter.doFilter(actuatorRequest, actuatorResponse, chain);
        assertThat(actuatorResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
