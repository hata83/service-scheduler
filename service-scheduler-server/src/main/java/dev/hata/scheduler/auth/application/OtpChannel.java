package dev.hata.scheduler.auth.application;

/**
 * Sends an OTP code to the customer. The Zalo ZNS integration arrives with
 * notifications, so Phase 1 uses a stub (design D7).
 */
@FunctionalInterface
public interface OtpChannel {

    /**
     * Delivers the code to the phone.
     *
     * @param phone E.164 phone number
     * @param code six-digit OTP code
     */
    void send(String phone, String code);
}
