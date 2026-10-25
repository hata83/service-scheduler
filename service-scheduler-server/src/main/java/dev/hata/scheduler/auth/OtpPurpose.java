package dev.hata.scheduler.auth;

/**
 * OTP purpose slots (design D7). The slot name is the Redis key segment:
 * {@code otp:login:{phone}} and {@code otp:register:{phone}}. A purpose
 * never reads the other purpose's slot.
 */
public enum OtpPurpose {
    LOGIN("login"),
    REGISTER("register");

    private final String slotName;

    OtpPurpose(String slotName) {
        this.slotName = slotName;
    }

    public String value() {
        return slotName;
    }
}
