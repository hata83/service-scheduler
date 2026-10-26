package dev.hata.scheduler.auth.domain;

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

    /**
     * Maps the wire type onto the purpose. A type outside REGISTER and LOGIN
     * is a bad request.
     */
    public static OtpPurpose fromWire(String type) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        for (OtpPurpose purpose : values()) {
            if (purpose.name().equalsIgnoreCase(type.trim())) {
                return purpose;
            }
        }
        throw new IllegalArgumentException("unknown OTP type " + type);
    }
}
