package dev.hata.scheduler.infra.redis;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Spring Cache abstraction configuration backed by Redis.
 *
 * <p>Cache reads and writes fail open: a Redis outage degrades to a cache miss
 * so callers fall back to the database instead of surfacing a 500.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultCfg = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCfg)
                .withCacheConfiguration(
                        "dealership-config",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(15))
                                .disableCachingNullValues()
                                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer())))
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler();
    }

    private static GenericJacksonJsonRedisSerializer jsonSerializer() {
        // Polymorphic typing is restricted to the scheduler domain on
        // deserialization so values planted in Redis cannot instantiate
        // arbitrary classes (Qodo #12 / Kilo CacheConfig:49).
        return GenericJacksonJsonRedisSerializer.create(
                configurer -> configurer.enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("dev.hata.scheduler.")
                        .allowIfSubType("java.util.")
                        .build()));
    }

    /**
     * Logs and swallows cache access failures so cache errors never propagate
     * to callers; a failed read behaves like a miss and a failed write or
     * eviction leaves the entry to expire naturally.
     */
    static final class FailOpenCacheErrorHandler implements CacheErrorHandler {

        @Override
        public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Cache {} get failed for key {}; treating as miss", cache.getName(), key, ex);
        }

        @Override
        public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
            log.warn("Cache {} put failed for key {}", cache.getName(), key, ex);
        }

        @Override
        public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Cache {} evict failed for key {}", cache.getName(), key, ex);
        }

        @Override
        public void handleCacheClearError(RuntimeException ex, Cache cache) {
            log.warn("Cache {} clear failed", cache.getName(), ex);
        }
    }
}
