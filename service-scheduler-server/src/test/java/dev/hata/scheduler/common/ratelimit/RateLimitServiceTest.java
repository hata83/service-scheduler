package dev.hata.scheduler.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.port.DealershipFeaturesProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitServiceTest {

    private static final UUID DEALERSHIP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private WindowCounter counter;

    @Mock
    private DealershipFeaturesProvider dealershipFeatures;

    private RateLimitProperties properties;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties("classpath:config/rate-limits-test.json", false);
        service = new RateLimitService(counter, dealershipFeatures);
        when(dealershipFeatures.resolveRateLimits(any())).thenReturn(properties.resolve(null));
    }

    @Test
    void checkOtpSendPassesUnderLimits() {
        when(counter.tryAcquire(any(), anyLong(), any())).thenReturn(true);

        service.checkOtpSend("+84900000004", "login", "10.0.0.1", DEALERSHIP_ID);

        verify(counter).tryAcquire(eq("ratelimit:otp-send:phone:+84900000004:login"), eq(5L), any());
        verify(counter).tryAcquire(eq("ratelimit:otp-send:ip:10.0.0.1"), eq(50L), any());
    }

    @Test
    void checkOtpSendThrowsWhenPhoneExceeded() {
        when(counter.tryAcquire(eq("ratelimit:otp-send:phone:+84900000004:login"), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.checkOtpSend("+84900000004", "login", "10.0.0.1", null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many OTP requests for this phone");
    }

    @Test
    void checkOtpSendThrowsWhenIpExceeded() {
        when(counter.tryAcquire(eq("ratelimit:otp-send:phone:+84900000004:login"), anyLong(), any()))
                .thenReturn(true);
        when(counter.tryAcquire(eq("ratelimit:otp-send:ip:10.0.0.1"), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.checkOtpSend("+84900000004", "login", "10.0.0.1", null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many OTP requests from this address");
    }

    @Test
    void checkOtpVerifyPassesUnderLimit() {
        when(counter.tryAcquire(any(), anyLong(), any())).thenReturn(true);

        service.checkOtpVerify("10.0.0.1", DEALERSHIP_ID);

        verify(counter).tryAcquire(eq("ratelimit:otp-verify:ip:10.0.0.1"), eq(50L), any());
    }

    @Test
    void checkOtpVerifyThrowsWhenIpExceeded() {
        when(counter.tryAcquire(eq("ratelimit:otp-verify:ip:10.0.0.1"), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.checkOtpVerify("10.0.0.1", null))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many verification requests");
    }

    @Test
    void checkRegisterPassesUnderLimit() {
        when(counter.tryAcquire(any(), anyLong(), any())).thenReturn(true);

        service.checkRegister("10.0.0.1");

        verify(counter).tryAcquire(eq("ratelimit:register:ip:10.0.0.1"), eq(3L), any());
    }

    @Test
    void checkRegisterThrowsWhenIpExceeded() {
        when(counter.tryAcquire(eq("ratelimit:register:ip:10.0.0.1"), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.checkRegister("10.0.0.1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many registration requests");
    }
}
