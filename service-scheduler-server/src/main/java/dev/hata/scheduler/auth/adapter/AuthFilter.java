package dev.hata.scheduler.auth.adapter;

import java.io.IOException;
import java.util.UUID;

import dev.hata.scheduler.auth.application.JwtService;
import dev.hata.scheduler.auth.application.SessionStore;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@code jwt} session cookie and exposes the caller (design D5).
 * The filter serves the protected identity endpoints. It does not reject a
 * missing cookie, because the controller answers 401 with problem+json.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AuthFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = "dev.hata.scheduler.principal";

    private final JwtService jwt;
    private final SessionStore sessions;

    AuthFilter(JwtService jwt, SessionStore sessions) {
        this.jwt = jwt;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readCookie(request);
        if (token != null) {
            jwt.read(token)
                    .filter(claims -> sessions.exists(claims.getId()))
                    .ifPresent(claims -> request.setAttribute(PRINCIPAL_ATTRIBUTE, toPrincipal(claims)));
        }
        chain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("jwt".equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private AuthPrincipal toPrincipal(Claims claims) {
        String dealershipId = claims.get("did", String.class);
        return new AuthPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("role", String.class),
                claims.get("ph", String.class),
                dealershipId == null || dealershipId.isBlank() ? null : UUID.fromString(dealershipId),
                claims.getId());
    }
}
