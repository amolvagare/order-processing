# Order Processing System — Initial Plan

## 1. Objective

Build the backend for an E-commerce Order Processing System that allows customers to place orders, track their status, and support basic order operations.

The system manages two domains:
- **Customer domain**: Customer profiles with unique email, and multiple addresses per customer (soft-deletable).
- **Order domain**: Orders tied to an existing customer and one of their addresses, with a status lifecycle and a background promotion job.

---

## 2. Language & Framework Decision

### Options Considered

| Criteria | Java + Spring Boot | Python + FastAPI |
|---|---|---|
| Background job (5 min) | `@Scheduled` built-in — zero extra setup | APScheduler — additional dependency, threading concerns |
| ORM | Spring Data JPA + Hibernate — near-zero boilerplate | SQLAlchemy — more manual, explicit session mgmt |
| Type safety | Compile-time errors caught early | Runtime errors only |
| REST API structure | Spring MVC — annotation-driven, declarative | FastAPI — good but less structured for large codebases |
| Testing | JUnit 5 + Mockito + MockMvc — excellent | pytest + httpx — good but less integrated |
| Enterprise fit | Industry standard for e-commerce backends | Better for scripting, data APIs, ML services |

### Decision: **Java 17 + Spring Boot 3.x**

**Rationale:**
- The `@Scheduled(fixedRate = 300_000)` annotation handles the 5-minute background job natively — no extra dependencies or thread management.
- Spring Data JPA's `JpaRepository` provides CRUD and custom queries out of the box.
- Strong compile-time type checking reduces runtime bugs.
- Lombok eliminates boilerplate (getters/setters/builders/constructors) without sacrificing clarity.
- The full Spring ecosystem (Web, JPA, Validation, Test) provides a coherent, well-tested toolkit.

---

## 3. Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Java 17 | LTS release with records, text blocks, sealed classes |
| Framework | Spring Boot 3.2 | Auto-configuration, embedded Tomcat, starter dependencies |
| Persistence | Spring Data JPA + Hibernate 6 | ORM, JPQL queries, schema management |
| Database (dev/test) | H2 (in-memory) | Zero-setup, schema auto-created on startup, torn down after tests |
| Database (prod) | PostgreSQL 17 | Production-grade relational DB |
| Build tool | Maven | Dependency management, test runner, fat-JAR packaging |
| Boilerplate reduction | Lombok | `@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j` |
| API documentation | springdoc-openapi 2.3 | Auto-generates Swagger UI from annotations |
| Validation | spring-boot-starter-validation | `@NotBlank`, `@Email`, `@Min`, `@DecimalMin` on DTOs |
| Unit tests | JUnit 5 + Mockito | Service-layer unit tests with mocked repository |
| Integration tests | MockMvc (Spring MVC Test) | HTTP-layer tests without a running server |

---

## 4. Data Model Design

### Table: `customers`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGINT | PK, auto-increment | Surrogate key |
| `customer_name` | VARCHAR | NOT NULL | Java field: `name` |
| `customer_email` | VARCHAR | NOT NULL, UNIQUE | Java field: `email` |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | Set once on insert |
| `updated_at` | TIMESTAMP | NOT NULL, auto-updated | Updated on every save |

### Table: `customer_addresses`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGINT | PK, auto-increment | |
| `customer_id` | BIGINT | FK → customers.id, NOT NULL | |
| `address_label` | VARCHAR | nullable | Java field: `label` |
| `address_line1` | VARCHAR | NOT NULL | |
| `address_line2` | VARCHAR | nullable | |
| `city` | VARCHAR | NOT NULL | |
| `state` | VARCHAR | NOT NULL | |
| `postal_code` | VARCHAR | NOT NULL | |
| `country` | VARCHAR | NOT NULL | |
| `is_deleted` | BOOLEAN | NOT NULL, default false | Java field: `deleted` — soft-delete flag |
| `deleted_at` | TIMESTAMP | nullable | Set to `now()` on soft-delete |

