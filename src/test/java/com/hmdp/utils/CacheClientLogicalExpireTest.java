package com.hmdp.utils;

import com.hmdp.entity.Shop;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CacheClientLogicalExpireTest {

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnFreshLogicalValueWithoutDatabaseAccess() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RedisData data = new RedisData();
        data.setData(new Shop().setId(1L).setName("fresh"));
        data.setExpireTime(LocalDateTime.now().plusMinutes(5));
        when(values.get("cache:shop:1")).thenReturn(cn.hutool.json.JSONUtil.toJsonStr(data));

        CacheClient client = new CacheClient(redis);
        AtomicInteger databaseCalls = new AtomicInteger();
        CacheClient.LogicalCacheResult<Shop> result = client.queryWithLogicalExpireResult(
                "cache:shop:", "lock:shop:", 1L, Shop.class,
                id -> { databaseCalls.incrementAndGet(); return null; },
                30, TimeUnit.MINUTES, null);

        assertFalse(result.isStale());
        assertEquals("fresh", result.getValue().getName());
        assertEquals(0, databaseCalls.get());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnStaleValueAndRebuildOnlyOnce() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RedisData data = new RedisData();
        data.setData(new Shop().setId(1L).setName("stale"));
        data.setExpireTime(LocalDateTime.now().minusSeconds(1));
        when(values.get("cache:shop:1")).thenReturn(cn.hutool.json.JSONUtil.toJsonStr(data));
        when(values.setIfAbsent(eq("lock:shop:1"), eq("1"), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(true, false);

        CacheClient client = new CacheClient(redis);
        AtomicInteger databaseCalls = new AtomicInteger();
        CountDownLatch rebuilt = new CountDownLatch(1);
        java.util.function.Function<Long, Shop> loader = id -> {
            databaseCalls.incrementAndGet();
            return new Shop().setId(id).setName("latest");
        };

        CacheClient.LogicalCacheResult<Shop> first = client.queryWithLogicalExpireResult(
                "cache:shop:", "lock:shop:", 1L, Shop.class, loader,
                30, TimeUnit.MINUTES, rebuilt::countDown);
        CacheClient.LogicalCacheResult<Shop> second = client.queryWithLogicalExpireResult(
                "cache:shop:", "lock:shop:", 1L, Shop.class, loader,
                30, TimeUnit.MINUTES, rebuilt::countDown);

        assertTrue(first.isStale());
        assertTrue(second.isStale());
        assertEquals("stale", first.getValue().getName());
        assertTrue(rebuilt.await(2, TimeUnit.SECONDS));
        assertEquals(1, databaseCalls.get());
        verify(values, times(1)).set(eq("cache:shop:1"), contains("latest"));
        verify(redis, times(1)).delete("lock:shop:1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldMigratePlainJsonToLogicalExpirationEnvelope() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("cache:shop:1"))
                .thenReturn(cn.hutool.json.JSONUtil.toJsonStr(new Shop().setId(1L).setName("legacy")));

        CacheClient client = new CacheClient(redis);
        CacheClient.LogicalCacheResult<Shop> result = client.queryWithLogicalExpireResult(
                "cache:shop:", "lock:shop:", 1L, Shop.class, id -> null,
                30, TimeUnit.MINUTES, null);

        assertFalse(result.isStale());
        assertEquals("legacy", result.getValue().getName());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("cache:shop:1"), json.capture());
        assertTrue(json.getValue().contains("expireTime"));
        assertTrue(json.getValue().contains("legacy"));
    }
}
