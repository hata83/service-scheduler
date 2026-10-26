package dev.hata.scheduler.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import dev.hata.scheduler.auth.config.AuthProperties;
import dev.hata.scheduler.auth.model.AuthPrincipal;
import dev.hata.scheduler.auth.model.OtpPurpose;
import dev.hata.scheduler.auth.model.User;
import dev.hata.scheduler.common.exception.ApiProblem;
import dev.hata.scheduler.common.exception.AuthException;

import org.junit.jupiter.api.Test;

/**
 * Covers the small value types of the auth feature: the entities, the OTP
 * purpose mapping, the session store contract and the problem body.
 */
class AuthValueTypesTest {

    private static final UUID DEALERSHIP = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void userCarriesItsColumns() {
        User user = new User();
        user.assign(USER_ID, "+84900000004", "CUSTOMER", DEALERSHIP);
        user.setEmail("my.nguyen@example.com");

        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getPhone()).isEqualTo("+84900000004");
        assertThat(user.getRole()).isEqualTo("CUSTOMER");
        assertThat(user.getDealershipId()).isEqualTo(DEALERSHIP);
        assertThat(user.getEmail()).isEqualTo("my.nguyen@example.com");
        assertThat(user.isActive()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void otpPurposeMapsTheWireType() {
        assertThat(OtpPurpose.fromWire("LOGIN")).isEqualTo(OtpPurpose.LOGIN);
        assertThat(OtpPurpose.fromWire("register")).isEqualTo(OtpPurpose.REGISTER);
        assertThat(OtpPurpose.LOGIN.value()).isEqualTo("login");
        assertThat(OtpPurpose.REGISTER.value()).isEqualTo("register");
    }

    @Test
    void otpPurposeRejectsAnUnknownType() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class, () -> OtpPurpose.fromWire("NOPE")))
                .hasMessageContaining("NOPE");
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class, () -> OtpPurpose.fromWire(null)))
                .hasMessageContaining("required");
    }

    @Test
    void zaloStubPrintsTheCode() {
        ZaloStubOtpChannel channel = new ZaloStubOtpChannel();

        channel.send("+84900000004", "483920");
    }

    @Test
    void authExceptionCarriesItsStatusAndTitle() {
        AuthException exception = new AuthException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "OTP verification failed", "The code is wrong.");
        AuthException withCause = new AuthException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Unknown OTP type",
                "bad",
                new IllegalArgumentException("bad"));

        assertThat(exception.httpStatus()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
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
    void authPropertiesCarryTheConfiguredValues() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt("secret", "service-scheduler", Duration.ofHours(8), "jwt"),
                new AuthProperties.Otp(Duration.ofMinutes(5), true, "zalo-stub"),
                new AuthProperties.Registration(DEALERSHIP.toString()));

        assertThat(properties.jwt().cookieName()).isEqualTo("jwt");
        assertThat(properties.jwt().ttl()).isEqualTo(Duration.ofHours(8));
        assertThat(properties.otp().echo()).isTrue();
        assertThat(properties.otp().channel()).isEqualTo("zalo-stub");
        assertThat(properties.registration().defaultDealershipId()).isEqualTo(DEALERSHIP.toString());
    }
}
