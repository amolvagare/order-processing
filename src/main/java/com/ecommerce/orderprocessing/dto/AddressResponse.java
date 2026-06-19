package com.ecommerce.orderprocessing.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO representing one customer address.
 */
@Data
@Builder
public class AddressResponse {

    private Long id;
    private String label;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}

