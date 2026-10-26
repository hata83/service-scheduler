package dev.hata.scheduler.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload for OTP dispatch.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpSendResponse(
        @Schema(example = "+84900000004") String phone,
        @Schema(example = "LOGIN") String type,
        @Schema(description = "Echoed code in non-production environments", example = "483920") String otpCode) {}
