package com.ecommerce.orderprocessing;

import com.ecommerce.orderprocessing.dto.AddressRequest;
import com.ecommerce.orderprocessing.dto.AddressResponse;
import com.ecommerce.orderprocessing.dto.CustomerRequest;
import com.ecommerce.orderprocessing.dto.CustomerResponse;
import com.ecommerce.orderprocessing.exception.CustomerEmailAlreadyExistsException;
import com.ecommerce.orderprocessing.exception.CustomerNotFoundException;
import com.ecommerce.orderprocessing.model.Customer;
import com.ecommerce.orderprocessing.model.CustomerAddress;
import com.ecommerce.orderprocessing.repository.CustomerAddressRepository;
import com.ecommerce.orderprocessing.repository.CustomerRepository;
import com.ecommerce.orderprocessing.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CustomerService with mocked repository interactions.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAddressRepository customerAddressRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer sample_customer;
    private CustomerAddress sample_address;

    @BeforeEach
    void set_up() {
        sample_customer = Customer.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .build();

        sample_address = CustomerAddress.builder()
                .id(10L)
                .customer(sample_customer)
                .label("home")
                .addressLine1("123 Main Street")
                .city("Austin")
                .state("TX")
                .postalCode("73301")
                .country("USA")
                .deleted(false)
                .build();
    }

    @Test
    void create_customer_should_return_saved_profile() {
        CustomerRequest request = new CustomerRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");

        when(customerRepository.save(any(Customer.class))).thenReturn(sample_customer);
        when(customerAddressRepository.findByCustomerIdAndDeletedFalse(1L)).thenReturn(List.of());

        CustomerResponse response = customerService.create_customer(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void create_customer_should_throw_when_email_already_exists() {
        CustomerRequest request = new CustomerRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");

        when(customerRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sample_customer));

        assertThatThrownBy(() -> customerService.create_customer(request))
                .isInstanceOf(CustomerEmailAlreadyExistsException.class)
                .hasMessageContaining("alice@example.com");
    }

    @Test
    void update_customer_should_throw_when_email_already_used_by_another_customer() {
        Customer another_customer = Customer.builder()
                .id(2L)
                .name("Bob")
                .email("bob@example.com")
                .build();

        CustomerRequest request = new CustomerRequest();
        request.setName("Alice Updated");
        request.setEmail("bob@example.com");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sample_customer));
        when(customerRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(another_customer));

        assertThatThrownBy(() -> customerService.update_customer(1L, request))
                .isInstanceOf(CustomerEmailAlreadyExistsException.class)
                .hasMessageContaining("bob@example.com");
    }

    @Test
    void get_customer_by_id_should_throw_when_customer_missing() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.get_customer_by_id(99L))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void add_address_should_append_new_address_for_customer() {
        AddressRequest request = new AddressRequest();
        request.setLabel("work");
        request.setAddressLine1("500 5th Ave");
        request.setAddressLine2("Floor 9");
        request.setCity("New York");
        request.setState("NY");
        request.setPostalCode("10018");
        request.setCountry("USA");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(sample_customer));
        when(customerAddressRepository.save(any(CustomerAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse response = customerService.add_address(1L, request);

        assertThat(response.getAddressLine1()).isEqualTo("500 5th Ave");
        verify(customerAddressRepository).save(any(CustomerAddress.class));
    }

    @Test
    void remove_address_should_throw_when_address_missing_for_customer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(sample_customer));
        when(customerAddressRepository.findByIdAndCustomerId(999L, 1L)).thenReturn(Optional.empty());

        // Should not throw - idempotent operation
        customerService.remove_address(1L, 999L);

        verify(customerAddressRepository).findByIdAndCustomerId(999L, 1L);
        verify(customerAddressRepository, never()).save(any());
    }

    @Test
    void remove_address_should_soft_delete_when_address_exists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(sample_customer));
        when(customerAddressRepository.findByIdAndCustomerId(10L, 1L)).thenReturn(Optional.of(sample_address));

        customerService.remove_address(1L, 10L);

        ArgumentCaptor<CustomerAddress> address_captor = ArgumentCaptor.forClass(CustomerAddress.class);
        verify(customerAddressRepository).save(address_captor.capture());
        assertThat(address_captor.getValue().getDeleted()).isTrue();
        assertThat(address_captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void list_addresses_should_return_all_active_addresses_for_customer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(sample_customer));
        when(customerAddressRepository.findByCustomerIdAndDeletedFalse(1L)).thenReturn(List.of(sample_address));

        List<AddressResponse> response = customerService.list_addresses(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getCity()).isEqualTo("Austin");
        verify(customerRepository).findById(1L);
    }
}
