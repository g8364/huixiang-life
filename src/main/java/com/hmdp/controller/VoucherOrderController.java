package com.hmdp.controller;


import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.Result;
import com.hmdp.enums.RateLimitDimension;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

   @Autowired
   private IVoucherOrderService voucherOrderService;
    /**
     * 优惠券抢购
     * http://localhost:8080/api/voucher-order/seckill/11
     */
    @PostMapping("seckill/{id}")
    @RateLimit(key = "voucher-order:seckill", count = 5, windowSeconds = 1,
            dimensions = {RateLimitDimension.USER, RateLimitDimension.IP},
            message = "秒杀请求过于频繁，请稍后再试")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {


        return voucherOrderService.setKillVoucher(voucherId);

    }

    @GetMapping("/my")
    public Result queryMyOrders(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return voucherOrderService.queryMyOrders(current);
    }

    @GetMapping("/{id}")
    public Result queryOrderById(@PathVariable("id") Long orderId) {
        return voucherOrderService.queryOrderById(orderId);
    }

    /**
     * 模拟支付回调。当前项目不接真实支付渠道，只验证订单状态流转与并发安全。
     */
    @PostMapping("/{id}/pay")
    public Result payOrder(@PathVariable("id") Long orderId,
                           @RequestParam(value = "payType", defaultValue = "1") Integer payType) {
        return voucherOrderService.payOrder(orderId, payType);
    }
}
