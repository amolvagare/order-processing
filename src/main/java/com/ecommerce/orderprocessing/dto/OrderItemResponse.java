package com.ecommerce.orderprocessing.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Response DTO for a single order line item.
 * Includes a computed subtotal (quantity * unitPrice).
 */
@Data
@Builder
public class OrderItemResponse {

    private Long id;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;

    // Computed server-side: quantity * unitPrice
    private BigDecimal subtotal;
}
