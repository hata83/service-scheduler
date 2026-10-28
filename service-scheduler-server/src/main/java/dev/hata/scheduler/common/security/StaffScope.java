package dev.hata.scheduler.common.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the dealership scope for staff schedule reads (change manager-schedule-realtime).
 * MANAGER is pinned to its own {@code dealershipId} claim. ADMIN may select a dealership
 * by parameter or read all dealerships. Non-staff roles are rejected with HTTP 403.
 */
public final class StaffScope {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MANAGER = "MANAGER";

    private StaffScope() {}

    /**
     * Checks whether the role may open the schedule board, history, and staff streams.
     */
    public static boolean isStaffRole(String role) {
        return ROLE_MANAGER.equals(role) || ROLE_ADMIN.equals(role);
    }

    /**
     * Returns the effective dealership scope, or {@code null} when the caller reads all
     * dealerships (ADMIN without a filter).
     *
     * @throws ResponseStatusException HTTP 401 without a principal, HTTP 403 for non-staff
     *     roles, and for a MANAGER requesting a foreign or missing dealership
     */
    public static UUID dealershipScope(AuthPrincipal principal, UUID requestedDealershipId) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!isStaffRole(principal.role())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only MANAGER and ADMIN accounts can read the schedule");
        }
        if (ROLE_ADMIN.equals(principal.role())) {
            return requestedDealershipId;
        }
        if (principal.dealershipId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager has no dealership assignment");
        }
        if (requestedDealershipId != null && !requestedDealershipId.equals(principal.dealershipId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager scope is limited to its own dealership");
        }
        return principal.dealershipId();
    }
}
