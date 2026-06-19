package com.ecommerce.orderprocessing;

import com.ecommerce.orderprocessing.controller.OrderController;
import com.ecommerce.orderprocessing.dto.*;
import com.ecommerce.orderprocessing.enums.OrderStatus;
import com.ecommerce.orderprocessing.exception.GlobalExceptionHandler;
import com.ecommerce.orderprocessing.exception.OrderCancellationException;
import com.ecommerce.orderprocessing.exception.OrderNotFoundException;
import com.ecommerce.orderprocessing.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for OrderController.
 * OrderService is mocked — only the HTTP layer is exercised.
 */
@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private OrderResponse sample_response;

    @BeforeEach
    void setUp() {
        OrderItemResponse item_resp = OrderItemResponse.builder()
                .id(1L)
                .productName("Widget")
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .subtotal(new BigDecimal("20.00"))
                .build();

        sample_response = OrderResponse.builder()
                .id(1L)
                .customerId(100L)
                .addressId(200L)
                .customerName("Alice")
                .customerEmail("alice@example.com")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("20.00"))
                .items(List.of(item_resp))
                .build();
    }

    @Test
    void create_order_should_return_201_with_pending_status() throws Exception {
        when(orderService.create_order(any(CreateOrderRequest.class))).thenReturn(sample_response);

        String payload = """
                {
                  "customerId": 100,
                  "addressId": 200,
                  "items": [
                    { "productName": "Widget", "quantity": 2, "unitPrice": 10.00 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value(100))
                .andExpect(jsonPath("$.addressId").value(200))
                .andExpect(jsonPath("$.customerEmail").value("alice@example.com"))
                .andExpect(jsonPath("$.totalAmount").value(20.00));
    }

    @Test
    void create_order_should_return_400_when_payload_is_invalid() throws Exception {
        // Missing required ids and empty items list should fail validation
        String payload = """
                { "items": [] }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void get_order_should_return_200_when_found() throws Exception {
        when(orderService.get_order_by_id(1L)).thenReturn(sample_response);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerName").value("Alice"));
    }

    @Test
    void get_order_should_return_404_when_not_found() throws Exception {
        when(orderService.get_order_by_id(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("99")));
    }

    @Test
    void list_orders_should_return_all_when_no_filter() throws Exception {
        when(orderService.list_orders(Optional.empty())).thenReturn(List.of(sample_response));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void list_orders_should_filter_by_status() throws Exception {
        when(orderService.list_orders(Optional.of(OrderStatus.PENDING))).thenReturn(List.of(sample_response));

        mockMvc.perform(get("/api/orders").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void update_status_should_return_200_with_updated_status() throws Exception {
        OrderResponse shipped = OrderResponse.builder()
                .id(1L)
                .customerId(100L)
                .addressId(200L)
                .customerName("Alice")
                .customerEmail("alice@example.com")
                .status(OrderStatus.SHIPPED)
                .totalAmount(new BigDecimal("20.00"))
                .items(List.of())
                .build();

        when(orderService.update_order_status(eq(1L), eq(OrderStatus.SHIPPED))).thenReturn(shipped);

        mockMvc.perform(put("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"status\": \"SHIPPED\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void update_status_should_return_400_when_cancelling_non_pending_order() throws Exception {
        when(orderService.update_order_status(eq(1L), eq(OrderStatus.CANCELLED)))
                .thenThrow(new OrderCancellationException(1L, OrderStatus.PROCESSING));

        mockMvc.perform(put("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"status\": \"CANCELLED\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("PROCESSING")));
    }

    @Test
    void cancel_order_should_return_200_with_cancelled_status_when_pending() throws Exception {
        OrderResponse cancelled = OrderResponse.builder()
                .id(1L)
                .customerId(100L)
                .addressId(200L)
                .customerName("Alice")
                .customerEmail("alice@example.com")
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("20.00"))
                .items(List.of())
                .build();

        when(orderService.cancel_order(1L)).thenReturn(cancelled);

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_order_should_return_400_when_order_is_not_pending() throws Exception {
        when(orderService.cancel_order(1L))
                .thenThrow(new OrderCancellationException(1L, OrderStatus.PROCESSING));

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("PROCESSING")));
    }
}
