package com.ecommerce.orderprocessing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request payload for creating a new order.
 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "Customer id is required")
    private Long customerId;

    @NotNull(message = "Address id is required")
    private Long addressId;

    // At least one item must be present; each item is individually validated
    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<OrderItemRequest> items;
}
