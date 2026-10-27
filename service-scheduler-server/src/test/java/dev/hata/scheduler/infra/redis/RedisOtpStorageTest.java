package dev.hata.scheduler.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.hata.scheduler.common.otp.OtpStorage;
import dev.hata.scheduler.common.otp.OtpType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * OtpStorage contract against a real Redis 7 instance:
 * tenant-scoped keys, SecureRandom six-digit codes, configurable TTL,
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
        redisTemplate.delete(java.util.List.of(
                "otp:global:login:" + PHONE,
                "otp:global:register:" + PHONE,
                "otp:attempts:global:login:" + PHONE,
                "otp:attempts:global:register:" + PHONE,
                "otp:lock:global:login:" + PHONE,
                "otp:lock:global:register:" + PHONE,
                "otp:dealer-1:login:" + PHONE,
                "otp:attempts:dealer-1:login:" + PHONE,
                "otp:lock:dealer-1:login:" + PHONE));
    }

    @Test
    void storesLoginPurposeUnderItsOwnKey() {
        otpStorage.save(PHONE, OtpType.LOGIN, "123456", TTL);

        assertThat(redisTemplate.hasKey("otp:global:login:" + PHONE)).isTrue();
        assertThat(redisTemplate.opsForValue().get("otp:global:login:" + PHONE)).isEqualTo("123456");
        Long remaining = redisTemplate.getExpire("otp:global:login:" + PHONE);
        assertThat(remaining).isBetween(1L, 302L);
    }

    @Test
    void loginAndRegisterSlotsStaySeparate() {
        otpStorage.save(PHONE, OtpType.LOGIN, "111111", TTL);
        otpStorage.save(PHONE, OtpType.REGISTER, "222222", TTL);

        assertThat(redisTemplate.opsForValue().get("otp:global:login:" + PHONE)).isEqualTo("111111");
        assertThat(redisTemplate.opsForValue().get("otp:global:register:" + PHONE))
                .isEqualTo("222222");
    }

    @Test
    void tenantScopedSlotsStayIsolated() {
        otpStorage.save("dealer-1", PHONE, OtpType.LOGIN, "999888", TTL);

        assertThat(redisTemplate.opsForValue().get("otp:dealer-1:login:" + PHONE))
                .isEqualTo("999888");
        assertThat(otpStorage.get("dealer-1", PHONE, OtpType.LOGIN)).contains("999888");
        assertThat(otpStorage.get("global", PHONE, OtpType.LOGIN)).isEmpty();
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
        assertThat(redisTemplate.hasKey("otp:global:register:" + PHONE)).isFalse();
    }

    @Test
    void saveReplacesThePreviousCodeInTheSameSlot() {
        otpStorage.save(PHONE, OtpType.LOGIN, "666666", TTL);
        otpStorage.save(PHONE, OtpType.LOGIN, "777777", TTL);

        assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, "666666")).isFalse();
        assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, "777777")).isTrue();
    }

    @Test
    void getReadsTheStoredCodeAndFailedGuessesInvalidateAfterMaxAttempts() {
        otpStorage.save(PHONE, OtpType.LOGIN, "888888", TTL);

        assertThat(otpStorage.get(PHONE, OtpType.LOGIN)).contains("888888");
        assertThat(otpStorage.get(PHONE, OtpType.REGISTER)).isEmpty();

        for (int i = 0; i < RedisOtpStorage.MAX_FAILED_ATTEMPTS; i++) {
            assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, "000000")).isFalse();
        }
        assertThat(otpStorage.get(PHONE, OtpType.LOGIN)).isEmpty();
    }

    @Test
    void consumeNullCodeReturnsFalse() {
        assertThat(otpStorage.consume(PHONE, OtpType.LOGIN, null)).isFalse();
    }

    @Test
    @SuppressWarnings("PMD.CloseResource")
    void lockCoordinatesConcurrentSendsForSamePhoneAndType() throws InterruptedException {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String ownerToken = "thread-" + threadId;
                    if (otpStorage.tryAcquireLock(PHONE, OtpType.LOGIN, ownerToken)) {
                        successCount.incrementAndGet();
                        Thread.sleep(50);
                        otpStorage.save(PHONE, OtpType.LOGIN, "CODE" + threadId, TTL);
                        otpStorage.releaseLock(PHONE, OtpType.LOGIN, ownerToken);
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        String finalCode = redisTemplate.opsForValue().get("otp:global:login:" + PHONE);
        assertThat(finalCode).matches("CODE\\d+");
    }
}
