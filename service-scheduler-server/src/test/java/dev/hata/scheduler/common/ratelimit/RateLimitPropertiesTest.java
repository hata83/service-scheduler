package dev.hata.scheduler.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Covers the YAML-configured rate-limit properties and default policy fallbacks.
 */
class RateLimitPropertiesTest {

    @Test
    void resolvesConfiguredPolicyAndFallsBackToDefaults() {
        RateLimitProperties.Policy custom = new RateLimitProperties.Policy(7, Duration.ofSeconds(15));
        RateLimitProperties properties = new RateLimitProperties(true, Map.of("otp-send-phone", custom));

        assertThat(properties.trustForwardedFor()).isTrue();
        assertThat(properties.policy("otp-send-phone").limit()).isEqualTo(7);
        assertThat(properties.policy("otp-send-phone").window()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.policy("otp-send-ip").limit()).isEqualTo(30);
        assertThat(properties.policy("unknown-policy").limit()).isEqualTo(10);

        RateLimitProperties emptyProps = new RateLimitProperties(false, null);
        assertThat(emptyProps.policy("otp-verify-ip").limit()).isEqualTo(10);
    }
}
