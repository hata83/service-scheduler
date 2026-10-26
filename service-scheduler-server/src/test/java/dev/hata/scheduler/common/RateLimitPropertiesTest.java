package dev.hata.scheduler.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Reads the bundled rate-limit JSON files (design D7). The loader resolves a
 * dealership block with the default block as fallback.
 */
class RateLimitPropertiesTest {

    @Test
    void readsTheDefaultBlock() {
        RateLimitProperties properties = new RateLimitProperties("classpath:config/rate-limits.json", false);

        RateLimitProperties.Resolved resolved = properties.resolve(null);

        assertThat(resolved.otpSendPerPhonePurpose()).isEqualTo(5);
        assertThat(resolved.otpSendPerIp()).isEqualTo(30);
        assertThat(resolved.otpVerifyPerIp()).isEqualTo(10);
        assertThat(resolved.registerPerIp()).isEqualTo(10);
    }

    @Test
    void readsTheTestProfileBlock() {
        RateLimitProperties properties = new RateLimitProperties("classpath:config/rate-limits-test.json", false);

        RateLimitProperties.Resolved resolved = properties.resolve(null);

        assertThat(resolved.otpSendPerPhonePurpose()).isEqualTo(5);
        assertThat(resolved.registerPerIp()).isEqualTo(3);
        assertThat(resolved.otpSendPerPhonePurposeWindow().toSeconds()).isEqualTo(10);
    }

    @Test
    void fallsBackToTheDefaultBlockForAnUnknownDealership() {
        RateLimitProperties properties = new RateLimitProperties("classpath:config/rate-limits.json", false);

        RateLimitProperties.Resolved resolved = properties.resolve("00000000-0000-0000-0000-000000000001");

        assertThat(resolved.otpSendPerPhonePurpose()).isEqualTo(5);
    }
}
