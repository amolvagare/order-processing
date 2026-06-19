---
agent: agent
description: Write tests for a service method or controller endpoint in the Order Processing System
---

Write tests for the following feature in the Order Processing System:

Feature: ${input:feature_to_test}
Examples:
- "OrderService.cancel_order — cancels a PENDING order and throws for non-PENDING"
- "CustomerService.remove_address — soft-deletes an address; idempotent when address missing"
- "CustomerController POST /api/customers — returns 201; returns 409 on duplicate email"

## Test type to generate

Choose based on what needs testing:

---

### For `OrderService` methods → Unit test in `OrderServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerAddressRepository customerAddressRepository;

    @InjectMocks private OrderService orderService;
```

Required test cases:
- **Happy path**: correct return value, correct side effects (verify `orderRepository.save(...)`)
- **Customer not found**: mock `customerRepository.findById` to return `Optional.empty()`, assert `CustomerNotFoundException`
- **Address not found / soft-deleted**: mock `customerAddressRepository.findByIdAndCustomerIdAndDeletedFalse` to return `Optional.empty()`, assert `AddressNotFoundException`
- **Order not found**: mock `orderRepository.findById` to return `Optional.empty()`, assert `OrderNotFoundException`
- **Invalid state**: set entity to wrong status, assert `OrderCancellationException` (if applicable)

---

### For `CustomerService` methods → Unit test in `CustomerServiceTest.java`

```java
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerAddressRepository customerAddressRepository;

    @InjectMocks private CustomerService customerService;
```

Required test cases:
- **Happy path**: correct return value, verify correct repository method was called
- **Customer not found**: mock `customerRepository.findById` to return `Optional.empty()`, assert `CustomerNotFoundException`
- **Duplicate email**: mock `customerRepository.findByEmail` to return an existing customer, assert `CustomerEmailAlreadyExistsException`
- **Address soft-delete**: verify `deleted=true` and `deletedAt` is set; verify idempotency (no save called when address missing)

---

### For `OrderController` endpoints → MockMvc test in `OrderControllerTest.java`

```java
@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean OrderService orderService;
```

Required test cases:
- **Happy path**: correct HTTP status + key response fields (`$.id`, `$.status`, `$.customerId`, `$.addressId`)
- **Validation failure**: send invalid body (missing required fields), assert `400 Bad Request` + `$.message` is not empty
- **Not found**: service throws `OrderNotFoundException`, assert `404` + `$.message` contains ID
- **Business rule rejection**: service throws `OrderCancellationException`, assert `400` + `$.message` contains status name

---

### For `CustomerController` endpoints → MockMvc test in `CustomerControllerTest.java`

```java
@WebMvcTest(CustomerController.class)
@Import(GlobalExceptionHandler.class)
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CustomerService customerService;
```

Required test cases:
- **Happy path**: correct HTTP status + key response fields (`$.id`, `$.name`, `$.email`)
- **Duplicate email**: service throws `CustomerEmailAlreadyExistsException`, assert `409 Conflict` + `$.message` contains email
- **Not found**: service throws `CustomerNotFoundException`, assert `404` + `$.message` contains ID
- **Address idempotency**: `DELETE /api/customers/{id}/addresses/{addressId}` always returns `204` — test both found and not-found cases

---

### For `OrderStatusScheduler` → Unit test in `OrderStatusSchedulerTest.java`

```java
@ExtendWith(MockitoExtension.class)
class OrderStatusSchedulerTest {
    @Mock OrderService orderService;
    @InjectMocks OrderStatusScheduler scheduler;
```

Required test cases:
- Service is called exactly once: `verify(orderService, times(1)).promote_pending_to_processing()`
- Exception from service does not propagate: mock to throw, assert no exception thrown from scheduler

---

## Naming convention

Test methods: `<method>_should_<expected_outcome>_when_<condition>`

Examples:
- `cancel_order_should_succeed_when_order_is_pending`
- `cancel_order_should_throw_when_order_is_not_pending`
- `create_order_should_throw_when_address_does_not_belong_to_customer`
- `create_customer_should_return_409_when_email_already_exists`
- `remove_address_should_return_204_even_when_address_not_found`

## Arrange–Act–Assert pattern

1. **Arrange** — build sample entities in `@BeforeEach`; wire mocks with `when(...).thenReturn(...)`
2. **Act** — call service method or `mockMvc.perform(...)`
3. **Assert** — use AssertJ `assertThat(...)` / `assertThatThrownBy(...)` (never `assertEquals`); use `jsonPath` for HTTP tests

## Imports to use

```java
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
```
