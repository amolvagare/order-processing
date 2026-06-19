package com.ecommerce.orderprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the E-commerce Order Processing System.
 * Scheduling is enabled to support the background job that promotes PENDING orders.
 */
@SpringBootApplication
@EnableScheduling
public class OrderProcessingApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessingApplication.class, args);
    }
}
