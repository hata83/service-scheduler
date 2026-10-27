package dev.hata.scheduler.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The registration body. The service verifies the REGISTER-purpose code and
 * creates the customer account with the session.
 */
public record RegisterRequest(
        @Schema(example = "+84900000999") String phone,
        @Schema(example = "Tai") String firstName,
        @Schema(example = "Le") String lastName,
        @Schema(example = "176482") String code,
        @Schema(description = "Rejected with 403. Registration always creates a CUSTOMER.") String role) {}
