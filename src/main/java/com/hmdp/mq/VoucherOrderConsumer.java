package com.hmdp.mq;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderTransactionalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class VoucherOrderConsumer {

    @Resource
    private VoucherOrderTransactionalService transactionalService;

    @KafkaListener(
            topics = "${hmdp.kafka.voucher-order-topic}",
            containerFactory = "voucherOrderKafkaListenerContainerFactory"
    )
    public void consume(String message) {
        VoucherOrder order = JSONUtil.toBean(message, VoucherOrder.class);
        log.info("消费秒杀订单消息，orderId={}", order.getId());
        transactionalService.createVoucherOrder(order);
    }
}
