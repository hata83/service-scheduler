package dev.hata.scheduler.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The OTP verify body. The code must come from the LOGIN purpose.
 */
public record OtpVerifyRequest(
        @Schema(example = "+84900000004") String phone, @Schema(example = "483920") String code) {}
