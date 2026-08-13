package com.hmdp.mapper;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    @Update("UPDATE tb_voucher_order SET status = 4, update_time = NOW() " +
            "WHERE id = #{orderId} AND status = 1 AND create_time < #{deadline}")
    int closeExpiredOrder(@Param("orderId") Long orderId,
                          @Param("deadline") LocalDateTime deadline);
}
