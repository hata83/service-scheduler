package dev.hata.scheduler.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

/**
 * Covers the stateless JWT token issuance and in-memory cryptographic verification (ADR-005 / design D5).
 */
class JwtServiceTest {

    private JwtService service() {
        return new JwtService(new JwtProperties(
                "secret-secret-secret-secret-secret-secret-1234", "service-scheduler", Duration.ofHours(8), "jwt"));
    }

    @Test
    void issueAndReadRoundTripTheClaims() {
        JwtService service = service();

        JwtService.Issued issued = service.issue(
                "00000000-0000-0000-0000-000000000005",
                "CUSTOMER",
                "+84900000004",
                "00000000-0000-0000-0000-000000000001");

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.jti()).isNotBlank();
        Claims claims = service.read(issued.token()).orElseThrow();
        assertThat(claims.getSubject()).isEqualTo("00000000-0000-0000-0000-000000000005");
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get("ph", String.class)).isEqualTo("+84900000004");
        assertThat(claims.get("did", String.class)).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(claims.getId()).isEqualTo(issued.jti());
        assertThat(claims.getIssuer()).isEqualTo("service-scheduler");
    }

    @Test
    void authenticateReturnsPrincipalInStatelessMemory() {
        JwtService service = service();
        String userIdStr = "00000000-0000-0000-0000-000000000005";
        String dealerIdStr = "00000000-0000-0000-0000-000000000001";

        // With dealership id
        JwtService.Issued issuedWithDealer = service.issue(userIdStr, "MANAGER", "+84900000002", dealerIdStr);
        Optional<AuthPrincipal> principalOpt = service.authenticate(issuedWithDealer.token());
        assertThat(principalOpt).isPresent();
        assertThat(principalOpt.get().dealershipId()).isEqualTo(UUID.fromString(dealerIdStr));

        // With null dealership id
        JwtService.Issued issuedNullDealer = service.issue(userIdStr, "CUSTOMER", "+84900000004", null);
        Optional<AuthPrincipal> principalNullDealer = service.authenticate(issuedNullDealer.token());
        assertThat(principalNullDealer).isPresent();
        assertThat(principalNullDealer.get().dealershipId()).isNull();

        // With blank dealership id
        JwtService.Issued issuedBlankDealer = service.issue(userIdStr, "CUSTOMER", "+84900000004", "   ");
        Optional<AuthPrincipal> principalBlankDealer = service.authenticate(issuedBlankDealer.token());
        assertThat(principalBlankDealer).isPresent();
        assertThat(principalBlankDealer.get().dealershipId()).isNull();
    }

    @Test
    void authenticateRejectsInvalidSubjectUuid() {
        JwtService service = service();
        JwtService.Issued invalidSub = service.issue("not-a-uuid", "CUSTOMER", "+84900000004", null);
        assertThat(service.authenticate(invalidSub.token())).isEmpty();
    }

    @Test
    void getterPropertiesReportConfiguredState() {
        JwtService service = service();
        assertThat(service.cookieName()).isEqualTo("jwt");
        assertThat(service.ttl()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void theTokenUsesHs256() {
        JwtService service = service();

        String token = service.issue(
                        "00000000-0000-0000-0000-000000000005",
                        "CUSTOMER",
                        "+84900000004",
                        "00000000-0000-0000-0000-000000000001")
                .token();

        assertThat(token.split("\\.")[0]).isEqualTo("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    void readRejectsEmptyOrBlankTokens() {
        JwtService service = service();
        assertThat(service.read(null)).isEmpty();
        assertThat(service.read("")).isEmpty();
        assertThat(service.read("   ")).isEmpty();
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
        assertThat(service.authenticate("   ")).isEmpty();
    }

    @Test
    void readRejectsATamperedToken() {
        JwtService service = service();

        String token = service.issue(
                        "00000000-0000-0000-0000-000000000005",
                        "CUSTOMER",
                        "+84900000004",
                        "00000000-0000-0000-0000-000000000001")
                .token();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(service.read(tampered)).isEmpty();
        assertThat(service.authenticate(tampered)).isEmpty();
    }

    @Test
    void readRejectsAGarbageToken() {
        assertThat(service().read("not-a-token")).isEmpty();
        assertThat(service().authenticate("not-a-token")).isEmpty();
    }
}
