package com.ecommerce.orderprocessing.exception;

/**
 * Thrown when a customer with the specified ID cannot be found.
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customer_id) {
        super("Customer not found with id: " + customer_id);
    }
}

