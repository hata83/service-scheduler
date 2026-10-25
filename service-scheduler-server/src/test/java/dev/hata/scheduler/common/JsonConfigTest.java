package dev.hata.scheduler.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Covers the bundled JSON reader (design D7).
 */
class JsonConfigTest {

    @Test
    void readsAMap() {
        Map<String, Object> config = JsonConfig.readObject("config/rate-limits.json");

        assertThat(config).containsKey("default");
        assertThat(config.get("dealerships")).isInstanceOf(Map.class);
    }

    @Test
    void failsForAMissingResource() {
        assertThatThrownBy(() -> JsonConfig.readObject("config/missing-file.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read bundled JSON config");
    }
}
