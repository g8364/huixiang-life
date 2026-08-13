package com.hmdp.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.impl.VoucherOrderCloseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Closes unpaid voucher orders after the configured payment window.
 * The SQL only changes status=1 rows, so it forms an optimistic lock with payment.
 */
@Component
@ConditionalOnProperty(name = "hmdp.order.close-task-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class VoucherOrderCloseTask {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private VoucherOrderCloseService voucherOrderCloseService;

    @Value("${hmdp.order.payment-timeout-minutes:15}")
    private long paymentTimeoutMinutes;

    @Scheduled(fixedDelayString = "${hmdp.order.close-fixed-delay-ms:30000}")
    public void closeExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(paymentTimeoutMinutes);
        List<VoucherOrder> expiredOrders = voucherOrderMapper.selectList(new QueryWrapper<VoucherOrder>()
                .eq("status", 1)
                .lt("create_time", deadline)
                .select("id", "voucher_id", "user_id", "status", "create_time"));
        int closed = 0;
        for (VoucherOrder order : expiredOrders) {
            if (voucherOrderCloseService.closeIfExpired(order, deadline)) {
                closed++;
            }
        }
        if (closed > 0) {
            log.info("关闭超时未支付订单，数量={}，支付截止时间={}", closed, deadline);
        }
    }
}
