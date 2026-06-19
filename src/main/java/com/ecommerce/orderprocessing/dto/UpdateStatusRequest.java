package com.ecommerce.orderprocessing.dto;

import com.ecommerce.orderprocessing.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request payload for manually updating an order's status.
 */
@Data
public class UpdateStatusRequest {

    @NotNull(message = "Status is required")
    private OrderStatus status;
}
