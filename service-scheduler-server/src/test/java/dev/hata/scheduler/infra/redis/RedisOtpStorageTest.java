package dev.hata.scheduler.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import dev.hata.scheduler.auth.application.OtpStorage;
import dev.hata.scheduler.auth.domain.OtpPurpose;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * OtpStorage contract against a real Redis 7 instance (compose stack):
 * purpose-scoped keys, SecureRandom six-digit codes, configurable TTL,
 * atomic single-use consumption, and constant-time comparison
 * (design D7).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OtpStorageTest {

    private static final String PHONE = "+84900000004";

    @Autowired
    private OtpStorage otpStorage;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void storesLoginPurposeUnderItsOwnKey() {
        String code = otpStorage.save(PHONE, OtpPurpose.LOGIN);

        assertThat(code).matches("\\d{6}");
        assertThat(redisTemplate.hasKey("otp:login:" + PHONE)).isTrue();
        assertThat(redisTemplate.opsForValue().get("otp:login:" + PHONE)).isEqualTo(code);
        Long ttl = redisTemplate.getExpire("otp:login:" + PHONE);
        assertThat(ttl).isBetween(1L, 602L);
    }

    @Test
    void loginAndRegisterSlotsStaySeparate() {
        String loginCode = otpStorage.save(PHONE, OtpPurpose.LOGIN);
        String registerCode = otpStorage.save(PHONE, OtpPurpose.REGISTER);

        assertThat(redisTemplate.opsForValue().get("otp:login:" + PHONE)).isEqualTo(loginCode);
        assertThat(redisTemplate.opsForValue().get("otp:register:" + PHONE)).isEqualTo(registerCode);
        assertThat(loginCode).isNotEqualTo(registerCode);
    }

    @Test
    void consumeValidatesOnlyTheMatchingCode() {
        String code = otpStorage.save(PHONE, OtpPurpose.LOGIN);

        assertThat(otpStorage.consume(PHONE, OtpPurpose.LOGIN, "000000")).isFalse();
        assertThat(otpStorage.consume(PHONE, OtpPurpose.LOGIN, code)).isTrue();

        String fresh = otpStorage.save(PHONE, OtpPurpose.LOGIN);
        assertThat(otpStorage.consume(PHONE, OtpPurpose.REGISTER, fresh)).isFalse();
        assertThat(otpStorage.consume("+84900000999", OtpPurpose.LOGIN, fresh)).isFalse();
        assertThat(otpStorage.consume(PHONE, OtpPurpose.LOGIN, fresh)).isTrue();
    }

    @Test
    void consumeIsSingleUse() {
        String code = otpStorage.save(PHONE, OtpPurpose.REGISTER);

        assertThat(otpStorage.consume(PHONE, OtpPurpose.REGISTER, code)).isTrue();
        assertThat(otpStorage.consume(PHONE, OtpPurpose.REGISTER, code)).isFalse();
        assertThat(redisTemplate.hasKey("otp:register:" + PHONE)).isFalse();
    }

    @Test
    void saveReplacesThePreviousCodeInTheSameSlot() {
        String first = otpStorage.save(PHONE, OtpPurpose.LOGIN);
        String second = otpStorage.save(PHONE, OtpPurpose.LOGIN);

        assertThat(first).isNotEqualTo(second);
        assertThat(otpStorage.consume(PHONE, OtpPurpose.LOGIN, first)).isFalse();
        assertThat(otpStorage.consume(PHONE, OtpPurpose.LOGIN, second)).isTrue();
    }

    @Test
    void ttlComesFromConfiguration() {
        otpStorage.save(PHONE, OtpPurpose.LOGIN);

        Long ttl = redisTemplate.getExpire("otp:login:" + PHONE);
        assertThat(ttl).isGreaterThan(0L);
        assertThat(Duration.ofSeconds(ttl)).isLessThan(Duration.parse("PT25H"));
    }
}
