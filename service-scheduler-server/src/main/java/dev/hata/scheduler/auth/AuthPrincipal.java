package dev.hata.scheduler.auth;

import java.util.UUID;

/**
 * The authenticated caller. The auth filter places it in the request scope.
 */
public record AuthPrincipal(UUID userId, String role, String phone, UUID dealershipId, String jti) {}
