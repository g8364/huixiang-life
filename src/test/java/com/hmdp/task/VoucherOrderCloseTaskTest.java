package com.hmdp.task;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.impl.VoucherOrderCloseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherOrderCloseTaskTest {

    @Mock private VoucherOrderMapper voucherOrderMapper;
    @Mock private VoucherOrderCloseService closeService;
    @InjectMocks private VoucherOrderCloseTask task;

    @Test
    void shouldCloseEveryExpiredUnpaidOrder() {
        ReflectionTestUtils.setField(task, "paymentTimeoutMinutes", 15L);
        VoucherOrder order = new VoucherOrder().setId(1L).setVoucherId(10L);
        when(voucherOrderMapper.selectList(any())).thenReturn(Collections.singletonList(order));
        when(closeService.closeIfExpired(eq(order), any(LocalDateTime.class))).thenReturn(true);

        task.closeExpiredOrders();

        verify(closeService).closeIfExpired(eq(order), any(LocalDateTime.class));
    }
}
