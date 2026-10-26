package dev.hata.scheduler.common.security;

import java.util.UUID;

/**
 * The authenticated caller principal placed in request scope by the auth filter.
 */
public record AuthPrincipal(UUID userId, String role, String phone, UUID dealershipId, String jti) {}
