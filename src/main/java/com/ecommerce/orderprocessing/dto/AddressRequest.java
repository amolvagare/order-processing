package com.ecommerce.orderprocessing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload for adding a customer address.
 */
@Data
public class AddressRequest {

    private String label;

    @NotBlank(message = "Address line1 is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Postal code is required")
    private String postalCode;

    @NotBlank(message = "Country is required")
    private String country;
}

