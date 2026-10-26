package dev.hata.scheduler.common.otp;

import java.time.Duration;

/**
 * Domain event published when an OTP code is generated for verification.
 */
public record OtpGeneratedEvent(String phone, String code, OtpType type, Duration ttl) {}
