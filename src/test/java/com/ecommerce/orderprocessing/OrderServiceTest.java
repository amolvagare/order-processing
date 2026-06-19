package com.ecommerce.orderprocessing;

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
import com.ecommerce.orderprocessing.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService — repository is mocked.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAddressRepository customerAddressRepository;

    @InjectMocks
    private OrderService orderService;

    private Order sample_order;
    private Customer sample_customer;
    private CustomerAddress sample_address;
    private CreateOrderRequest create_request;

    @BeforeEach
    void setUp() {
        // Build a sample order entity with one item
        OrderItem item = OrderItem.builder()
                .id(1L)
                .productName("Widget")
                .quantity(2)
                .unitPrice(new BigDecimal("10.00"))
                .build();

        sample_customer = Customer.builder()
                .id(100L)
                .name("Alice")
                .email("alice@example.com")
                .build();

        sample_address = CustomerAddress.builder()
                .id(200L)
                .customer(sample_customer)
                .addressLine1("123 Main Street")
                .city("Austin")
                .state("TX")
                .postalCode("73301")
                .country("USA")
                .deleted(false)
                .build();

        sample_order = Order.builder()
                .id(1L)
                .customer(sample_customer)
                .customerAddress(sample_address)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("20.00"))
                .build();
        sample_order.getItems().add(item);
        item.setOrder(sample_order);

        // Build a matching create request
        OrderItemRequest item_req = new OrderItemRequest();
        item_req.setProductName("Widget");
        item_req.setQuantity(2);
        item_req.setUnitPrice(new BigDecimal("10.00"));

        create_request = new CreateOrderRequest();
        create_request.setCustomerId(100L);
        create_request.setAddressId(200L);
        create_request.setItems(List.of(item_req));
    }

    @Test
    void create_order_should_return_pending_order_with_correct_total() {
        when(customerRepository.findById(100L)).thenReturn(Optional.of(sample_customer));
        when(customerAddressRepository.findByIdAndCustomerIdAndDeletedFalse(200L, 100L)).thenReturn(Optional.of(sample_address));
        when(orderRepository.save(any(Order.class))).thenReturn(sample_order);

        OrderResponse response = orderService.create_order(create_request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getCustomerId()).isEqualTo(100L);
        assertThat(response.getAddressId()).isEqualTo(200L);
        assertThat(response.getCustomerEmail()).isEqualTo("alice@example.com");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("20.00");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void create_order_should_throw_when_customer_is_missing() {
        when(customerRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create_order(create_request))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("100");
    }

    @Test
    void create_order_should_throw_when_address_does_not_belong_to_customer() {
        when(customerRepository.findById(100L)).thenReturn(Optional.of(sample_customer));
        when(customerAddressRepository.findByIdAndCustomerIdAndDeletedFalse(200L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create_order(create_request))
                .isInstanceOf(AddressNotFoundException.class)
                .hasMessageContaining("200");
    }


    @Test
    void get_order_by_id_should_return_order_when_found() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sample_order));

        OrderResponse response = orderService.get_order_by_id(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerName()).isEqualTo("Alice");
    }

    @Test
    void get_order_by_id_should_throw_when_not_found() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.get_order_by_id(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void list_orders_without_filter_should_return_all() {
        when(orderRepository.findAll()).thenReturn(List.of(sample_order));

        List<OrderResponse> result = orderService.list_orders(Optional.empty());

        assertThat(result).hasSize(1);
    }

    @Test
    void list_orders_with_status_filter_should_return_filtered_results() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(sample_order));

        List<OrderResponse> result = orderService.list_orders(Optional.of(OrderStatus.PENDING));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void update_order_status_should_update_and_return_new_status() {
        Order updated = Order.builder()
                .id(1L)
                .customer(sample_customer)
                .customerAddress(sample_address)
                .status(OrderStatus.SHIPPED)
                .totalAmount(new BigDecimal("20.00"))
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(sample_order));
        when(orderRepository.save(any(Order.class))).thenReturn(updated);

        OrderResponse response = orderService.update_order_status(1L, OrderStatus.SHIPPED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void update_order_status_should_throw_when_cancelling_non_pending_order() {
        sample_order.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sample_order));

        assertThatThrownBy(() -> orderService.update_order_status(1L, OrderStatus.CANCELLED))
                .isInstanceOf(OrderCancellationException.class)
                .hasMessageContaining("PROCESSING");
    }

    @Test
    void cancel_order_should_succeed_when_order_is_pending() {
        Order cancelled = Order.builder()
                .id(1L)
                .customer(sample_customer)
                .customerAddress(sample_address)
                .status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("20.00"))
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(sample_order));
        when(orderRepository.save(any(Order.class))).thenReturn(cancelled);

        OrderResponse response = orderService.cancel_order(1L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_order_should_throw_when_order_is_not_pending() {
        // Override status to PROCESSING
        sample_order.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sample_order));

        assertThatThrownBy(() -> orderService.cancel_order(1L))
                .isInstanceOf(OrderCancellationException.class)
                .hasMessageContaining("PROCESSING");
    }

    @Test
    void promote_pending_to_processing_should_update_all_pending_orders() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(sample_order));
        when(orderRepository.saveAll(anyList())).thenReturn(List.of(sample_order));

        int count = orderService.promote_pending_to_processing();

        assertThat(count).isEqualTo(1);
        // Status should have been mutated on the entity
        assertThat(sample_order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void promote_pending_to_processing_should_return_zero_when_no_pending_orders() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());
        when(orderRepository.saveAll(anyList())).thenReturn(List.of());

        int count = orderService.promote_pending_to_processing();

        assertThat(count).isEqualTo(0);
    }
}
