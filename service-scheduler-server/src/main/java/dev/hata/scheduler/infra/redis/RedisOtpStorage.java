package dev.hata.scheduler.infra.redis;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

import dev.hata.scheduler.auth.application.AuthProperties;
import dev.hata.scheduler.auth.application.OtpStorage;
import dev.hata.scheduler.auth.domain.OtpPurpose;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis adapter for the OTP slot (design D7):
 * {@code otp:{slot}:{phone}} with a configurable TTL, one live code per slot,
 * and single-use consumption that deletes only on a match. The atomic
 * read-compare-delete runs in one server-side script, so two concurrent
 * verifications can never both consume one code, and no wrong guess wipes the
 * live code.
 */
@Component
public class RedisOtpStorage implements OtpStorage {

    /**
     * Returns true only when the slot holds ARGV[1]; the deletion happens in
     * the same script invocation, which makes the consume single use.
     */
    private static final DefaultRedisScript<Boolean> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local stored = redis.call('GET', KEYS[1]) " + "if stored == false then return false end "
                    + "if stored == ARGV[1] then redis.call('DEL', KEYS[1]) return true end "
                    + "return false",
            Boolean.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    /**
     * Duration for which the slot lives. Stored from {@link AuthProperties}.otp().ttl().
     */
    private final Duration expiry;

    RedisOtpStorage(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.expiry = properties.otp().ttl();
    }

    @Override
    public String save(String phone, OtpPurpose purpose) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redis.opsForValue().set(key(purpose, phone), code, expiry);
        return code;
    }

    @Override
    public boolean consume(String phone, OtpPurpose purpose, String candidate) {
        if (candidate == null) {
            return false;
        }
        Boolean matched = redis.execute(CONSUME_SCRIPT, List.of(key(purpose, phone)), candidate);
        return Boolean.TRUE.equals(matched);
    }

    @Override
    public Duration ttl() {
        return expiry;
    }

    private static String key(OtpPurpose purpose, String phone) {
        return "otp:" + purpose.value() + ":" + phone;
    }
}
