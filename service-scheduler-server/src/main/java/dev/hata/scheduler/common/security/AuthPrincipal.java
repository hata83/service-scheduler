package dev.hata.scheduler.common.security;

import java.util.UUID;

/**
 * The authenticated caller principal extracted from the stateless JWT cookie
 * and placed in request scope by the auth filter.
 */
public record AuthPrincipal(
        UUID userId,
        String role,
        String phone,
        UUID dealershipId,
        String email,
        String firstName,
        String lastName,
        String jti) {

    public AuthPrincipal(UUID userId, String role, String phone, UUID dealershipId, String jti) {
        this(userId, role, phone, dealershipId, null, null, null, jti);
    }
}
