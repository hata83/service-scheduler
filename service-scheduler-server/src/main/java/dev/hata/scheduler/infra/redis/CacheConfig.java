package dev.hata.scheduler.infra.redis;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Spring Cache abstraction configuration backed by Redis.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultCfg = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCfg)
                .withCacheConfiguration(
                        "slot-grids",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(1))
                                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer())))
                .withCacheConfiguration(
                        "dealership-config",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofDays(3))
                                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer())))
                .build();
    }

    private static GenericJacksonJsonRedisSerializer jsonSerializer() {
        // ponytail: unsafe default typing and null-value support mirror the deprecated
        // GenericJackson2JsonRedisSerializer defaults used by cached internal DTOs.
        // Upgrade path: enableDefaultTyping with a domain-restricted PolymorphicTypeValidator.
        return GenericJacksonJsonRedisSerializer.create(
                configurer -> configurer.enableUnsafeDefaultTyping().enableSpringCacheNullValueSupport());
    }
}
