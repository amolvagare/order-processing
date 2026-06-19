package com.ecommerce.orderprocessing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO representing a customer profile and addresses.
 */
@Data
@Builder
public class CustomerResponse {

    private Long id;
    private String name;
    private String email;
    private List<AddressResponse> addresses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

