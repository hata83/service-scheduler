package dev.hata.scheduler.auth.dto;

import dev.hata.scheduler.common.security.JwtService;

/**
 * Internal result from login or registration containing caller identity and issued session.
 */
public record AuthSession(AuthResponse user, JwtService.Issued session) {}