### Table: `orders`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGINT | PK, auto-increment | Surrogate key |
| `customer_id` | BIGINT | FK → customers.id, NOT NULL | |
| `customer_address_id` | BIGINT | FK → customer_addresses.id, NOT NULL | Must reference a non-deleted address |
| `status` | VARCHAR | NOT NULL, default PENDING | Stored as enum string |
| `total_amount` | DECIMAL(10,2) | NOT NULL | Computed server-side: Σ(qty × unit_price) |
| `created_at` | TIMESTAMP | NOT NULL, auto-set | Set once on insert via `@CreationTimestamp` |
| `updated_at` | TIMESTAMP | NOT NULL, auto-updated | Updated on every save via `@UpdateTimestamp` |

### Table: `order_items`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGINT | PK, auto-increment | |
| `order_id` | BIGINT | FK → orders.id, NOT NULL | Cascade delete with parent order |
| `product_name` | VARCHAR | NOT NULL | Free-text product identifier |
| `quantity` | INT | NOT NULL, ≥ 1 | |
| `unit_price` | DECIMAL(10,2) | NOT NULL, > 0 | Per-unit price |

### Relationships
```
customers 1 ────── * customer_addresses   (soft-delete on address)
customers 1 ────── * orders               (@ManyToOne from Order)
customer_addresses 1 ── * orders          (@ManyToOne from Order)
orders    1 ────── * order_items          (cascade ALL, orphanRemoval = true)
```

### Order Status Lifecycle

```
                ┌─────────────────────────────┐
                │  Background job (every 5m)  │
                └──────────────┬──────────────┘
                               │
  [CREATE] ──► PENDING ──────► PROCESSING ──► SHIPPED ──► DELIVERED
                  │
                  └──► CANCELLED  (manual — only from PENDING)
```

**Rules:**
- New orders always start as `PENDING`.
- Orders must reference an existing customer and one of their non-deleted addresses.
- The scheduler automatically moves `PENDING → PROCESSING` every 5 minutes.
- Manual status updates via `PUT /api/orders/{id}/status` accept any target status.
- `DELETE /api/orders/{id}` (cancel) is only allowed when current status is `PENDING`; otherwise returns `400`.
- `CANCELLED` is a terminal state — no further transitions.

---

## 5. API Design

### Customer Endpoints

| # | Method | Path | Request Body | Response | Description |
|---|---|---|---|---|---|
| 1 | `POST` | `/api/customers` | `CustomerRequest` | `201 CustomerResponse` | Register a new customer |
| 2 | `GET` | `/api/customers/{id}` | — | `200 CustomerResponse` | Get customer by ID |
| 3 | `GET` | `/api/customers` | — | `200 List<CustomerResponse>` | List all customers |
| 4 | `PUT` | `/api/customers/{id}` | `CustomerRequest` | `200 CustomerResponse` | Update customer name/email |
| 5 | `POST` | `/api/customers/{id}/addresses` | `AddressRequest` | `201 AddressResponse` | Add address to customer |
| 6 | `GET` | `/api/customers/{id}/addresses` | — | `200 List<AddressResponse>` | List active addresses |
| 7 | `DELETE` | `/api/customers/{id}/addresses/{addressId}` | — | `204 No Content` | Soft-delete an address (idempotent) |

### Order Endpoints

| # | Method | Path | Request Body | Response | Description |
|---|---|---|---|---|---|
| 1 | `POST` | `/api/orders` | `CreateOrderRequest` | `201 OrderResponse` | Place a new order |
| 2 | `GET` | `/api/orders/{id}` | — | `200 OrderResponse` | Get order by ID |
| 3 | `GET` | `/api/orders?status=` | — | `200 List<OrderResponse>` | List all orders (optional status filter) |
| 4 | `PUT` | `/api/orders/{id}/status` | `UpdateStatusRequest` | `200 OrderResponse` | Manually update status |
| 5 | `DELETE` | `/api/orders/{id}` | — | `200 OrderResponse` | Cancel order (PENDING only) |

### Error Response Shape

All errors return a consistent JSON body:
```json
{
  "timestamp": "2026-06-19T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Order not found with id: 99"
}
```

### Key Design Decisions
- `total_amount` is **computed server-side** — clients never supply it, preventing manipulation.
- Orders reference a `customerId` and `addressId` — customer name/email are resolved at order creation time from the `Customer` entity.
- Address soft-delete prevents physical removal; `findByIdAndCustomerIdAndDeletedFalse` ensures orders cannot be placed against deleted addresses.
- Cancel uses `DELETE` verb semantically (removes the order from active processing).
- Status filter on `GET /api/orders` uses an optional `@RequestParam` — absent = return all.
- DTOs decouple the API contract from JPA entities — soft-delete fields (`deleted`, `deletedAt`) are never exposed in `AddressResponse`.

