package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> SECKILL_ROLLBACK_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

        SECKILL_ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        SECKILL_ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_rollback.lua"));
        SECKILL_ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;
    @Resource
    private VoucherMapper voucherMapper;

    @Value("${hmdp.kafka.voucher-order-topic}")
    private String voucherOrderTopic;

    @Value("${hmdp.order.payment-timeout-minutes:15}")
    private long paymentTimeoutMinutes;

    @Override
    public Result setKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        if (result == null) {
            return Result.fail("秒杀服务暂时不可用");
        }
        if (result != 0L) {
            return Result.fail(result == 1L ? "库存不足" : "不能重复下单");
        }

        VoucherOrder order = new VoucherOrder();
        order.setId(redisIdWorker.nextId("order"));
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setCreateTime(LocalDateTime.now());

        try {
            kafkaTemplate.send(voucherOrderTopic, order.getId().toString(), JSONUtil.toJsonStr(order))
                    .get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("发送秒杀订单消息失败，orderId={}", order.getId(), e);
            stringRedisTemplate.execute(
                    SECKILL_ROLLBACK_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    userId.toString()
            );
            return Result.fail("下单请求提交失败，请重试");
        }
        // Return snowflake IDs as strings to prevent JavaScript precision loss.
        return Result.ok(order.getId().toString());
    }

    @Override
    public Result queryMyOrders(Integer current) {
        Long userId = UserHolder.getUser().getId();
        int pageNo = current == null || current < 1 ? 1 : current;
        Page<VoucherOrder> page = lambdaQuery()
                .eq(VoucherOrder::getUserId, userId)
                .orderByDesc(VoucherOrder::getCreateTime)
                .page(new Page<>(pageNo, 10));
        return Result.ok(toOrderDTOs(page.getRecords()), page.getTotal());
    }

    @Override
    public Result queryOrderById(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = lambdaQuery()
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getUserId, userId)
                .one();
        return order == null ? Result.fail("订单不存在") : Result.ok(toOrderDTOs(Collections.singletonList(order)).get(0));
    }

    @Override
    public Result payOrder(Long orderId, Integer payType) {
        if (payType == null || payType < 1 || payType > 3) {
            return Result.fail("不支持的支付方式");
        }
        Long userId = UserHolder.getUser().getId();
        LocalDateTime paymentDeadline = LocalDateTime.now().minusMinutes(paymentTimeoutMinutes);
        boolean paid = lambdaUpdate()
                .set(VoucherOrder::getStatus, 2)
                .set(VoucherOrder::getPayType, payType)
                .set(VoucherOrder::getPayTime, LocalDateTime.now())
                .eq(VoucherOrder::getId, orderId)
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getStatus, 1)
                .ge(VoucherOrder::getCreateTime, paymentDeadline)
                .update();
        if (paid) {
            return Result.ok();
        }
        VoucherOrder order = getById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            return Result.fail("订单不存在");
        }
        if (Integer.valueOf(2).equals(order.getStatus())) {
            return Result.fail("订单已支付，请勿重复支付");
        }
        if (Integer.valueOf(4).equals(order.getStatus())) {
            return Result.fail("订单已超时关闭");
        }
        if (Integer.valueOf(1).equals(order.getStatus())
                && order.getCreateTime().isBefore(paymentDeadline)) {
            return Result.fail("订单已超过支付时间，请等待系统关闭");
        }
        return Result.fail("当前订单状态不可支付");
    }

    private List<VoucherOrderDTO> toOrderDTOs(List<VoucherOrder> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> voucherIds = orders.stream().map(VoucherOrder::getVoucherId).distinct().collect(Collectors.toList());
        Map<Long, Voucher> voucherMap = voucherMapper.selectBatchIds(voucherIds).stream()
                .collect(Collectors.toMap(Voucher::getId, Function.identity()));
        return orders.stream().map(order -> {
            VoucherOrderDTO dto = new VoucherOrderDTO();
            dto.setId(order.getId().toString());
            dto.setVoucherId(order.getVoucherId());
            dto.setPayType(order.getPayType());
            dto.setStatus(order.getStatus());
            dto.setCreateTime(order.getCreateTime());
            dto.setPayTime(order.getPayTime());
            if (order.getCreateTime() != null) {
                dto.setPaymentDeadline(order.getCreateTime().plusMinutes(paymentTimeoutMinutes));
            }
            Voucher voucher = voucherMap.get(order.getVoucherId());
            if (voucher != null) {
                dto.setShopId(voucher.getShopId());
                dto.setVoucherTitle(voucher.getTitle());
                dto.setPayValue(voucher.getPayValue());
                dto.setActualValue(voucher.getActualValue());
            }
            return dto;
        }).collect(Collectors.toList());
    }
}
