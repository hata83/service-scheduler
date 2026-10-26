package dev.hata.scheduler.auth.dto;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The caller identity. The response carries the user id, the role, the phone,
 * the dealership, the email and the profile names.
 */
public record IdentityResponse(
        @Schema(example = "00000000-0000-0000-0000-000000000004") String id,
        @Schema(example = "CUSTOMER") String role,
        @Schema(example = "+84900000004") String phone,
        @Schema(example = "00000000-0000-0000-0000-000000000001") String dealershipId,
        @Schema(example = "customer@demo.vn") String email,
        @Schema(example = "My") String firstName,
        @Schema(example = "Nguyen") String lastName) {

    /**
     * Builds the response from the identity map of the service.
     */
    public static IdentityResponse from(Map<String, Object> identity) {
        return new IdentityResponse(
                (String) identity.get("id"),
                (String) identity.get("role"),
                (String) identity.get("phone"),
                (String) identity.get("dealershipId"),
                (String) identity.get("email"),
                (String) identity.get("firstName"),
                (String) identity.get("lastName"));
    }
}
