package dev.hata.scheduler.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import dev.hata.scheduler.common.otp.OtpStorage;
import dev.hata.scheduler.common.otp.OtpType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * OtpStorage contract against a real Redis 7 instance:
 * type-scoped keys, SecureRandom six-digit codes, configurable TTL,
 * atomic single-use consumption, and constant-time comparison (design D7).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisOtpStorageTest {

    private static final String PHONE = "+84900000004";
    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired
    private OtpStorage otpStorage;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @org.junit.jupiter.api.BeforeEach
    void clean() {
        redisTemplate.delete(java.util.List.of("otp:login:" + PHONE, "otp:register:" + PHONE));
    }

    @Test
    void storesLoginPurposeUnderItsOwnKey() {
        otpStorage.save(PHONE, OtpType.LOGIN, "123456", TTL);

        assertThat(redisTemplate.hasKey("otp:login:" + PHONE)).isTrue();
        assertThat(redisTemplate.opsForValue().get("otp:login:" + PHONE)).isEqualTo("123456");
        Long remaining = redisTemplate.getExpire("otp:login:" + PHONE);
        assertThat(remaining).isBetween(1L, 302L);
    }

    @Test
    void loginAndRegisterSlotsStaySeparate() {
        otpStorage.save(PHONE, OtpType.LOGIN, "111111", TTL);
        otpStorage.save(PHONE, OtpType.REGISTER, "222222", TTL);

        assertThat(redisTemplate.opsForValue().get("otp:login:" + PHONE)).isEqualTo("111111");
        assertThat(redisTemplate.opsForValue().get("otp:register:" + PHONE)).isEqualTo("222222");
    }

    @Test
    void consumeValidatesOnlyTheMatchingCode() {
        otpStorage.save(PHONE, OtpType.LOGIN, "333333", TTL);

        assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, "000000")).isFalse();
        assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, "333333")).isTrue();

        otpStorage.save(PHONE, OtpType.LOGIN, "444444", TTL);
        assertThat(otpStorage.consume(PHONE, OtpType.REGISTER, "444444")).isFalse();
        assertThat(otpStorage.consume("+84900000999", OtpType.LOGIN, "444444")).isFalse();
        assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, "444444")).isTrue();
    }

    @Test
    void consumeIsSingleUse() {
        otpStorage.save(PHONE, OtpType.REGISTER, "555555", TTL);

        assertThat(otpStorage.consume(PHONE, OtpType.REGISTER, "555555")).isTrue();
        assertThat(otpStorage.consume(PHONE, OtpType.REGISTER, "555555")).isFalse();
        assertThat(redisTemplate.hasKey("otp:register:" + PHONE)).isFalse();
    }

    @Test
    void saveReplacesThePreviousCodeInTheSameSlot() {
        otpStorage.save(PHONE, OtpType.LOGIN, "666666", TTL);
        otpStorage.save(PHONE, OtpType.LOGIN, "777777", TTL);

        assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, "666666")).isFalse();
        assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, "777777")).isTrue();
    }

    @Test
    void getReadsTheStoredCode() {
        otpStorage.save(PHONE, OtpType.LOGIN, "888888", TTL);

        assertThat(otpStorage.get(PHONE, OtpType.LOGIN)).contains("888888");
        assertThat(otpStorage.get(PHONE, OtpType.REGISTER)).isEmpty();
    }
}
