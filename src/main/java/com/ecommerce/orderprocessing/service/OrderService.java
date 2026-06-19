package com.ecommerce.orderprocessing.service;

import com.ecommerce.orderprocessing.dto.*;
import com.ecommerce.orderprocessing.enums.OrderStatus;
import com.ecommerce.orderprocessing.exception.AddressNotFoundException;
import com.ecommerce.orderprocessing.exception.CustomerNotFoundException;
import com.ecommerce.orderprocessing.exception.OrderCancellationException;

import com.ecommerce.orderprocessing.exception.OrderNotFoundException;
import com.ecommerce.orderprocessing.model.Customer;
import com.ecommerce.orderprocessing.model.CustomerAddress;
import com.ecommerce.orderprocessing.model.Order;
import com.ecommerce.orderprocessing.model.OrderItem;
import com.ecommerce.orderprocessing.repository.CustomerAddressRepository;
import com.ecommerce.orderprocessing.repository.CustomerRepository;
import com.ecommerce.orderprocessing.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service layer containing all order business logic.
 * Each method has a single responsibility and delegates persistence to OrderRepository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;

    /**
     * Create a new order with the provided items.
     * Total amount is computed server-side as sum(quantity * unitPrice).
     */
    @Transactional
    public OrderResponse create_order(CreateOrderRequest request) {
        log.debug("Creating order — customer_id: {}, address_id: {}", request.getCustomerId(), request.getAddressId());
        Customer customer = find_customer_or_throw(request.getCustomerId());
        CustomerAddress customer_address = find_customer_address_or_throw(request.getCustomerId(), request.getAddressId());

        // Map request items to entity items
        List<OrderItem> items = request.getItems().stream()
                .map(this::map_to_order_item)
                .collect(Collectors.toList());

        // Compute total amount server-side
        BigDecimal total = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Build order entity and link items
        Order order = Order.builder()
                .customer(customer)
                .customerAddress(customer_address)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .build();

        items.forEach(item -> item.setOrder(order));
        order.getItems().addAll(items);

        Order saved = orderRepository.save(order);
        log.debug("Order created — id: {}", saved.getId());
        return map_to_response(saved);
    }

    /**
     * Retrieve a single order by ID.
     */
    @Transactional(readOnly = true)
    public OrderResponse get_order_by_id(Long orderId) {
        log.debug("Fetching order — id: {}", orderId);
        Order order = find_order_or_throw(orderId);
        return map_to_response(order);
    }

    /**
     * List all orders, optionally filtered by status.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> list_orders(Optional<OrderStatus> status) {
        log.debug("Listing orders — status filter: {}", status.orElse(null));
        List<Order> orders = status
                .map(orderRepository::findByStatus)
                .orElseGet(orderRepository::findAll);
        return orders.stream().map(this::map_to_response).collect(Collectors.toList());
    }

    /**
     * Manually update the status of an order.
     */
    @Transactional
    public OrderResponse update_order_status(Long orderId, OrderStatus newStatus) {
        log.debug("Updating status — orderId: {}, newStatus: {}", orderId, newStatus);
        Order order = find_order_or_throw(orderId);

        // Guard: status endpoint can cancel only when current status is PENDING
        if (newStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.PENDING) {
            throw new OrderCancellationException(orderId, order.getStatus());
        }

        order.setStatus(newStatus);
        return map_to_response(orderRepository.save(order));
    }

    /**
     * Cancel an order — only allowed when current status is PENDING.
     */
    @Transactional
    public OrderResponse cancel_order(Long orderId) {
        log.debug("Cancelling order — id: {}", orderId);
        Order order = find_order_or_throw(orderId);

        // Guard: only PENDING orders can be cancelled
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderCancellationException(orderId, order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order cancelled = orderRepository.save(order);
        log.debug("Order {} successfully cancelled", orderId);
        return map_to_response(cancelled);
    }

    /**
     * Batch-promote all PENDING orders to PROCESSING.
     * Called by the background scheduler every 5 minutes.
     * Returns the number of orders promoted.
     */
    @Transactional
    public int promote_pending_to_processing() {
        log.debug("Scheduler: fetching all PENDING orders for promotion");
        List<Order> pending_orders = orderRepository.findByStatus(OrderStatus.PENDING);
        pending_orders.forEach(order -> order.setStatus(OrderStatus.PROCESSING));
        orderRepository.saveAll(pending_orders);
        log.debug("Scheduler: promoted {} order(s) to PROCESSING", pending_orders.size());
        return pending_orders.size();
    }

    // --- Private helpers ---

    /**
     * Find an order by id or throw a domain exception.
     */
    private Order find_order_or_throw(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Find customer by id or throw a domain exception.
     */
    private Customer find_customer_or_throw(Long customer_id) {
        return customerRepository.findById(customer_id)
                .orElseThrow(() -> new CustomerNotFoundException(customer_id));
    }

    /**
     * Find address by id and customer relation or throw a domain exception.
     */
    private CustomerAddress find_customer_address_or_throw(Long customer_id, Long address_id) {
        return customerAddressRepository.findByIdAndCustomerIdAndDeletedFalse(address_id, customer_id)
                .orElseThrow(() -> new AddressNotFoundException(customer_id, address_id));
    }

    /**
     * Map one item request into an order item entity.
     */
    private OrderItem map_to_order_item(OrderItemRequest req) {
        return OrderItem.builder()
                .productName(req.getProductName())
                .quantity(req.getQuantity())
                .unitPrice(req.getUnitPrice())
                .build();
    }

    /**
     * Map an order entity to API response shape.
     */
    private OrderResponse map_to_response(Order order) {
        List<OrderItemResponse> item_responses = order.getItems().stream()
                .map(this::map_item_to_response)
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomer().getId())
                .addressId(order.getCustomerAddress().getId())
                .customerName(order.getCustomer().getName())
                .customerEmail(order.getCustomer().getEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(item_responses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    /**
     * Map an order item entity to API response shape.
     */
    private OrderItemResponse map_item_to_response(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .build();
    }
}
