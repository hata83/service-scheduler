package dev.hata.scheduler.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.auth.model.AuthPrincipal;
import dev.hata.scheduler.auth.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Covers the session filter (design D5). The filter reads the {@code jwt}
 * cookie and places the caller in the request scope.
 */
@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private AuthService authService;

    @Mock
    private FilterChain chain;

    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthFilter(authService);
    }

    @Test
    void placesThePrincipalForAValidSession() throws Exception {
        String token = "valid.jwt.token";
        AuthPrincipal principal = new AuthPrincipal(
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                "CUSTOMER",
                "+84900000004",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "session-jti-123");

        when(authService.authenticate(token)).thenReturn(Optional.of(principal));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setCookies(new Cookie("jwt", token));

        filter.doFilter(request, response, chain);

        Object result = request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        assertThat(result).isInstanceOf(AuthPrincipal.class);
        AuthPrincipal auth = (AuthPrincipal) result;
        assertThat(auth.role()).isEqualTo("CUSTOMER");
        assertThat(auth.phone()).isEqualTo("+84900000004");
        assertThat(auth.dealershipId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(auth.jti()).isEqualTo("session-jti-123");
        verify(chain).doFilter(request, response);
    }

    @Test
    void skipsAnAbsentCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)).isNull();
    }

    @Test
    void skipsABlankCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", "   "));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)).isNull();
    }

    @Test
    void skipsAnInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", "bad-token"));
        when(authService.authenticate("bad-token")).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)).isNull();
    }
}
