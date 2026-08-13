package com.hmdp.mq;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderCompensationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class VoucherOrderDltConsumer {

    @Resource
    private VoucherOrderCompensationService compensationService;

    @KafkaListener(
            topics = "${hmdp.kafka.voucher-order-dlt-topic}",
            groupId = "voucher-order-dlt-group",
            containerFactory = "voucherOrderDltKafkaListenerContainerFactory"
    )
    public void consume(String message) {
        VoucherOrder order = JSONUtil.toBean(message, VoucherOrder.class);
        log.error("秒杀订单进入死信补偿流程，orderId={}", order.getId());
        compensationService.compensate(order);
    }
}
