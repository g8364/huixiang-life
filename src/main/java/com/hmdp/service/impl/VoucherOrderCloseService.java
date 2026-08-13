package com.hmdp.service.impl;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.RedisStockCompensationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderCloseService {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private RedisStockCompensationService redisStockCompensationService;

    @Transactional
    public boolean closeIfExpired(VoucherOrder order, LocalDateTime deadline) {
        int closed = voucherOrderMapper.closeExpiredOrder(order.getId(), deadline);
        if (closed == 0) {
            return false;
        }
        int restored = seckillVoucherMapper.updateStock(order.getVoucherId());
        if (restored != 1) {
            throw new IllegalStateException("恢复数据库库存失败，orderId=" + order.getId());
        }
        // Redis can only be restored after the database transaction commits.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                redisStockCompensationService.restoreStock(order.getVoucherId());
            }
        });
        return true;
    }
}
