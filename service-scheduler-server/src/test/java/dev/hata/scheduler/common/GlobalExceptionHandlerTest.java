package dev.hata.scheduler.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Covers the RFC 7807 handler (ADR-006). Every error answers an
 * {@code application/problem+json} body.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesAnAuthException() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");

        ResponseEntity<ApiProblem> response = handler.handleAuth(
                new AuthException(HttpStatus.FORBIDDEN, "Role pinning", "No role field allowed."), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().type()).isEqualTo("/errors/403");
        assertThat(response.getBody().title()).isEqualTo("Role pinning");
        assertThat(response.getBody().detail()).isEqualTo("No role field allowed.");
        assertThat(response.getBody().instance()).isEqualTo("/api/v1/auth/register");
    }

    @Test
    void handlesAnInvalidBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");

        ResponseEntity<ApiProblem> response = handler.handleInvalid(new IllegalArgumentException("bad body"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().title()).isEqualTo("Invalid request body");
    }

    @Test
    void handlesAnUnexpectedFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");

        ResponseEntity<ApiProblem> response = handler.handleOther(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().title()).isEqualTo("Unexpected server error");
        assertThat(response.getBody().detail()).isEqualTo("boom");
    }

    @Test
    void handlesAFailureWithoutAMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");

        ResponseEntity<ApiProblem> response = handler.handleOther(new IllegalStateException(), request);

        assertThat(response.getBody().detail()).isEqualTo("Unexpected server error");
    }

    @Test
    void theBodyCarriesTheProblemMediaType() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");

        ResponseEntity<ApiProblem> response = handler.handleOther(new IllegalStateException("boom"), request);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
