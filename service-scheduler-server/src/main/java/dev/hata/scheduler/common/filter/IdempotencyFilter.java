package dev.hata.scheduler.common.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.common.idempotency.IdempotencyStore;
import dev.hata.scheduler.common.security.AuthPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces {@code X-Idempotency-Key} on write endpoints after {@link AuthFilter},
 * bounds request body size, binds the idempotency slot to the caller principal or client IP
 * and SHA-256 request payload hash, claims an owner-scoped atomic lock, and
 * caches/replays 2xx JSON bodies in {@link IdempotencyStore} (ADR-008 / design D8).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Idempotency-Key";
    public static final String MDC_KEY = "idempotencyKey";
    public static final int MAX_KEY_LENGTH = 128;
    public static final int MAX_BODY_BYTES = 64 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IdempotencyStore store;

    public IdempotencyFilter(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!requiresKey(request)) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            writeProblem(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Idempotency key missing",
                    "Request must include a valid X-Idempotency-Key header (1..128 chars).",
                    request.getRequestURI());
            return;
        }

        byte[] rawBytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (rawBytes.length > MAX_BODY_BYTES) {
            writeProblem(
                    response,
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Request body too large",
                    "Write request body must not exceed 65536 bytes.",
                    request.getRequestURI());
            return;
        }

        CachedBodyRequest wrappedRequest = new CachedBodyRequest(request, rawBytes);
        String callerScope = resolveCallerScope(request);
        String bodyHash = sha256Hex(rawBytes);
        String endpoint = request.getMethod() + ":" + request.getRequestURI();
        String scope = callerScope + ":" + bodyHash;
        String slot = store.slot(endpoint, scope, key);
        String claimOwner = UUID.randomUUID().toString();

        MDC.put(MDC_KEY, sha256Hex(key.getBytes(StandardCharsets.UTF_8)).substring(0, 12));
        try {
            IdempotencyStore.CachedResponse cached = store.load(slot);
            if (cached != null) {
                replayResponse(response, cached);
                return;
            }
            if (!store.tryClaim(slot, claimOwner)) {
                writeProblem(
                        response,
                        HttpServletResponse.SC_CONFLICT,
                        "Idempotent request in progress",
                        "A request with this X-Idempotency-Key is currently being processed.",
                        request.getRequestURI());
                return;
            }
            // After claim, check if first request finished between load and claim.
            IdempotencyStore.CachedResponse cachedAfterClaim = store.load(slot);
            if (cachedAfterClaim != null) {
                replayResponse(response, cachedAfterClaim);
                store.releaseClaim(slot, claimOwner);
                return;
            }
            try {
                ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
                chain.doFilter(wrappedRequest, wrappedResponse);

                int status = wrappedResponse.getStatus();
                if (status >= 200 && status < 300) {
                    String respBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                    String contentType = wrappedResponse.getContentType();
                    if (contentType == null || contentType.isBlank()) {
                        contentType = "application/json;charset=UTF-8";
                    }
                    try {
                        store.store(slot, new IdempotencyStore.CachedResponse(status, respBody, contentType));
                    } catch (RuntimeException ignored) {
                        // Cache store failure does not fail an already-completed request
                    }
                }
                wrappedResponse.copyBodyToResponse();
            } finally {
                store.releaseClaim(slot, claimOwner);
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCallerScope(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Bootstrap auth endpoints are unauthenticated or pre-session:
        // keep the caller scope as "anon" so client proxy rotation or post-login session cookies
        // do not change the idempotency slot on replay.
        if (uri.startsWith("/api/v1/auth/register") || uri.startsWith("/api/v1/auth/otp/")) {
            return "anon";
        }
        Object attr = request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        if (attr instanceof AuthPrincipal principal && principal.userId() != null) {
            return principal.userId().toString();
        }
        return "anon";
    }

    private static void replayResponse(HttpServletResponse response, IdempotencyStore.CachedResponse cached)
            throws IOException {
        response.setStatus(cached.status());
        String contentType = cached.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/json;charset=UTF-8";
        }
        response.setContentType(contentType);
        response.getWriter().write(cached.body());
    }

    private static void writeProblem(
            HttpServletResponse response, int status, String title, String detail, String instance) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json;charset=UTF-8");
        ApiProblem problem = ApiProblem.of(status, title, detail, instance);
        MAPPER.writeValue(response.getWriter(), problem);
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private boolean requiresKey(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PATCH".equals(method) && !"PUT".equals(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.startsWith("/api/v1/") && !uri.endsWith("/logout");
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = Arrays.copyOf(cachedBody, cachedBody.length);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    // synchronous in-memory buffer
                }

                @Override
                public int read() {
                    return byteStream.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
