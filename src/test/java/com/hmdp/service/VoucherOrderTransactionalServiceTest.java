package com.hmdp.service;

import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.impl.VoucherOrderTransactionalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherOrderTransactionalServiceTest {

    @Mock private VoucherOrderMapper voucherOrderMapper;
    @Mock private ISeckillVoucherService seckillVoucherService;
    @Mock private UpdateChainWrapper<SeckillVoucher> updateWrapper;
    @InjectMocks private VoucherOrderTransactionalService service;
    private VoucherOrder order;

    @BeforeEach
    void setUp() {
        order = new VoucherOrder().setId(1L).setUserId(2L).setVoucherId(3L);
    }

    @Test
    void shouldIgnoreDuplicateMessageWithoutDeductingStock() {
        when(voucherOrderMapper.selectCount(any())).thenReturn(1);

        service.createVoucherOrder(order);

        verify(seckillVoucherService, never()).update();
        verify(voucherOrderMapper, never()).insert(any());
    }

    @Test
    void shouldCreateOrderAfterAtomicStockDeduction() {
        when(voucherOrderMapper.selectCount(any())).thenReturn(0);
        when(seckillVoucherService.update()).thenReturn(updateWrapper);
        when(updateWrapper.setSql(anyString())).thenReturn(updateWrapper);
        when(updateWrapper.eq(anyString(), any())).thenReturn(updateWrapper);
        when(updateWrapper.gt(anyString(), any())).thenReturn(updateWrapper);
        when(updateWrapper.update()).thenReturn(true);

        service.createVoucherOrder(order);

        verify(voucherOrderMapper).insert(order);
    }

    @Test
    void shouldFailWhenDatabaseStockIsExhausted() {
        when(voucherOrderMapper.selectCount(any())).thenReturn(0);
        when(seckillVoucherService.update()).thenReturn(updateWrapper);
        when(updateWrapper.setSql(anyString())).thenReturn(updateWrapper);
        when(updateWrapper.eq(anyString(), any())).thenReturn(updateWrapper);
        when(updateWrapper.gt(anyString(), any())).thenReturn(updateWrapper);
        when(updateWrapper.update()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.createVoucherOrder(order));
        verify(voucherOrderMapper, never()).insert(any());
    }
}
