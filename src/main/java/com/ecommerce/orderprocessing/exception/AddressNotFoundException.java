package com.ecommerce.orderprocessing.exception;

/**
 * Thrown when an address is not found under a customer.
 */
public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(Long customer_id, Long address_id) {
        super("Address " + address_id + " not found for customer id: " + customer_id);
    }
}

