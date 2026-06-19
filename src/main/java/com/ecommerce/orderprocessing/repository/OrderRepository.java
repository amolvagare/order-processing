package com.ecommerce.orderprocessing.repository;

import com.ecommerce.orderprocessing.enums.OrderStatus;
import com.ecommerce.orderprocessing.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Order entity.
 * Provides CRUD operations and a status-based lookup used by both the API and the scheduler.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Retrieve all orders matching the given status (used for filter and scheduler batch)
    List<Order> findByStatus(OrderStatus status);
}
