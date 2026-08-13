package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.entity.Shop;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalCacheConfigTest {

    @Test
    void shouldEvictWhenMaximumSizeIsExceeded() {
        Cache<Long, Shop> cache = new LocalCacheConfig().shopLocalCache(1, 5);
        cache.put(1L, new Shop().setId(1L));
        cache.put(2L, new Shop().setId(2L));
        cache.cleanUp();

        assertTrue(cache.estimatedSize() <= 1);
        assertNotNull(cache.getIfPresent(2L));
    }
}
