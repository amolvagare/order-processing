package com.ecommerce.orderprocessing.controller;

import com.ecommerce.orderprocessing.dto.*;
import com.ecommerce.orderprocessing.enums.OrderStatus;
import com.ecommerce.orderprocessing.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing all order management endpoints.
 * Base path: /api/orders
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Orders", description = "Order management API")
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/orders
     * Create a new order with one or more items.
     */
    @PostMapping
    @Operation(summary = "Create a new order")
    public ResponseEntity<OrderResponse> create_order(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/orders — payload: {}", request);
        OrderResponse response = orderService.create_order(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/orders/{id}
     * Retrieve a single order by its ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<OrderResponse> get_order(@PathVariable Long id) {
        log.info("GET /api/orders/{}", id);
        return ResponseEntity.ok(orderService.get_order_by_id(id));
    }

    /**
     * GET /api/orders?status={status}
     * List all orders, optionally filtered by status.
     */
    @GetMapping
    @Operation(summary = "List all orders (optional status filter)")
    public ResponseEntity<List<OrderResponse>> list_orders(
            @RequestParam(required = false) OrderStatus status) {
        log.info("GET /api/orders — status filter: {}", status);
        return ResponseEntity.ok(orderService.list_orders(Optional.ofNullable(status)));
    }

    /**
     * PUT /api/orders/{id}/status
     * Manually update the status of an existing order.
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Update order status")
    public ResponseEntity<OrderResponse> update_status(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        log.info("PUT /api/orders/{}/status — payload: {}", id, request);
        return ResponseEntity.ok(orderService.update_order_status(id, request.getStatus()));
    }

    /**
     * DELETE /api/orders/{id}
     * Cancel an order — only allowed when current status is PENDING.
     * Returns 200 with the updated order showing CANCELLED status.
     * Returns 400 if the order is not in PENDING status.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order (PENDING only)")
    public ResponseEntity<OrderResponse> cancel_order(@PathVariable Long id) {
        log.info("DELETE /api/orders/{}", id);
        return ResponseEntity.ok(orderService.cancel_order(id));
    }
}
