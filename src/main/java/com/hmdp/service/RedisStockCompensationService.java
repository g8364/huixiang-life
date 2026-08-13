package com.hmdp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@Slf4j
public class RedisStockCompensationService {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private final Queue<Long> pendingVoucherIds = new ConcurrentLinkedQueue<>();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void restoreStock(Long voucherId) {
        try {
            stringRedisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + voucherId);
            log.info("恢复 Redis 秒杀库存成功，voucherId={}", voucherId);
        } catch (RuntimeException e) {
            pendingVoucherIds.offer(voucherId);
            log.error("恢复 Redis 秒杀库存失败，已加入重试队列，voucherId={}", voucherId, e);
        }
    }

    @Scheduled(fixedDelay = 5000L)
    public void retryPendingCompensations() {
        int attempts = pendingVoucherIds.size();
        for (int i = 0; i < attempts; i++) {
            Long voucherId = pendingVoucherIds.poll();
            if (voucherId != null) {
                restoreStock(voucherId);
            }
        }
    }
}
