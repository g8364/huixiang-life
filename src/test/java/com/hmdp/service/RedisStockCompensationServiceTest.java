package com.hmdp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisStockCompensationServiceTest {
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @InjectMocks private RedisStockCompensationService service;

    @Test
    void shouldIncrementRedisStock() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        service.restoreStock(10L);

        verify(valueOperations).increment("seckill:stock:10");
    }

    @Test
    void shouldRetryFailedCompensation() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis unavailable"))
                .doReturn(1L)
                .when(valueOperations).increment("seckill:stock:10");

        service.restoreStock(10L);
        service.retryPendingCompensations();

        verify(valueOperations, times(2)).increment("seckill:stock:10");
    }
}
