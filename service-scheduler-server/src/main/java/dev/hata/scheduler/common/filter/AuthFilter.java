package dev.hata.scheduler.common.filter;

import java.io.IOException;

import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.common.security.JwtService;
import dev.hata.scheduler.common.security.RedisUnavailableException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the {@code jwt} session cookie and exposes the caller principal in request scope.
 * Uses the stateless JwtService for in-memory token verification.
 * Returns a controlled service-unavailable response when Redis is unavailable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = "dev.hata.scheduler.principal";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JwtService jwtService;

    public AuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readCookie(request);
        if (token != null) {
            try {
                jwtService
                        .authenticate(token)
                        .ifPresent(principal -> request.setAttribute(PRINCIPAL_ATTRIBUTE, principal));
            } catch (RedisUnavailableException ex) {
                org.slf4j.LoggerFactory.getLogger(AuthFilter.class)
                        .warn(
                                "Redis auth check failed for request {} (correlation: {}) - cause: {}",
                                request.getRequestURI(),
                                request.getHeader("X-Correlation-Id"),
                                ex.getMessage(),
                                ex);
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                response.setContentType("application/problem+json;charset=UTF-8");
                ApiProblem problem = ApiProblem.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "Service Unavailable",
                        "Authentication service temporarily unavailable",
                        request.getRequestURI());
                MAPPER.writeValue(response.getWriter(), problem);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = jwtService.cookieName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
