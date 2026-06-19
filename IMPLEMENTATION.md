# Order Processing System — Implementation Details

## 1. Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+ (or use the wrapper from `~/.m2/wrapper/dists/`)
- Git

### Run locally (H2 in-memory database)

```bash
cd order-processing
mvn spring-boot:run
```

- API base URL: `http://localhost:8080/api/orders`, `http://localhost:8080/api/customers`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:orderdb`, user: `sa`, password: empty)

### Run tests

```bash
mvn clean test
```

Expected output: `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`

### Run with PostgreSQL (prod profile)

```bash
DB_USERNAME=myuser DB_PASSWORD=mypass mvn spring-boot:run --spring.profiles.active=prod
```

Or set env vars in your shell and run with `-Dspring.profiles.active=prod`.

---

## 2. Project Structure

```
order-processing/
├── pom.xml                                              ← Maven build, all dependencies
├── PLAN.md                                              ← Architecture & design decisions
├── IMPLEMENTATION.md                                    ← This file
└── src/
    ├── main/
    │   ├── java/com/ecommerce/orderprocessing/
    │   │   ├── OrderProcessingApplication.java          ← Entry point, @EnableScheduling
    │   │   ├── enums/
    │   │   │   └── OrderStatus.java                     ← PENDING/PROCESSING/SHIPPED/DELIVERED/CANCELLED
    │   │   ├── model/
    │   │   │   ├── Customer.java                        ← JPA entity, customers table
    │   │   │   ├── CustomerAddress.java                 ← JPA entity, customer_addresses table (soft-delete)
    │   │   │   ├── Order.java                           ← JPA entity, orders table
    │   │   │   └── OrderItem.java                       ← JPA entity, order_items table
    │   │   ├── repository/
    │   │   │   ├── CustomerRepository.java              ← JpaRepository + findByEmail()
    │   │   │   ├── CustomerAddressRepository.java       ← JpaRepository + soft-delete aware queries
    │   │   │   └── OrderRepository.java                 ← JpaRepository + findByStatus()
    │   │   ├── dto/
    │   │   │   ├── CustomerRequest.java                 ← POST/PUT /api/customers body
    │   │   │   ├── CustomerResponse.java                ← Customer API response (includes addresses[])
    │   │   │   ├── AddressRequest.java                  ← POST /api/customers/{id}/addresses body
    │   │   │   ├── AddressResponse.java                 ← Address within customer response
    │   │   │   ├── CreateOrderRequest.java              ← POST /api/orders body (customerId + addressId)
    │   │   │   ├── OrderItemRequest.java                ← Item within create order request
    │   │   │   ├── UpdateStatusRequest.java             ← PUT /api/orders/{id}/status body
    │   │   │   ├── OrderResponse.java                   ← All order responses (includes customerId, addressId)
    │   │   │   └── OrderItemResponse.java               ← Item within order response
    │   │   ├── service/
    │   │   │   ├── CustomerService.java                 ← Customer/address business logic
    │   │   │   └── OrderService.java                    ← Order business logic
    │   │   ├── controller/
    │   │   │   ├── CustomerController.java              ← HTTP layer for /api/customers
    │   │   │   └── OrderController.java                 ← HTTP layer for /api/orders
    │   │   ├── scheduler/
    │   │   │   └── OrderStatusScheduler.java            ← 5-min PENDING→PROCESSING job
    │   │   └── exception/
    │   │       ├── OrderNotFoundException.java          ← 404 trigger (order)
    │   │       ├── OrderCancellationException.java      ← 400 trigger (cancel non-PENDING)
    │   │       ├── CustomerNotFoundException.java       ← 404 trigger (customer)
    │   │       ├── CustomerEmailAlreadyExistsException.java ← 409 trigger (duplicate email)
    │   │       ├── AddressNotFoundException.java        ← 404 trigger (address)
    │   │       └── GlobalExceptionHandler.java          ← @RestControllerAdvice, uniform errors
    │   └── resources/
    │       ├── application.yml                          ← H2 dev config (default profile)
    │       └── application-prod.yml                     ← PostgreSQL config (prod profile)
    └── test/
        └── java/com/ecommerce/orderprocessing/
            ├── OrderServiceTest.java                    ← 13 unit tests
            ├── OrderControllerTest.java                 ← 10 MockMvc tests
            ├── OrderStatusSchedulerTest.java            ← 2 unit tests
            ├── CustomerServiceTest.java                 ← 8 unit tests
            └── CustomerControllerTest.java              ← 6 MockMvc tests
