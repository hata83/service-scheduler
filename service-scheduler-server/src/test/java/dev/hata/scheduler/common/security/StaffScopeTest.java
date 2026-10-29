package dev.hata.scheduler.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for the staff schedule scope resolver.
 * MANAGER is pinned to its dealership claim. ADMIN may select any dealership or read all.
 * Non-staff roles are rejected with HTTP 403.
 */
class StaffScopeTest {

    private static final UUID DEALERSHIP_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEALERSHIP_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    @DisplayName("Manager without explicit dealership reads its own dealership")
    void managerWithoutExplicitDealershipReadsOwn() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_A, "jti");

        UUID scope = StaffScope.dealershipScope(principal, null);

        assertThat(scope).isEqualTo(DEALERSHIP_A);
    }

    @Test
    @DisplayName("Manager requesting a foreign dealership is rejected with 403")
    void managerForeignDealershipRejected() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", DEALERSHIP_A, "jti");

        assertThatThrownBy(() -> StaffScope.dealershipScope(principal, DEALERSHIP_B))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Admin without explicit dealership reads all dealerships")
    void adminWithoutExplicitDealershipReadsAll() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "ADMIN", "+84900000001", null, "jti");

        assertThat(StaffScope.dealershipScope(principal, null)).isNull();
    }

    @Test
    @DisplayName("Admin selects one dealership by parameter")
    void adminSelectsDealershipByParameter() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "ADMIN", "+84900000001", null, "jti");

        assertThat(StaffScope.dealershipScope(principal, DEALERSHIP_B)).isEqualTo(DEALERSHIP_B);
    }

    @Test
    @DisplayName("Customer role is rejected with 403")
    void customerRoleRejected() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "CUSTOMER", "+84900000004", null, "jti");

        assertThatThrownBy(() -> StaffScope.dealershipScope(principal, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Technician role is rejected with 403 on schedule reads")
    void technicianRoleRejected() {
        AuthPrincipal principal =
                new AuthPrincipal(UUID.randomUUID(), "TECHNICIAN", "+84900000003", DEALERSHIP_A, "jti");

        assertThatThrownBy(() -> StaffScope.dealershipScope(principal, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Manager with a null dealership claim is rejected with 403")
    void managerWithNullClaimRejected() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "MANAGER", "+84900000002", null, "jti");

        assertThatThrownBy(() -> StaffScope.dealershipScope(principal, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Missing principal is rejected with 401")
    void missingPrincipalRejected() {
        assertThatThrownBy(() -> StaffScope.dealershipScope(null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Staff role predicate matches manager and admin only")
    void staffRolePredicateMatchesManagerAndAdmin() {
        assertThat(StaffScope.isStaffRole("MANAGER")).isTrue();
        assertThat(StaffScope.isStaffRole("ADMIN")).isTrue();
        assertThat(StaffScope.isStaffRole("TECHNICIAN")).isFalse();
        assertThat(StaffScope.isStaffRole("CUSTOMER")).isFalse();
        assertThat(StaffScope.isStaffRole(null)).isFalse();
    }
}
