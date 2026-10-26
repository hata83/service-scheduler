package dev.hata.scheduler.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;

import dev.hata.scheduler.auth.model.User;
import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.otp.OtpProperties;
import dev.hata.scheduler.common.otp.OtpType;
import dev.hata.scheduler.common.security.AuthPrincipal;
import dev.hata.scheduler.common.security.JwtProperties;
import dev.hata.scheduler.common.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Covers the small value types of the auth and common features: the entities, the OTP
 * type mapping, properties, and the problem body.
 */
class AuthValueTypesTest {

    private static final UUID DEALERSHIP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void userCarriesItsColumns() {
        User user = new User();
        user.assign(USER_ID, "+84900000004", "CUSTOMER");
        user.setEmail("my.nguyen@example.com");

        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getPhone()).isEqualTo("+84900000004");
        assertThat(user.getType()).isEqualTo(User.TYPE_CUSTOMER);
        assertThat(user.getEmail()).isEqualTo("my.nguyen@example.com");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void otpTypeMapsTheWireType() {
        assertThat(OtpType.fromWire("LOGIN")).isEqualTo(OtpType.LOGIN);
        assertThat(OtpType.fromWire("register")).isEqualTo(OtpType.REGISTER);
        assertThat(OtpType.LOGIN.value()).isEqualTo("login");
        assertThat(OtpType.REGISTER.value()).isEqualTo("register");
    }

    @Test
    void otpTypeRejectsAnUnknownType() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> OtpType.fromWire("NOPE")))
                .hasMessageContaining("NOPE");
        assertThat(assertThrows(IllegalArgumentException.class, () -> OtpType.fromWire(null)))
                .hasMessageContaining("missing");
    }

    @Test
    void authExceptionCarriesItsStatusAndTitle() {
        AuthException exception =
                new AuthException(HttpStatus.UNAUTHORIZED, "OTP verification failed", "The code is wrong.");
        AuthException withCause = new AuthException(
                HttpStatus.BAD_REQUEST, "Unknown OTP type", "bad", new IllegalArgumentException("bad"));

        assertThat(exception.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.problemTitle()).isEqualTo("OTP verification failed");
        assertThat(exception.getMessage()).isEqualTo("The code is wrong.");
        assertThat(withCause.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apiProblemCarriesTheRfc7807Fields() {
        ApiProblem problem = ApiProblem.of(400, "Bad request", "The body is wrong.", "/api/v1/auth/register");

        assertThat(problem.type()).isEqualTo("/errors/400");
        assertThat(problem.title()).isEqualTo("Bad request");
        assertThat(problem.status()).isEqualTo(400);
        assertThat(problem.detail()).isEqualTo("The body is wrong.");
        assertThat(problem.instance()).isEqualTo("/api/v1/auth/register");
        assertThat(problem.timestamp()).isNotBlank();
    }

    @Test
    void authPrincipalCarriesItsFields() {
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "CUSTOMER", "+84900000004", DEALERSHIP, "jti-1");

        assertThat(principal.userId()).isEqualTo(USER_ID);
        assertThat(principal.role()).isEqualTo("CUSTOMER");
        assertThat(principal.phone()).isEqualTo("+84900000004");
        assertThat(principal.dealershipId()).isEqualTo(DEALERSHIP);
        assertThat(principal.jti()).isEqualTo("jti-1");
    }

    @Test
    void jwtPropertiesCarryTheConfiguredValues() {
        JwtProperties properties = new JwtProperties(
                "secret-key-123456789012345678901234567890", "service-scheduler", Duration.ofHours(8), "jwt");

        assertThat(properties.cookieName()).isEqualTo("jwt");
        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(8));
        assertThat(properties.issuer()).isEqualTo("service-scheduler");
    }

    @Test
    void otpPropertiesCarryTheConfiguredValues() {
        OtpProperties properties = new OtpProperties(Duration.ofMinutes(5), true, "zalo-stub");

        assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.echo()).isTrue();
        assertThat(properties.channel()).isEqualTo("zalo-stub");
    }

    @Test
    void authDtosCarryTheirFields() {
        dev.hata.scheduler.auth.dto.OtpSendResponse otpSend =
                new dev.hata.scheduler.auth.dto.OtpSendResponse("+84900000004", "LOGIN", "123456");
        assertThat(otpSend.phone()).isEqualTo("+84900000004");
        assertThat(otpSend.type()).isEqualTo("LOGIN");
        assertThat(otpSend.otpCode()).isEqualTo("123456");

        dev.hata.scheduler.auth.dto.AuthResponse authResp =
                new dev.hata.scheduler.auth.dto.AuthResponse(USER_ID, "CUSTOMER", "+84900000004", DEALERSHIP);
        assertThat(authResp.id()).isEqualTo(USER_ID);
        assertThat(authResp.role()).isEqualTo("CUSTOMER");
        assertThat(authResp.phone()).isEqualTo("+84900000004");
        assertThat(authResp.dealershipId()).isEqualTo(DEALERSHIP);

        JwtService.Issued issued = new JwtService.Issued("tok", "jti", java.time.Instant.now());
        dev.hata.scheduler.auth.dto.AuthSession session = new dev.hata.scheduler.auth.dto.AuthSession(authResp, issued);
        assertThat(session.user()).isEqualTo(authResp);
        assertThat(session.session()).isEqualTo(issued);
    }
}
