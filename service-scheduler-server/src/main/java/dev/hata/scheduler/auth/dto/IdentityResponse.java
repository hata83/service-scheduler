package dev.hata.scheduler.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The caller identity. The response carries the user id, the role, the phone,
 * the active dealership, the email, and the profile names.
 */
public record IdentityResponse(
        @Schema(example = "00000000-0000-0000-0000-000000000005") String id,
        @Schema(example = "CUSTOMER") String role,
        @Schema(example = "+84900000004") String phone,
        @Schema(example = "00000000-0000-0000-0000-000000000001") String dealershipId,
        @Schema(example = "customer@demo.vn") String email,
        @Schema(example = "My") String firstName,
        @Schema(example = "Nguyen") String lastName) {}
