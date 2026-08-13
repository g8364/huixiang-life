package com.hmdp.service;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.CacheInvalidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheInvalidationPublisher {

    public static final String SHOP_CACHE = "shop";

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${hmdp.kafka.cache-invalidation-topic}")
    private String topic;

    public void publishShopInvalidation(Long shopId) {
        CacheInvalidationMessage message = new CacheInvalidationMessage(SHOP_CACHE, shopId);
        try {
            kafkaTemplate.send(topic, shopId.toString(), JSONUtil.toJsonStr(message))
                    .get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("缓存失效事件发送失败，需要人工告警处理，shopId={}", shopId, e);
        }
    }
}
