package dev.hata.scheduler.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reports the build that serves the request. The header pins the API suite to
 * the running build. A stale server then fails the suite instead of testing
 * the old code.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class BuildIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Build-Id";

    private final String buildId;

    BuildIdFilter(@Value("${app.build.id:}") String buildId) {
        this.buildId = buildId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!buildId.isBlank()) {
            response.setHeader(HEADER, buildId);
        }
        chain.doFilter(request, response);
    }
}