---

## 6. Background Job Design

```
OrderStatusScheduler
  └── @Scheduled(fixedRate = 300_000)   // 5 minutes in milliseconds
        └── promote_pending_orders()
              └── orderService.promote_pending_to_processing()
                    ├── orderRepository.findByStatus(PENDING)
                    ├── order.setStatus(PROCESSING) for each
                    └── orderRepository.saveAll(...)
```

**Safety considerations:**
- The scheduler catches all exceptions internally — if a DB failure occurs, the scheduled thread does not die.
- `promote_pending_to_processing()` is `@Transactional` — all updates succeed or all roll back.
- Returns a count of promoted orders, enabling the scheduler to log meaningful output.

---

## 7. Testing Strategy

| Test Class | Type | Tests | What it covers |
|---|---|---|---|
| `OrderServiceTest` | Unit | 13 | All order service methods including new customer/address validation paths |
| `OrderControllerTest` | MockMvc | 10 | All 5 order HTTP endpoints — happy paths + error cases (404, 400, 409) |
| `CustomerServiceTest` | Unit | 8 | Customer CRUD, address add/remove/list, email uniqueness, soft-delete |
| `CustomerControllerTest` | MockMvc | 6 | Customer and address HTTP endpoints — happy paths + error cases |
| `OrderStatusSchedulerTest` | Unit | 2 | Scheduler delegates to service; exceptions swallowed gracefully |

**Total test count: 39 tests (0 failures)**

---

## 8. Implementation Phases

| Phase | Work |
|---|---|
| 1 — Bootstrap | `pom.xml`, `application.yml`, `OrderProcessingApplication.java`, `OrderStatus.java` |
| 2 — Customer data layer | `Customer.java`, `CustomerAddress.java`, `CustomerRepository.java`, `CustomerAddressRepository.java` |
| 3 — Order data layer | `Order.java`, `OrderItem.java`, `OrderRepository.java` |
| 4 — DTOs | `CustomerRequest`, `CustomerResponse`, `AddressRequest`, `AddressResponse`, `CreateOrderRequest`, `OrderItemRequest`, `UpdateStatusRequest`, `OrderResponse`, `OrderItemResponse` |
| 5 — Exceptions | `OrderNotFoundException`, `OrderCancellationException`, `CustomerNotFoundException`, `CustomerEmailAlreadyExistsException`, `AddressNotFoundException`, `GlobalExceptionHandler` |
| 6 — Customer service | `CustomerService.java` (all customer/address business logic) |
| 7 — Order service | `OrderService.java` (all order business logic, references Customer + Address repos) |
| 8 — Controllers | `CustomerController.java`, `OrderController.java` (HTTP layers, delegate to services) |
| 9 — Scheduler | `OrderStatusScheduler.java` (`@Scheduled` wrapper) |
| 10 — Tests | `OrderServiceTest`, `OrderControllerTest`, `CustomerServiceTest`, `CustomerControllerTest`, `OrderStatusSchedulerTest` |

---

## 9. Key Decisions Log

| Decision | Choice | Reason |
|---|---|---|
| Database for dev/test | H2 in-memory | Zero setup; schema auto-managed; wiped clean between test runs |
| Database for prod | PostgreSQL | Production-grade, widely supported |
| `total_amount` source | Server-computed | Prevents client-side price manipulation |
| Order references customer | FK to `customers.id` | Avoids duplicating customer data; enables customer profile updates to reflect across orders |
| Address ownership check | `findByIdAndCustomerIdAndDeletedFalse` | Ensures addresses belong to the customer and are active at order creation time |
| Address deletion strategy | Soft-delete (`is_deleted`, `deleted_at`) | Preserves historical address data for existing orders; idempotent DELETE semantics |
| Cancel endpoint verb | `DELETE` | Semantically correct — removes the order from active processing |
| Status storage | `EnumType.STRING` | Human-readable in DB; survives enum reordering |
| Exception handling | `@RestControllerAdvice` | Single place for all error formatting |
| Scheduler resilience | Try-catch in scheduler | Prevents scheduler thread death on transient errors |
| Test scope | Unit + MockMvc | Unit tests verify logic; MockMvc verifies HTTP contract without full server |
