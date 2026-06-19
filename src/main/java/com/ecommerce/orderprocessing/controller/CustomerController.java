package com.ecommerce.orderprocessing.controller;

import com.ecommerce.orderprocessing.dto.*;
import com.ecommerce.orderprocessing.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing customer and address management endpoints.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customers", description = "Customer profile API")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * POST /api/customers
     * Create a customer profile.
     */
    @PostMapping
    @Operation(summary = "Create a customer")
    public ResponseEntity<CustomerResponse> create_customer(@Valid @RequestBody CustomerRequest request) {
        log.info("POST /api/customers - payload: {}", request);
        CustomerResponse response = customerService.create_customer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/customers/{id}
     * Retrieve one customer profile by id.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<CustomerResponse> get_customer(@PathVariable Long id) {
        log.info("GET /api/customers/{}", id);
        return ResponseEntity.ok(customerService.get_customer_by_id(id));
    }

    /**
     * GET /api/customers
     * List all customer profiles.
     */
    @GetMapping
    @Operation(summary = "List customers")
    public ResponseEntity<List<CustomerResponse>> list_customers() {
        log.info("GET /api/customers");
        return ResponseEntity.ok(customerService.list_customers());
    }

    /**
     * PUT /api/customers/{id}
     * Update name/email for a customer profile.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update customer")
    public ResponseEntity<CustomerResponse> update_customer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        log.info("PUT /api/customers/{} - payload: {}", id, request);
        return ResponseEntity.ok(customerService.update_customer(id, request));
    }

    /**
     * POST /api/customers/{id}/addresses
     * Add one address to a customer profile.
     */
    @PostMapping("/{id}/addresses")
    @Operation(summary = "Add customer address")
    public ResponseEntity<AddressResponse> add_address(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        log.info("POST /api/customers/{}/addresses - payload: {}", id, request);
        AddressResponse response = customerService.add_address(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/customers/{id}/addresses
     * List all addresses for a customer profile.
     */
    @GetMapping("/{id}/addresses")
    @Operation(summary = "List customer addresses")
    public ResponseEntity<List<AddressResponse>> list_addresses(@PathVariable Long id) {
        log.info("GET /api/customers/{}/addresses", id);
        return ResponseEntity.ok(customerService.list_addresses(id));
    }

    /**
     * DELETE /api/customers/{id}/addresses/{addressId}
     * Remove an address from a customer profile.
     */
    @DeleteMapping("/{id}/addresses/{addressId}")
    @Operation(summary = "Remove customer address")
    public ResponseEntity<Void> remove_address(@PathVariable Long id, @PathVariable Long addressId) {
        log.info("DELETE /api/customers/{}/addresses/{}", id, addressId);
        customerService.remove_address(id, addressId);
        return ResponseEntity.noContent().build();
    }
}

