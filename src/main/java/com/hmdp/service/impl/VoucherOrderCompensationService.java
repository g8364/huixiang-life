package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

@Service
@Slf4j
public class VoucherOrderCompensationService {

    private static final DefaultRedisScript<Long> SECKILL_ROLLBACK_SCRIPT;

    static {
        SECKILL_ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        SECKILL_ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_rollback.lua"));
        SECKILL_ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void compensate(VoucherOrder order) {
        Integer count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, order.getUserId())
                .eq(VoucherOrder::getVoucherId, order.getVoucherId()));
        if (count != null && count > 0) {
            log.info("数据库订单已存在，无需补偿，orderId={}", order.getId());
            return;
        }

        Long result = stringRedisTemplate.execute(
                SECKILL_ROLLBACK_SCRIPT,
                Collections.emptyList(),
                order.getVoucherId().toString(),
                order.getUserId().toString()
        );
        if (result == null) {
            throw new IllegalStateException("Redis 补偿执行失败，orderId=" + order.getId());
        }
        log.warn("秒杀资格补偿完成，orderId={}, restored={}", order.getId(), result == 1L);
    }
}
