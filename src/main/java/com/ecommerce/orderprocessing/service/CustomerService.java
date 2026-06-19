package com.ecommerce.orderprocessing.service;

import com.ecommerce.orderprocessing.dto.*;
import com.ecommerce.orderprocessing.exception.CustomerEmailAlreadyExistsException;
import com.ecommerce.orderprocessing.exception.CustomerNotFoundException;
import com.ecommerce.orderprocessing.model.Customer;
import com.ecommerce.orderprocessing.model.CustomerAddress;
import com.ecommerce.orderprocessing.repository.CustomerAddressRepository;
import com.ecommerce.orderprocessing.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer containing all customer and address business logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;

    /**
     * Create a customer profile.
     */
    @Transactional
    public CustomerResponse create_customer(CustomerRequest request) {
        log.debug("Creating customer profile - email: {}", request.getEmail());
        ensure_email_is_unique_for_customer(request.getEmail(), null);

        Customer customer = Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .build();

        Customer saved;
        try {
            saved = customerRepository.save(customer);
        } catch (DataIntegrityViolationException ex) {
            throw new CustomerEmailAlreadyExistsException(request.getEmail());
        }
        return map_customer_to_response(saved);
    }

    /**
     * Fetch one customer by id.
     */
    @Transactional(readOnly = true)
    public CustomerResponse get_customer_by_id(Long customer_id) {
        log.debug("Fetching customer profile - id: {}", customer_id);
        Customer customer = find_customer_or_throw(customer_id);
        return map_customer_to_response(customer);
    }

    /**
     * List all customer profiles.
     */
    @Transactional(readOnly = true)
    public List<CustomerResponse> list_customers() {
        log.debug("Listing all customer profiles");
        return customerRepository.findAll().stream()
                .map(this::map_customer_to_response)
                .collect(Collectors.toList());
    }

    /**
     * Update customer profile fields.
     */
    @Transactional
    public CustomerResponse update_customer(Long customer_id, CustomerRequest request) {
        log.debug("Updating customer profile - id: {}, email: {}", customer_id, request.getEmail());
        Customer customer = find_customer_or_throw(customer_id);
        ensure_email_is_unique_for_customer(request.getEmail(), customer_id);

        customer.setName(request.getName());
        customer.setEmail(request.getEmail());

        Customer saved;
        try {
            saved = customerRepository.save(customer);
        } catch (DataIntegrityViolationException ex) {
            throw new CustomerEmailAlreadyExistsException(request.getEmail());
        }
        return map_customer_to_response(saved);
    }

    /**
     * Add one address under a customer profile.
     */
    @Transactional
    public AddressResponse add_address(Long customer_id, AddressRequest request) {
        log.debug("Adding address - customer_id: {}, label: {}", customer_id, request.getLabel());
        Customer customer = find_customer_or_throw(customer_id);

        CustomerAddress address = CustomerAddress.builder()
                .customer(customer)
                .label(request.getLabel())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .deleted(false)
                .deletedAt(null)
                .build();

        CustomerAddress saved_address = customerAddressRepository.save(address);
        return map_address_to_response(saved_address);
    }

    /**
     * Remove one address from a customer profile.
     */
    @Transactional
    public void remove_address(Long customer_id, Long address_id) {
        log.debug("Removing address - customer_id: {}, address_id: {}", customer_id, address_id);
        find_customer_or_throw(customer_id);

        // Idempotent operation: silently succeed whether address exists or not
        customerAddressRepository.findByIdAndCustomerId(address_id, customer_id)
                .ifPresent(address -> {
                    if (Boolean.TRUE.equals(address.getDeleted())) {
                        return;
                    }
                    address.setDeleted(true);
                    address.setDeletedAt(LocalDateTime.now());
                    customerAddressRepository.save(address);
                });
    }

    /**
     * List all active addresses for one customer.
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> list_addresses(Long customer_id) {
        log.debug("Listing addresses - customer_id: {}", customer_id);
        find_customer_or_throw(customer_id);
        return customerAddressRepository.findByCustomerIdAndDeletedFalse(customer_id).stream()
                .map(this::map_address_to_response)
                .collect(Collectors.toList());
    }

    // --- Private helpers ---

    /**
     * Find a customer or throw a domain exception.
     */
    private Customer find_customer_or_throw(Long customer_id) {
        return customerRepository.findById(customer_id)
                .orElseThrow(() -> new CustomerNotFoundException(customer_id));
    }

    /**
     * Reject duplicate emails, excluding the current customer when updating.
     */
    private void ensure_email_is_unique_for_customer(String customer_email, Long current_customer_id) {
        customerRepository.findByEmail(customer_email)
                .filter(existing_customer -> !existing_customer.getId().equals(current_customer_id))
                .ifPresent(existing_customer -> {
                    throw new CustomerEmailAlreadyExistsException(customer_email);
                });
    }

    /**
     * Map a customer entity into API response shape.
     */
    private CustomerResponse map_customer_to_response(Customer customer) {
        List<AddressResponse> addresses = customerAddressRepository.findByCustomerIdAndDeletedFalse(customer.getId()).stream()
                .map(this::map_address_to_response)
                .collect(Collectors.toList());

        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .addresses(addresses)
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }

    /**
     * Map one address entity to API response shape.
     */
    private AddressResponse map_address_to_response(CustomerAddress address) {
        return AddressResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }
}
