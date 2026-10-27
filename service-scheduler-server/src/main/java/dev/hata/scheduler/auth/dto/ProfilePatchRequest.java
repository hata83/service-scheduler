package dev.hata.scheduler.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The profile patch body. The email is editable for every role. The names are
 * editable for a CUSTOMER and a TECHNICIAN. The phone never changes.
 */
public record ProfilePatchRequest(
        @Schema(example = "My") String firstName,
        @Schema(example = "Nguyen") String lastName,
        @Schema(example = "my.nguyen@example.com") String email,
        @Schema(description = "Rejected with 400. The phone never changes.") String phone) {}
