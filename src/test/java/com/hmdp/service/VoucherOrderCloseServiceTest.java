package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.impl.VoucherOrderCloseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherOrderCloseServiceTest {
    @Mock private VoucherOrderMapper voucherOrderMapper;
    @Mock private SeckillVoucherMapper seckillVoucherMapper;
    @Mock private RedisStockCompensationService redisStockCompensationService;
    @InjectMocks private VoucherOrderCloseService service;

    @Test
    void shouldRestoreStockWhenCloseWins() {
        VoucherOrder order = new VoucherOrder().setId(1L).setVoucherId(10L);
        when(voucherOrderMapper.closeExpiredOrder(eq(1L), any())).thenReturn(1);
        when(seckillVoucherMapper.updateStock(10L)).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(service.closeIfExpired(order, LocalDateTime.now()));
            verify(seckillVoucherMapper).updateStock(10L);
            verify(redisStockCompensationService, never()).restoreStock(anyLong());

            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(redisStockCompensationService).restoreStock(10L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldNotRestoreStockWhenPaymentWins() {
        VoucherOrder order = new VoucherOrder().setId(1L).setVoucherId(10L);
        when(voucherOrderMapper.closeExpiredOrder(eq(1L), any())).thenReturn(0);

        assertFalse(service.closeIfExpired(order, LocalDateTime.now()));
        verify(seckillVoucherMapper, never()).updateStock(anyLong());
        verify(redisStockCompensationService, never()).restoreStock(anyLong());
    }
}
