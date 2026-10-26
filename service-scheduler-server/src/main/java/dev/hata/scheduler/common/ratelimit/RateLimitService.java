package dev.hata.scheduler.common.ratelimit;

import java.util.UUID;

import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.port.DealershipFeaturesProvider;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Service evaluating and enforcing domain rate limits using {@link WindowCounter}
 * and dynamic feature configurations from {@link DealershipFeaturesProvider}.
 */
@Service
public class RateLimitService {

    private final WindowCounter counter;
    private final DealershipFeaturesProvider dealershipFeatures;

    public RateLimitService(WindowCounter counter, DealershipFeaturesProvider dealershipFeatures) {
        this.counter = counter;
        this.dealershipFeatures = dealershipFeatures;
    }

    /**
     * Enforces rate limits for OTP send requests.
     */
    public void checkOtpSend(String phone, String purposeKey, String ip, UUID dealershipId) {
        RateLimitProperties.Resolved limits = dealershipFeatures.resolveRateLimits(dealershipId);
        if (!counter.tryAcquire(
                "ratelimit:otp-send:phone:" + phone + ":" + purposeKey,
                limits.otpSendPerPhonePurpose(),
                limits.otpSendPerPhonePurposeWindow())) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    "Too many OTP requests for this phone and purpose. Try again later.");
        }
        if (!counter.tryAcquire("ratelimit:otp-send:ip:" + ip, limits.otpSendPerIp(), limits.otpSendPerIpWindow())) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    "Too many OTP requests from this address. Try again later.");
        }
    }

    /**
     * Enforces rate limits for OTP verify requests.
     */
    public void checkOtpVerify(String ip, UUID dealershipId) {
        RateLimitProperties.Resolved limits = dealershipFeatures.resolveRateLimits(dealershipId);
        if (!counter.tryAcquire(
                "ratelimit:otp-verify:ip:" + ip, limits.otpVerifyPerIp(), limits.otpVerifyPerIpWindow())) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    "Too many verification requests from this address. Try again later.");
        }
    }

    /**
     * Enforces rate limits for customer registration requests.
     */
    public void checkRegister(String ip) {
        RateLimitProperties.Resolved limits = dealershipFeatures.resolveRateLimits(null);
        if (!counter.tryAcquire("ratelimit:register:ip:" + ip, limits.registerPerIp(), limits.registerPerIpWindow())) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded",
                    "Too many registration requests from this address. Try again later.");
        }
    }
}
