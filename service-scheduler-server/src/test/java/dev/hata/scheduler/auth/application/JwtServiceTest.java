package dev.hata.scheduler.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the session token (ADR-005 / design D5). The token carries the
 * sub, role, did, ph and jti claims and uses HS256.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    private JwtService service() {
        return new JwtService(new AuthProperties(
                new AuthProperties.Jwt(
                        "secret-secret-secret-secret-secret-secret-1234",
                        "service-scheduler",
                        Duration.ofHours(8),
                        "jwt"),
                new AuthProperties.Otp(Duration.ofMinutes(5), true, "zalo-stub"),
                new AuthProperties.Registration("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void issueAndReadRoundTripTheClaims() {
        JwtService service = service();

        JwtService.Issued issued = service.issue("user-1", "CUSTOMER", "+84900000004", "dealer-1");

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.jti()).isNotBlank();
        Claims claims = service.read(issued.token()).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get("ph", String.class)).isEqualTo("+84900000004");
        assertThat(claims.get("did", String.class)).isEqualTo("dealer-1");
        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(claims.getIssuer()).isEqualTo("service-scheduler");
    }

    @Test
    void theTokenUsesHs256() {
        JwtService service = service();

        String token =
                service.issue("user-1", "CUSTOMER", "+84900000004", "dealer-1").token();

        assertThat(token.split("\\.")[0]).isEqualTo("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    void readRejectsATamperedToken() {
        JwtService service = service();

        String token =
                service.issue("user-1", "CUSTOMER", "+84900000004", "dealer-1").token();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(service.read(tampered)).isEmpty();
    }

    @Test
    void readRejectsAGarbageToken() {
        assertThat(service().read("not-a-token")).isEmpty();
    }
}
