package com.ecommerce.orderprocessing.scheduler;

import com.ecommerce.orderprocessing.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduler that automatically promotes PENDING orders to PROCESSING.
 * Runs every 5 minutes (300,000 ms fixed rate).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusScheduler {

    private final OrderService orderService;

    /**
     * Triggered every 5 minutes.
     * Delegates batch promotion logic to OrderService.
     * Exceptions are caught here to prevent scheduler thread from dying.
     */
    @Scheduled(fixedRate = 300_000)
    public void promote_pending_orders() {
        log.info("Scheduler triggered: promoting PENDING orders to PROCESSING");
        try {
            int count = orderService.promote_pending_to_processing();
            log.info("Scheduler completed: {} order(s) promoted to PROCESSING", count);
        } catch (Exception ex) {
            log.error("Scheduler error during order promotion: {}", ex.getMessage(), ex);
        }
    }
}
