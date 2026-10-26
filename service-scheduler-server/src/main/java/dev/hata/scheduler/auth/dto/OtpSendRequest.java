package dev.hata.scheduler.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The OTP send body. The purpose separates the login code from the
 * registration code (design D2).
 */
public record OtpSendRequest(
        @Schema(example = "+84900000004") String phone,
        @Schema(
                        example = "LOGIN",
                        allowableValues = {"REGISTER", "LOGIN"})
                String type) {}
