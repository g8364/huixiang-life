package com.hmdp.config;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

@Component
@Slf4j
public class SeckillStockInitializer implements ApplicationRunner {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<SeckillVoucher> vouchers = seckillVoucherService.query()
                .gt("end_time", LocalDateTime.now())
                .list();
        for (SeckillVoucher voucher : vouchers) {
            String key = SECKILL_STOCK_KEY + voucher.getVoucherId();
            stringRedisTemplate.opsForValue().setIfAbsent(key, voucher.getStock().toString());
        }
        log.info("秒杀库存预热完成，voucherCount={}", vouchers.size());
    }
}