```

---

## 3. Package & Class Reference

### `enums/OrderStatus.java`
Simple enum with 5 values:
```
PENDING → PROCESSING → SHIPPED → DELIVERED
PENDING → CANCELLED  (cancel-only path)
```
Stored as `EnumType.STRING` in the database for human-readability and migration safety.

---

### `model/Customer.java`
JPA entity mapped to the `customers` table.

Key annotations:
- `@Entity @Table(name = "customers")`
- `@Column(name = "customer_name")` / `@Column(name = "customer_email", unique = true)` — DB column names differ from Java field names
- `@CreationTimestamp` / `@UpdateTimestamp` — Hibernate auto-manages audit timestamps
- Java fields: `name`, `email` (camelCase); DB columns: `customer_name`, `customer_email` (snake_case)

---

### `model/CustomerAddress.java`
JPA entity mapped to `customer_addresses`. Supports **soft-delete** — records are never physically removed.

Key annotations:
- `@ManyToOne(fetch = FetchType.LAZY, optional = false)` → `@JoinColumn(name = "customer_id")` — FK to `customers`
- `@Column(name = "address_label")` — optional display label
- `@Column(name = "is_deleted")` with `@Builder.Default private Boolean deleted = false`
- `@Column(name = "deleted_at")` — nullable `LocalDateTime`, set on soft-delete
- Java field `label` maps to DB column `address_label`; `deleted` maps to `is_deleted`

---

### `model/Order.java`
JPA entity mapped to the `orders` table.

Key annotations:
- `@Entity @Table(name = "orders")` — maps to `orders`
- `@ManyToOne(fetch = FetchType.LAZY, optional = false)` to both `Customer` and `CustomerAddress` — orders must always reference a valid customer and one of their addresses
- `@Enumerated(EnumType.STRING)` on `status` — persists enum name, not ordinal
- `@OneToMany(cascade = ALL, orphanRemoval = true, fetch = LAZY)` — items are cascade-managed
- `@CreationTimestamp` / `@UpdateTimestamp` — Hibernate auto-manages audit timestamps
- `@Builder.Default` on `status` and `items` — ensures Lombok builder respects defaults

---

### `model/OrderItem.java`
JPA entity mapped to `order_items`.

Key detail: `@ManyToOne(fetch = LAZY)` on `order` — avoids N+1 loading. Items are always accessed within the parent order's transaction.

---

### `repository/CustomerRepository.java`
```java
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
}
```
- `findByEmail` used by `CustomerService` to enforce unique email on create and update.

---

### `repository/CustomerAddressRepository.java`
```java
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {
    List<CustomerAddress>    findByCustomerIdAndDeletedFalse(Long customerId);
    Optional<CustomerAddress> findByIdAndCustomerId(Long addressId, Long customerId);
    Optional<CustomerAddress> findByIdAndCustomerIdAndDeletedFalse(Long addressId, Long customerId);
}
```
- `findByCustomerIdAndDeletedFalse` — active address listing (used by `list_addresses` and `map_customer_to_response`)
- `findByIdAndCustomerId` — used in soft-delete flow (`remove_address`) — includes already-deleted records for idempotency
- `findByIdAndCustomerIdAndDeletedFalse` — used in order creation to reject soft-deleted addresses

---

### `repository/OrderRepository.java`
```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatus(OrderStatus status);
}
```
- `findByStatus` used by both the service (status filter) and the scheduler (find PENDING batch)

---

### `dto/` — Data Transfer Objects

| Class | Direction | Key Fields |
|---|---|---|
| `CustomerRequest` | Client → Server | `name`, `email` |
| `CustomerResponse` | Server → Client | `id`, `name`, `email`, `addresses[]`, `createdAt`, `updatedAt` |
| `AddressRequest` | Client → Server | `label` (opt), `addressLine1`, `addressLine2` (opt), `city`, `state`, `postalCode`, `country` |
| `AddressResponse` | Server → Client | `id`, `label`, address fields (soft-delete fields never exposed) |
| `CreateOrderRequest` | Client → Server | `customerId` (Long), `addressId` (Long), `items[]` |
| `OrderItemRequest` | Client → Server | `productName`, `quantity`, `unitPrice` |
| `UpdateStatusRequest` | Client → Server | `status` (OrderStatus enum) |
| `OrderResponse` | Server → Client | `id`, `customerId`, `addressId`, `customerName`, `customerEmail`, `status`, `totalAmount`, `items[]`, timestamps |
| `OrderItemResponse` | Server → Client | `id`, `productName`, `quantity`, `unitPrice`, computed `subtotal` |

**Validation constraints on request DTOs:**
- `CustomerRequest`: `@NotBlank` on name and email; `@Email` on email
- `AddressRequest`: `@NotBlank` on addressLine1, city, state, postalCode, country
- `CreateOrderRequest`: `@NotNull` on customerId and addressId; `@NotEmpty` + `@Valid` on items list
- `OrderItemRequest`: `@NotBlank` on productName; `@Min(1)` on quantity; `@DecimalMin("0.01")` on unitPrice

---

### `service/CustomerService.java`
All customer and address business logic. Depends on `CustomerRepository` and `CustomerAddressRepository`.

| Method | Signature | Notes |
|---|---|---|
| `create_customer` | `(CustomerRequest) → CustomerResponse` | Calls `ensure_email_is_unique_for_customer`; catches `DataIntegrityViolationException` as a second guard |
| `get_customer_by_id` | `(Long) → CustomerResponse` | Throws `CustomerNotFoundException` if absent |
| `list_customers` | `() → List<CustomerResponse>` | Returns all customers, each with their active addresses |
| `update_customer` | `(Long, CustomerRequest) → CustomerResponse` | Re-enforces email uniqueness excluding self |
| `add_address` | `(Long, AddressRequest) → AddressResponse` | Creates address with `deleted=false`; verifies customer exists first |
| `remove_address` | `(Long, Long) → void` | Soft-delete (sets `deleted=true`, `deletedAt=now`); idempotent — no error if address missing/already deleted |
| `list_addresses` | `(Long) → List<AddressResponse>` | Returns only non-deleted addresses via `findByCustomerIdAndDeletedFalse` |

**Private helpers:**
- `find_customer_or_throw(Long)` — DRY lookup
- `ensure_email_is_unique_for_customer(String, Long)` — rejects duplicates, excludes current customer ID when updating
- `map_customer_to_response(Customer)` — maps entity + active addresses to response DTO
- `map_address_to_response(CustomerAddress)` — maps address entity to response DTO

---

### `service/OrderService.java`
All order business logic. Depends on `OrderRepository`, `CustomerRepository`, and `CustomerAddressRepository`.

| Method | Signature | Notes |
|---|---|---|
| `create_order` | `(CreateOrderRequest) → OrderResponse` | Validates customer + address exist and address is not soft-deleted; maps items, computes total, saves |
| `get_order_by_id` | `(Long) → OrderResponse` | Throws `OrderNotFoundException` if absent |
| `list_orders` | `(Optional<OrderStatus>) → List<OrderResponse>` | Empty Optional = findAll(); present = findByStatus() |
| `update_order_status` | `(Long, OrderStatus) → OrderResponse` | Guards CANCELLED target: only allowed from PENDING; other transitions unrestricted |
| `cancel_order` | `(Long) → OrderResponse` | Guards status == PENDING; throws `OrderCancellationException` otherwise |
| `promote_pending_to_processing` | `() → int` | Batch update; returns count; used by scheduler |

All write methods are `@Transactional`. Read methods use `@Transactional(readOnly = true)`.

**Private helpers:**
- `find_order_or_throw(Long)` — DRY lookup
- `find_customer_or_throw(Long)` — throws `CustomerNotFoundException`
- `find_customer_address_or_throw(Long, Long)` — uses `findByIdAndCustomerIdAndDeletedFalse`; throws `AddressNotFoundException`
- `map_to_order_item(OrderItemRequest)` — maps request to entity
- `map_to_response(Order)` — maps entity to response DTO (includes customer name/email from relationship)
- `map_item_to_response(OrderItem)` — maps item entity + computes subtotal

---

### `controller/CustomerController.java`
HTTP layer only — zero business logic.

| Method | HTTP | Path | Status |
|---|---|---|---|
| `create_customer` | POST | `/api/customers` | 201 Created |
| `get_customer` | GET | `/api/customers/{id}` | 200 OK |
| `list_customers` | GET | `/api/customers` | 200 OK |
| `update_customer` | PUT | `/api/customers/{id}` | 200 OK |
| `add_address` | POST | `/api/customers/{id}/addresses` | 201 Created |
| `list_addresses` | GET | `/api/customers/{id}/addresses` | 200 OK |
| `remove_address` | DELETE | `/api/customers/{id}/addresses/{addressId}` | 204 No Content |

---

### `controller/OrderController.java`
HTTP layer only — zero business logic.

| Method | HTTP | Path | Status |
|---|---|---|---|
| `create_order` | POST | `/api/orders` | 201 Created |
| `get_order` | GET | `/api/orders/{id}` | 200 OK |
| `list_orders` | GET | `/api/orders?status=` | 200 OK |
| `update_status` | PUT | `/api/orders/{id}/status` | 200 OK |
| `cancel_order` | DELETE | `/api/orders/{id}` | 200 OK |

All endpoints log the incoming request at INFO level with `@Slf4j`.

---

### `scheduler/OrderStatusScheduler.java`
```java
@Scheduled(fixedRate = 300_000)   // every 5 minutes
public void promote_pending_orders() {
    // delegates to orderService.promote_pending_to_processing()
    // catches all exceptions — scheduler thread never dies on DB errors
}
```

`@EnableScheduling` is on `OrderProcessingApplication` — required for `@Scheduled` to activate.

---

### `exception/GlobalExceptionHandler.java`
`@RestControllerAdvice` intercepts all controller exceptions and converts them to consistent JSON:

| Exception | HTTP Status | Trigger |
|---|---|---|
| `OrderNotFoundException` | 404 Not Found | `GET/PUT/DELETE` with unknown order ID |
| `CustomerNotFoundException` | 404 Not Found | Any operation with unknown customer ID |
| `AddressNotFoundException` | 404 Not Found | Order creation with invalid/deleted address |
| `OrderCancellationException` | 400 Bad Request | Cancel attempted on non-PENDING order |
| `CustomerEmailAlreadyExistsException` | 409 Conflict | Create/update customer with duplicate email |
| `MethodArgumentNotValidException` | 400 Bad Request | `@Valid` validation failure on request body |
| `Exception` (catch-all) | 500 Internal Server Error | Any unexpected failure |

All error responses share the same JSON shape:
```json
{
  "timestamp": "2026-06-19T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Order not found with id: 99"
}
```

---

## 4. Configuration

### `application.yml` (default — H2 dev)
```properties
spring.datasource.url=jdbc:h2:mem:orderdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.jpa.hibernate.ddl-auto=create-drop    # schema created on start, dropped on shutdown
spring.h2.console.enabled=true               # accessible at /h2-console
logging.level.com.ecommerce.orderprocessing=DEBUG
```

### `application-prod.yml` (PostgreSQL)
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/orderdb
spring.datasource.username=${DB_USERNAME}    # from environment variable
spring.datasource.password=${DB_PASSWORD}    # from environment variable
spring.jpa.hibernate.ddl-auto=validate       # schema managed externally in prod
spring.h2.console.enabled=false
```

