package dev.hata.scheduler.infra.redis;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.hata.scheduler.common.otp.OtpStorage;
import dev.hata.scheduler.common.otp.OtpType;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis adapter for OTP storage (design D7):
 * {@code otp:{type}:{phone}} with a configurable TTL, one live code per slot,
 * and single-use consumption that deletes only on a match.
 */
@Component
public class RedisOtpStorage implements OtpStorage {

    private static final DefaultRedisScript<Boolean> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local stored = redis.call('GET', KEYS[1]) " + "if stored == false then return false end "
                    + "if stored == ARGV[1] then redis.call('DEL', KEYS[1]) return true end "
                    + "return false",
            Boolean.class);

    private final StringRedisTemplate redis;

    public RedisOtpStorage(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String phone, OtpType type, String code, Duration ttl) {
        redis.opsForValue().set(key(type, phone), code, ttl);
    }

    @Override
    public boolean consume(String phone, OtpType type, String code) {
        if (code == null) {
            return false;
        }
        Boolean matched = redis.execute(CONSUME_SCRIPT, List.of(key(type, phone)), code);
        return Boolean.TRUE.equals(matched);
    }

    @Override
    public Optional<String> get(String phone, OtpType type) {
        return Optional.ofNullable(redis.opsForValue().get(key(type, phone)));
    }

    private static String key(OtpType type, String phone) {
        return "otp:" + type.value() + ":" + phone;
    }
}
