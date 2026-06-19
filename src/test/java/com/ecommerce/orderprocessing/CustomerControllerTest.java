package com.ecommerce.orderprocessing;

import com.ecommerce.orderprocessing.controller.CustomerController;
import com.ecommerce.orderprocessing.dto.AddressResponse;
import com.ecommerce.orderprocessing.dto.CustomerResponse;
import com.ecommerce.orderprocessing.exception.CustomerEmailAlreadyExistsException;
import com.ecommerce.orderprocessing.exception.CustomerNotFoundException;
import com.ecommerce.orderprocessing.exception.GlobalExceptionHandler;
import com.ecommerce.orderprocessing.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for CustomerController endpoints.
 */
@WebMvcTest(CustomerController.class)
@Import(GlobalExceptionHandler.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    private CustomerResponse sample_customer;

    @BeforeEach
    void set_up() {
        AddressResponse address = AddressResponse.builder()
                .id(10L)
                .label("home")
                .addressLine1("123 Main Street")
                .city("Austin")
                .state("TX")
                .postalCode("73301")
                .country("USA")
                .build();

        sample_customer = CustomerResponse.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .addresses(List.of(address))
                .build();
    }

    @Test
    void create_customer_should_return_201() throws Exception {
        when(customerService.create_customer(any())).thenReturn(sample_customer);

        String payload = """
                {
                  "name": "Alice",
                  "email": "alice@example.com"
                }
                """;

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void create_customer_should_return_409_when_email_already_exists() throws Exception {
        when(customerService.create_customer(any()))
                .thenThrow(new CustomerEmailAlreadyExistsException("alice@example.com"));

        String payload = """
                {
                  "name": "Alice",
                  "email": "alice@example.com"
                }
                """;

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("alice@example.com")));
    }

    @Test
    void get_customer_should_return_404_when_missing() throws Exception {
        when(customerService.get_customer_by_id(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("99")));
    }

    @Test
    void add_address_should_return_201() throws Exception {
        AddressResponse response = AddressResponse.builder()
                .id(20L)
                .label("work")
                .addressLine1("500 5th Ave")
                .city("New York")
                .state("NY")
                .postalCode("10018")
                .country("USA")
                .build();

        when(customerService.add_address(eq(1L), any())).thenReturn(response);

        String payload = """
                {
                  "label": "work",
                  "addressLine1": "500 5th Ave",
                  "city": "New York",
                  "state": "NY",
                  "postalCode": "10018",
                  "country": "USA"
                }
                """;

        mockMvc.perform(post("/api/customers/1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.label").value("work"));
    }

    @Test
    void remove_address_should_return_204() throws Exception {
        doNothing().when(customerService).remove_address(1L, 10L);

        mockMvc.perform(delete("/api/customers/1/addresses/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    void remove_address_should_return_204_even_when_address_not_found() throws Exception {
        doNothing().when(customerService).remove_address(1L, 999L);

        mockMvc.perform(delete("/api/customers/1/addresses/999"))
                .andExpect(status().isNoContent());
    }
}