Activate with: `--spring.profiles.active=prod`

---

## 5. API Usage Examples

### Register a customer
```http
POST /api/customers
Content-Type: application/json

{ "name": "Alice Smith", "email": "alice@example.com" }
```
Response `201`:
```json
{ "id": 1, "name": "Alice Smith", "email": "alice@example.com", "addresses": [], "createdAt": "...", "updatedAt": "..." }
```

### Add an address to a customer
```http
POST /api/customers/1/addresses
Content-Type: application/json

{
  "label": "home",
  "addressLine1": "123 Main Street",
  "city": "Austin",
  "state": "TX",
  "postalCode": "73301",
  "country": "USA"
}
```
Response `201`: `{ "id": 10, "label": "home", "addressLine1": "123 Main Street", ... }`

### Create an order (references existing customer and address)
```http
POST /api/orders
Content-Type: application/json

{
  "customerId": 1,
  "addressId": 10,
  "items": [
    { "productName": "Laptop", "quantity": 1, "unitPrice": 999.99 },
    { "productName": "Mouse",  "quantity": 2, "unitPrice": 29.99  }
  ]
}
```
Response `201`:
```json
{
  "id": 1,
  "customerId": 1,
  "addressId": 10,
  "customerName": "Alice Smith",
  "customerEmail": "alice@example.com",
  "status": "PENDING",
  "totalAmount": 1059.97,
  "items": [
    { "id": 1, "productName": "Laptop", "quantity": 1, "unitPrice": 999.99, "subtotal": 999.99 },
    { "id": 2, "productName": "Mouse",  "quantity": 2, "unitPrice": 29.99,  "subtotal": 59.98  }
  ],
  "createdAt": "2026-06-19T12:00:00",
  "updatedAt": "2026-06-19T12:00:00"
}
```

