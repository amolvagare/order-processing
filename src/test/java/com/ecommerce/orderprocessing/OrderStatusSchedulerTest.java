package com.ecommerce.orderprocessing;

import com.ecommerce.orderprocessing.scheduler.OrderStatusScheduler;
import com.ecommerce.orderprocessing.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderStatusScheduler.
 * Verifies that the scheduler delegates correctly and handles errors gracefully.
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusSchedulerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderStatusScheduler scheduler;

    @Test
    void promote_pending_orders_should_invoke_service_once() {
        when(orderService.promote_pending_to_processing()).thenReturn(3);

        scheduler.promote_pending_orders();

        verify(orderService, times(1)).promote_pending_to_processing();
    }

    @Test
    void promote_pending_orders_should_handle_service_exception_without_rethrowing() {
        // If the service throws, the scheduler must not propagate the exception
        // (otherwise the scheduled thread would die and no future runs would occur)
        when(orderService.promote_pending_to_processing())
                .thenThrow(new RuntimeException("DB connection lost"));

        // Should complete without throwing
        scheduler.promote_pending_orders();

        verify(orderService, times(1)).promote_pending_to_processing();
    }
}
