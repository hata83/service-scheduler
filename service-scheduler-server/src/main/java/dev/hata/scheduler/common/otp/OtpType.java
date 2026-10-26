package dev.hata.scheduler.common.otp;

/**
 * Supported OTP purposes.
 */
public enum OtpType {
    LOGIN("login"),
    REGISTER("register");

    private final String wireValue;

    OtpType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String value() {
        return wireValue;
    }

    public static OtpType fromWire(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("OTP type is missing.");
        }
        for (OtpType type : values()) {
            if (type.name().equalsIgnoreCase(raw.trim()) || type.wireValue.equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OTP type: " + raw);
    }
}
