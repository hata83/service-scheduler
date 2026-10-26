package dev.hata.scheduler.common.config;

import java.io.InputStream;
import java.util.Map;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a bundled JSON resource. The rate-limit loader uses this helper
 * (design D7), so the limits stay in a file instead of Java constants.
 */
public final class JsonConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DefaultResourceLoader LOADER = new DefaultResourceLoader();

    private JsonConfig() {}

    public static Map<String, Object> readObject(String location) {
        Resource resource = LOADER.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Cannot read bundled JSON config " + location + " at " + resource.getDescription(), ex);
        }
    }
}
