package dev.hata.scheduler.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hata.scheduler.infra.redis.CacheConfig.FailOpenCacheErrorHandler;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

class CacheConfigTest {

    private final Cache cache = new ConcurrentMapCache("test-cache");
    private final FailOpenCacheErrorHandler handler = new FailOpenCacheErrorHandler();

    @Test
    void errorHandlerIsFailOpen() {
        assertThat(new CacheConfig().errorHandler()).isInstanceOf(FailOpenCacheErrorHandler.class);
    }

    @Test
    void getErrorIsSwallowed() {
        assertThatCode(() -> handler.handleCacheGetError(new RuntimeException("redis down"), cache, "key"))
                .doesNotThrowAnyException();
    }

    @Test
    void putErrorIsSwallowed() {
        assertThatCode(() -> handler.handleCachePutError(new RuntimeException("redis down"), cache, "key", "value"))
                .doesNotThrowAnyException();
    }

    @Test
    void evictErrorIsSwallowed() {
        assertThatCode(() -> handler.handleCacheEvictError(new RuntimeException("redis down"), cache, "key"))
                .doesNotThrowAnyException();
    }

    @Test
    void clearErrorIsSwallowed() {
        assertThatCode(() -> handler.handleCacheClearError(new RuntimeException("redis down"), cache))
                .doesNotThrowAnyException();
    }
}
