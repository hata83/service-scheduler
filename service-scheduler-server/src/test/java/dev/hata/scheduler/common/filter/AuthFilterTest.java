package dev.hata.scheduler.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.common.security.JwtService;
import dev.hata.scheduler.common.security.RedisUnavailableException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Covers the stateless authentication filter. The filter reads the {@code jwt}
 * cookie and places the caller principal in request scope.
 */
@ExtendWith(MockitoExtension.class)
class AuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private FilterChain chain;

    private AuthFilter filter;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(jwtService.cookieName()).thenReturn("jwt");
        filter = new AuthFilter(jwtService);
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

        when(jwtService.authenticate(token)).thenReturn(Optional.of(principal));

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
        when(jwtService.authenticate("bad-token")).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)).isNull();
    }

    @Test
    void returnsServiceUnavailableWhenRedisIsDown() throws Exception {
        String token = "valid.jwt.token";
        when(jwtService.authenticate(token)).thenThrow(new RedisUnavailableException("Redis unavailable"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setCookies(new Cookie("jwt", token));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getContentAsString())
                .contains("Service Unavailable")
                .contains("Authentication service temporarily unavailable");
    }
}
