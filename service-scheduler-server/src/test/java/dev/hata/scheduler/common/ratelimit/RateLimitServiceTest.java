package dev.hata.scheduler.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;

import dev.hata.scheduler.auth.dto.OtpSendRequest;
import dev.hata.scheduler.common.exception.AuthException;
import dev.hata.scheduler.common.filter.ClientIpResolver;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private WindowCounter counter;

    @Mock
    private ClientIpResolver ipResolver;

    private RateLimitProperties properties;
    private RateLimitService service;
    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties(
                false,
                Map.of(
                        "otp-send-phone", new RateLimitProperties.Policy(5, Duration.ofSeconds(10)),
                        "otp-send-ip", new RateLimitProperties.Policy(50, Duration.ofSeconds(10))));
        service = new RateLimitService(counter, properties);
        aspect = new RateLimitAspect(service, ipResolver);
    }

    @Test
    void checkLimitAcquiresWindowAndThrowsOnExhaustion() {
        when(counter.tryAcquire(eq("ratelimit:otp-send-phone:login:+84900000004"), eq(5L), any()))
                .thenReturn(true);

        service.checkLimit("otp-send-phone", "login:+84900000004", "Too many OTP requests");

        verify(counter).tryAcquire(eq("ratelimit:otp-send-phone:login:+84900000004"), eq(5L), any());

        when(counter.tryAcquire(eq("ratelimit:otp-send-ip:10.0.0.1"), anyLong(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.checkLimit("otp-send-ip", "10.0.0.1", "Too many IP requests"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many IP requests");
    }

    @Test
    void aspectEnforcesPayloadAndIpRules() throws Exception {
        when(counter.tryAcquire(any(), anyLong(), any())).thenReturn(true);
        when(ipResolver.resolve(any())).thenReturn("10.0.0.1");

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            Method method =
                    SampleEndpoint.class.getDeclaredMethod("handle", OtpSendRequest.class, HttpServletRequest.class);
            RateLimited annotation = method.getAnnotation(RateLimited.class);
            JoinPoint joinPoint = mock(JoinPoint.class);
            MethodSignature signature = mock(MethodSignature.class);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getMethod()).thenReturn(method);
            when(joinPoint.getArgs()).thenReturn(new Object[] {new OtpSendRequest("+84900000004", "LOGIN"), request});

            aspect.enforceRateLimits(joinPoint, annotation);

            verify(counter).tryAcquire(eq("ratelimit:otp-send-phone:login:+84900000004"), eq(5L), any());
            verify(counter).tryAcquire(eq("ratelimit:otp-send-ip:10.0.0.1"), eq(50L), any());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    static class SampleEndpoint {
        @RateLimited({
            @RateLimited.Rule(
                    policy = "otp-send-phone",
                    keyExpression = "#body.type().toLowerCase() + ':' + #body.phone()"),
            @RateLimited.Rule(policy = "otp-send-ip")
        })
        void handle(OtpSendRequest body, HttpServletRequest request) {}
    }
}