### Get order by ID
```http
GET /api/orders/1
```

### List orders filtered by status
```http
GET /api/orders?status=PENDING
GET /api/orders                  # returns all
```

### Update status manually
```http
PUT /api/orders/1/status
Content-Type: application/json

{ "status": "SHIPPED" }
```

### Cancel an order
```http
DELETE /api/orders/1
```
Returns `400` with error message if order is not in `PENDING` status.

### Soft-delete an address (idempotent)
```http
DELETE /api/customers/1/addresses/10
```
Always returns `204 No Content`. Soft-deleted addresses cannot be used for new orders.

---

## 6. Test Coverage

### `OrderServiceTest` (13 tests)
| Test | Covers |
|---|---|
| `create_order_should_return_pending_order_with_correct_total` | Create path, total computation, customer/address resolution |
| `create_order_should_throw_when_customer_is_missing` | CustomerNotFoundException on create |
| `create_order_should_throw_when_address_does_not_belong_to_customer` | AddressNotFoundException on create |
| `get_order_by_id_should_return_order_when_found` | Successful GET |
| `get_order_by_id_should_throw_when_not_found` | OrderNotFoundException |
| `list_orders_without_filter_should_return_all` | findAll() path |
| `list_orders_with_status_filter_should_return_filtered_results` | findByStatus() path |
| `update_order_status_should_update_and_return_new_status` | Status update happy path |
| `update_order_status_should_throw_when_cancelling_non_pending_order` | CANCELLED guard via update endpoint |
| `cancel_order_should_succeed_when_order_is_pending` | Cancel happy path |
| `cancel_order_should_throw_when_order_is_not_pending` | Cancel guard check |
| `promote_pending_to_processing_should_update_all_pending_orders` | Scheduler batch |
| `promote_pending_to_processing_should_return_zero_when_no_pending_orders` | Empty batch |

