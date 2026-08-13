package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
@Slf4j
public class VoucherOrderTransactionalService {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Transactional
    public void createVoucherOrder(VoucherOrder order) {
        Integer count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, order.getUserId())
                .eq(VoucherOrder::getVoucherId, order.getVoucherId()));
        if (count != null && count > 0) {
            log.info("秒杀订单已存在，忽略重复消息，orderId={}", order.getId());
            return;
        }

        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            throw new IllegalStateException("数据库秒杀库存不足，voucherId=" + order.getVoucherId());
        }

        // 并发重复消息即使同时通过前置查询，也会被数据库唯一索引拦截。
        // 异常必须继续抛出，使本事务中的库存扣减一并回滚。
        order.setStatus(1);
        order.setPayType(1);
        if (order.getCreateTime() == null) {
            order.setCreateTime(LocalDateTime.now());
        }
        voucherOrderMapper.insert(order);
    }
}
