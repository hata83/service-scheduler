package dev.hata.scheduler.common.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import dev.hata.scheduler.common.idempotency.IdempotencyStore;

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
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Enforces {@code X-Idempotency-Key} on write endpoints, binds the idempotency
 * slot to a SHA-256 hash of the request payload, claims an atomic in-flight
 * lock, and caches/replays 2xx responses in {@link IdempotencyStore} (ADR-008 / design D8).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Idempotency-Key";
    public static final String MDC_KEY = "idempotencyKey";

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
        if (key == null || key.isBlank()) {
            writeProblem(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Idempotency key missing",
                    "Request must include an X-Idempotency-Key header.",
                    request.getRequestURI());
            return;
        }

        byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());
        CachedBodyRequest wrappedRequest = new CachedBodyRequest(request, bodyBytes);
        String bodyHash = sha256Hex(bodyBytes);
        String slot = store.slot(request.getMethod() + ":" + request.getRequestURI(), bodyHash, key);

        MDC.put(MDC_KEY, sha256Hex(key.getBytes(StandardCharsets.UTF_8)).substring(0, 12));
        try {
            IdempotencyStore.CachedResponse cached = store.load(slot);
            if (cached != null) {
                replayResponse(response, cached);
                return;
            }
            if (!store.tryClaim(slot)) {
                writeProblem(
                        response,
                        HttpServletResponse.SC_CONFLICT,
                        "Idempotent request in progress",
                        "A request with this X-Idempotency-Key is currently being processed.",
                        request.getRequestURI());
                return;
            }
            try {
                ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
                chain.doFilter(wrappedRequest, wrappedResponse);

                int status = wrappedResponse.getStatus();
                if (status >= 200 && status < 300) {
                    String respBody = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                    String setCookie = wrappedResponse.getHeader("Set-Cookie");
                    try {
                        store.store(slot, new IdempotencyStore.CachedResponse(status, respBody, setCookie));
                    } catch (RuntimeException ignored) {
                        // Cache store failure does not fail an already-completed request
                    }
                }
                wrappedResponse.copyBodyToResponse();
            } finally {
                store.releaseClaim(slot);
            }
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static void replayResponse(HttpServletResponse response, IdempotencyStore.CachedResponse cached)
            throws IOException {
        response.setStatus(cached.status());
        response.setContentType("application/json");
        if (cached.hasSetCookie()) {
            response.addHeader("Set-Cookie", cached.setCookie());
        }
        response.getWriter().write(cached.body());
    }

    private static void writeProblem(
            HttpServletResponse response, int status, String title, String detail, String instance) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter()
                .write("{\"type\":\"/errors/" + status + "\",\"title\":\"" + title + "\","
                        + "\"status\":" + status + ",\"detail\":\"" + detail + "\","
                        + "\"instance\":\"" + instance + "\"}");
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
            this.cachedBody = cachedBody.clone();
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
