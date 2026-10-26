package dev.hata.scheduler.auth.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Authentication response payload containing caller identity.
 */
public record AuthResponse(
        @Schema(example = "00000000-0000-0000-0000-000000000005") UUID id,
        @Schema(example = "CUSTOMER") String role,
        @Schema(example = "+84900000004") String phone,
        @Schema(example = "00000000-0000-0000-0000-000000000001") UUID dealershipId) {}