### `OrderControllerTest` (10 tests)
| Test | Covers |
|---|---|
| `create_order_should_return_201_with_pending_status` | POST happy path, customerId/addressId in response |
| `create_order_should_return_400_when_payload_is_invalid` | Validation rejection |
| `get_order_should_return_200_when_found` | GET happy path |
| `get_order_should_return_404_when_not_found` | 404 error response |
| `list_orders_should_return_all_when_no_filter` | GET list no filter |
| `list_orders_should_filter_by_status` | GET list with status param |
| `update_status_should_return_200_with_updated_status` | PUT status update |
| `update_status_should_return_400_when_cancelling_non_pending_order` | CANCELLED guard via PUT endpoint |
| `cancel_order_should_return_200_with_cancelled_status_when_pending` | DELETE happy path |
| `cancel_order_should_return_400_when_order_is_not_pending` | DELETE guard rejection |

### `CustomerServiceTest` (8 tests)
| Test | Covers |
|---|---|
| `create_customer_should_return_saved_profile` | Customer create happy path |
| `create_customer_should_throw_when_email_already_exists` | Duplicate email on create |
| `update_customer_should_throw_when_email_already_used_by_another_customer` | Duplicate email on update (other customer) |
| `get_customer_by_id_should_throw_when_customer_missing` | CustomerNotFoundException |
| `add_address_should_append_new_address_for_customer` | Address creation |
| `remove_address_should_throw_when_address_missing_for_customer` | Idempotent delete (no-op when address missing) |
| `remove_address_should_soft_delete_when_address_exists` | Soft-delete sets `deleted=true` and `deletedAt` |
| `list_addresses_should_return_all_active_addresses_for_customer` | Only non-deleted addresses returned |

