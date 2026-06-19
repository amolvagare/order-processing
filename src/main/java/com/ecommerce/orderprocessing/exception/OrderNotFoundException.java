package com.ecommerce.orderprocessing.exception;

/**
 * Thrown when an order with the specified ID cannot be found.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Order not found with id: " + orderId);
    }
}
