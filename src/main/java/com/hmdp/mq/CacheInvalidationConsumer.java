package com.hmdp.mq;

import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.dto.CacheInvalidationMessage;
import com.hmdp.entity.Shop;
import com.hmdp.service.CacheInvalidationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Component
@Slf4j
public class CacheInvalidationConsumer {

    @Resource
    private Cache<Long, Shop> shopLocalCache;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @KafkaListener(
            topics = "${hmdp.kafka.cache-invalidation-topic}",
            groupId = "${spring.application.name}-cache-invalidation-${random.uuid}",
            containerFactory = "cacheInvalidationKafkaListenerContainerFactory"
    )
    public void consume(String payload) {
        CacheInvalidationMessage message = JSONUtil.toBean(payload, CacheInvalidationMessage.class);
        if (!CacheInvalidationPublisher.SHOP_CACHE.equals(message.getCacheName()) || message.getId() == null) {
            log.warn("忽略未知缓存失效事件，payload={}", payload);
            return;
        }

        shopLocalCache.invalidate(message.getId());
        Boolean deleted = stringRedisTemplate.delete(CACHE_SHOP_KEY + message.getId());
        log.info("商户两级缓存失效完成，shopId={}, redisDeleted={}", message.getId(), deleted);
    }
}
