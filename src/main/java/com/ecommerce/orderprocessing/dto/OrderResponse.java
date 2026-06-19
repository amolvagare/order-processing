package com.ecommerce.orderprocessing.dto;

import com.ecommerce.orderprocessing.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for an order.
 * Decouples the API contract from the internal JPA entity.
 */
@Data
@Builder
public class OrderResponse {

    private Long id;
    private Long customerId;
    private Long addressId;
    private String customerName;
    private String customerEmail;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