### `CustomerControllerTest` (6 tests)
| Test | Covers |
|---|---|
| `create_customer_should_return_201` | POST customer happy path |
| `create_customer_should_return_409_when_email_already_exists` | 409 Conflict on duplicate email |
| `get_customer_should_return_404_when_missing` | 404 error response |
| `add_address_should_return_201` | POST address happy path |
| `remove_address_should_return_204` | DELETE address happy path |
| `remove_address_should_return_204_even_when_address_not_found` | Idempotent DELETE — always 204 |

### `OrderStatusSchedulerTest` (2 tests)
| Test | Covers |
|---|---|
| `promote_pending_orders_should_invoke_service_once` | Service delegation |
| `promote_pending_orders_should_handle_service_exception_without_rethrowing` | Resilience |

---

## 7. How to Extend

### Add a new order endpoint
1. Add method to `OrderService` with `@Transactional`
2. Add handler to `OrderController` with the appropriate mapping annotation
3. Add corresponding test cases in `OrderControllerTest` and `OrderServiceTest`

### Add a new customer endpoint
1. Add method to `CustomerService` with `@Transactional`
2. Add handler to `CustomerController`
3. Add tests in `CustomerControllerTest` and `CustomerServiceTest`

### Add a new field to Order
1. Add column to `Order.java` entity with `@Column(name = "snake_case_name")`
2. Add field to `OrderResponse.java` DTO
3. Update `map_to_response()` in `OrderService`
4. If it's an input field, add to `CreateOrderRequest.java`

### Add a new exception type
1. Create the exception class extending `RuntimeException` in `exception/`
2. Add a handler method in `GlobalExceptionHandler` returning the appropriate HTTP status
3. Throw from the relevant service method

### Switch to PostgreSQL for local dev
1. Create a local PostgreSQL database named `orderdb`
2. Set environment variables `DB_USERNAME` and `DB_PASSWORD`
3. Run with `--spring.profiles.active=prod`
4. On first run, temporarily change `ddl-auto=validate` to `ddl-auto=create` to create schema, then revert

