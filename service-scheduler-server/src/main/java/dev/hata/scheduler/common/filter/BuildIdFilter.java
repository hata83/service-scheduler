package dev.hata.scheduler.common.filter;

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
 * Attaches the build id to response headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class BuildIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Build-Id";

    private final String buildId;

    public BuildIdFilter(@Value("${app.build.id:}") String buildId) {
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
