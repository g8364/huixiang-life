package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.impl.VoucherOrderCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherOrderCompensationServiceTest {

    @Mock private VoucherOrderMapper voucherOrderMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @InjectMocks private VoucherOrderCompensationService service;
    private VoucherOrder order;

    @BeforeEach
    void setUp() {
        order = new VoucherOrder().setId(1L).setUserId(2L).setVoucherId(3L);
    }

    @Test
    void shouldNotCompensateWhenOrderAlreadyExists() {
        when(voucherOrderMapper.selectCount(any())).thenReturn(1);

        service.compensate(order);

        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    void shouldCompensateMissingOrder() {
        when(voucherOrderMapper.selectCount(any())).thenReturn(0);
        when(stringRedisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(1L);

        service.compensate(order);

        verify(stringRedisTemplate).execute(any(), anyList(), eq("3"), eq("2"));
    }

    @Test
    void shouldRetryWhenRedisDoesNotReturnResult() {
        when(voucherOrderMapper.selectCount(any())).thenReturn(0);
        when(stringRedisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.compensate(order));
    }
}
