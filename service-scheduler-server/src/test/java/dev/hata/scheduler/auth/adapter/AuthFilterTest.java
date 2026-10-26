package dev.hata.scheduler.auth.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import dev.hata.scheduler.auth.application.AuthProperties;
import dev.hata.scheduler.auth.application.JwtService;
import dev.hata.scheduler.auth.application.SessionStore;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
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
 * Covers the session filter (design D5). The filter reads the {@code jwt}
 * cookie and places the caller in the request scope.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthFilterTest {

    private static final String SECRET = "secret-secret-secret-secret-secret-secret-1234";

    @Mock
    private SessionStore sessions;

    @Mock
    private FilterChain chain;

    private JwtService jwt;

    @BeforeEach
    void setUp() {
        jwt = new JwtService(new AuthProperties(
                new AuthProperties.Jwt(SECRET, "service-scheduler", Duration.ofHours(8), "jwt"),
                new AuthProperties.Otp(Duration.ofMinutes(5), true, "zalo-stub"),
                new AuthProperties.Registration("00000000-0000-0000-0000-000000000001")));
    }

    private AuthFilter filter() {
        return new AuthFilter(jwt, sessions);
    }

    private String tokenFor(String subject, String role, String phone, String dealershipId) {
        return jwt.issue(subject, role, phone, dealershipId).token();
    }

    @Test
    void placesThePrincipalForAValidSession() throws Exception {
        String token = tokenFor(
                "00000000-0000-0000-0000-000000000005",
                "CUSTOMER",
                "+84900000004",
                "00000000-0000-0000-0000-000000000001");
        Claims claims = jwt.read(token).orElseThrow();
        when(sessions.exists(claims.getId())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", token));

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        Object principal = request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        assertThat(principal).isInstanceOf(AuthPrincipal.class);
        AuthPrincipal auth = (AuthPrincipal) principal;
        assertThat(auth.role()).isEqualTo("CUSTOMER");
        assertThat(auth.phone()).isEqualTo("+84900000004");
        assertThat(auth.dealershipId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(auth.jti()).isNotBlank();
    }

    @Test
    void skipsAnAbsentCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)).isNull();
    }

    @Test
    void skipsABlankCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", ""));

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)).isNull();
    }

    @Test
    void skipsAnInvalidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", "bad"));

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)).isNull();
    }

    @Test
    void skipsARevokedSession() throws Exception {
        String token = tokenFor(
                "00000000-0000-0000-0000-000000000005",
                "CUSTOMER",
                "+84900000004",
                "00000000-0000-0000-0000-000000000001");
        Claims claims = jwt.read(token).orElseThrow();
        when(sessions.exists(claims.getId())).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", token));

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE)).isNull();
    }

    @Test
    void skipsASessionWithoutADealership() throws Exception {
        String token = tokenFor("00000000-0000-0000-0000-000000000005", "CUSTOMER", "+84900000004", null);
        Claims claims = jwt.read(token).orElseThrow();
        when(sessions.exists(claims.getId())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", token));

        filter().doFilter(request, new MockHttpServletResponse(), chain);

        AuthPrincipal principal = (AuthPrincipal) request.getAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE);
        assertThat(principal.dealershipId()).isNull();
    }
}
