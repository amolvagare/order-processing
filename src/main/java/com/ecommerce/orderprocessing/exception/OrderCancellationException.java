package com.ecommerce.orderprocessing.exception;

import com.ecommerce.orderprocessing.enums.OrderStatus;

/**
 * Thrown when a cancellation is attempted on an order that is not in PENDING status.
 */
public class OrderCancellationException extends RuntimeException {

    public OrderCancellationException(Long orderId, OrderStatus currentStatus) {
        super("Cannot cancel order " + orderId + " with status: " + currentStatus
                + ". Only PENDING orders can be cancelled.");
    }
}
