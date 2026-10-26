package dev.hata.scheduler.common.filter;

import java.io.IOException;

import dev.hata.scheduler.common.security.JwtService;

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
 * Reads the {@code jwt} session cookie and exposes the caller principal in request scope.
 * Uses the stateless JwtService for in-memory token verification.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class AuthFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = "dev.hata.scheduler.principal";

    private final JwtService jwtService;

    public AuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readCookie(request);
        if (token != null) {
            jwtService.authenticate(token).ifPresent(principal -> request.setAttribute(PRINCIPAL_ATTRIBUTE, principal));
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
}
