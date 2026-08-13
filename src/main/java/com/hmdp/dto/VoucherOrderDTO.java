package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VoucherOrderDTO {
    /** JavaScript cannot safely represent 64-bit snowflake IDs, so expose it as text. */
    private String id;
    private Long voucherId;
    private Long shopId;
    private String voucherTitle;
    private Long payValue;
    private Long actualValue;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime paymentDeadline;
}
