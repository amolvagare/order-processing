# AGENTS.md

## Big picture
- This codebase is a Spring Boot 3.2 / Java 17 REST API under `src/main/java/com/ecommerce/orderprocessing`.
- Keep the layering strict: controller → service → repository → database. Each controller stays HTTP-only; services own all business logic and transaction boundaries.
- The API comprises two main domains:
  - **Customer domain** (`CustomerController`, `CustomerService`): Manages customer profiles and their addresses via `/api/customers`.
  - **Order domain** (`OrderController`, `OrderService`): Manages orders via `/api/orders`; orders reference a customer and one of their addresses.
- The core flow is: request DTOs (`dto/`) → service mapping → JPA entities (`model/`) → repositories → response DTO mapping back in services.
- Do not return JPA entities from controllers; always map through helpers like `map_to_response(...)`, `map_customer_to_response(...)`, `map_item_to_response(...)` in the service layer.

## Domain rules you must preserve
- **Customer lifecycle**: Customers are registered via `POST /api/customers` with unique email. Each customer can own multiple addresses via `POST /api/customers/{id}/addresses`.
- **Customer updates**: `PUT /api/customers/{id}` updates name/email and still enforces unique email via `ensure_email_is_unique_for_customer(...)` in `CustomerService`.
- **Order lifecycle**: New orders always start as `PENDING`; see `OrderService#create_order`. Orders require a valid `customer_id` and `customer_address_id` (the address must belong to that customer).
- **Address validation**: `OrderService` calls `find_customer_address_or_throw(customer_id, address_id)` and resolves via `findByIdAndCustomerIdAndDeletedFalse(...)`, so orders cannot use soft-deleted addresses.
- **Address removal**: `DELETE /api/customers/{id}/addresses/{addressId}` is a soft delete (sets `is_deleted=true`, `deleted_at=now`) and is idempotent: missing/already-deleted addresses still return `204`.
- `totalAmount` is computed server-side from item `quantity * unitPrice`; clients never send it.
- `DELETE /api/orders/{id}` is a cancel operation, not a delete-from-db operation: it changes status to `CANCELLED` only when the current status is `PENDING`.
- Manual `PUT /api/orders/{id}/status` accepts any target `OrderStatus`; there is no transition validator beyond cancel-specific rules.
- The background job in `scheduler/OrderStatusScheduler.java` promotes `PENDING` orders to `PROCESSING` every 5 minutes by calling `orderService.promote_pending_to_processing()`.
- The scheduler must never talk to `OrderRepository` directly and must swallow/log exceptions so scheduled execution continues.

## Code conventions specific to this repo
- Methods and local variables use `snake_case` (`create_order`, `find_order_or_throw`, `pending_orders`); entity/DTO fields stay `camelCase`.
- Add `@Slf4j` to classes; controllers log requests with `log.info(...)`, services log parameters with `log.debug(...)`, exception/scheduler failure paths use `log.error(...)`.
- Add a short comment above each method. Private helper sections are grouped under `// --- Private helpers ---`.
- DB column names are explicit `snake_case` via `@Column(name = ...)`; see `model/Order.java` and `model/OrderItem.java`.

## Persistence and configuration
- **Customer** maps to table `customers` with fields: `id`, `customer_name`, `customer_email` (unique), `created_at`, `updated_at`.
- **CustomerAddress** maps to `customer_addresses` with fields: `id`, `customer_id` (FK), `address_label`, `address_line1`, `address_line2`, `city`, `state`, `postal_code`, `country`, `is_deleted`, `deleted_at`. One customer can own many addresses.
- **Order** maps to table `orders` and **OrderItem** to `order_items`; `Order.status` uses `EnumType.STRING`.
- **Order relationships**: `Order` has `@ManyToOne` to both `Customer` and `CustomerAddress`; both are `fetch = FetchType.LAZY, optional = false`. The association ensures orders are tied to a customer and their chosen shipping/billing address.
- `Order.items` is `@OneToMany(mappedBy = "order", cascade = ALL, orphanRemoval = true, fetch = LAZY)`; remember to set the back-reference (`item.setOrder(order)`) before saving.
- **Repository methods**: `CustomerRepository` provides `findByEmail(String)` to check for unique emails. `CustomerAddressRepository` provides `findByIdAndCustomerId(address_id, customer_id)` (remove flow), `findByIdAndCustomerIdAndDeletedFalse(address_id, customer_id)` (order/address validation), and `findByCustomerIdAndDeletedFalse(customer_id)` (active address listing).
- Default config in `src/main/resources/application.yml` uses H2 in-memory with `spring.jpa.hibernate.ddl-auto=create-drop` and DEBUG logging for `com.ecommerce.orderprocessing`.
- Production config in `src/main/resources/application-prod.yml` targets PostgreSQL and expects `DB_USERNAME` / `DB_PASSWORD`; `ddl-auto=validate` means schema creation is not handled in prod.
- Swagger UI is available at `/swagger-ui.html`; H2 console is `/h2-console` in the default profile.

## Testing and workflow
- Main tests live in `src/test/java/com/ecommerce/orderprocessing`: `OrderServiceTest`, `OrderControllerTest`, `CustomerServiceTest`, `CustomerControllerTest`, and `OrderStatusSchedulerTest`.
- Service tests use `@ExtendWith(MockitoExtension.class)` and mock repositories; controller tests use `@WebMvcTest(ControllerClass.class)` + `@Import(GlobalExceptionHandler.class)` + mocked service.
- Follow the existing test naming style like `cancel_order_should_throw_when_order_is_not_pending` and `create_customer_should_throw_when_email_already_exists`; use AssertJ `assertThat(...)` / `assertThatThrownBy(...)`.
- Standard commands from the docs are `mvn clean test` and `mvn spring-boot:run`.
- This repository does **not** include `mvnw`; in this workspace `mvn` was not on `PATH`, so agents may need IntelliJ's bundled Maven or a local Maven install before running commands.
- For PowerShell prod runs, set env vars first, then start with the prod profile, e.g. `$env:DB_USERNAME="myuser"; $env:DB_PASSWORD="mypass"; mvn spring-boot:run "-Dspring.profiles.active=prod"`.

## High-value files to read before changing behavior
- `service/OrderService.java` — order business rules, customer/address validation, mapping helpers, transaction boundaries.
- `service/CustomerService.java` — customer/address lifecycle, email uniqueness enforcement, mapping helpers.
- `controller/OrderController.java` — order endpoint signatures and logging shape.
- `controller/CustomerController.java` — customer/address endpoint signatures and logging shape.
- `exception/GlobalExceptionHandler.java` — canonical error response JSON, handles `OrderNotFoundException`, `OrderCancellationException`, `CustomerNotFoundException`, `CustomerEmailAlreadyExistsException`, `AddressNotFoundException`, `MethodArgumentNotValidException`, plus a catch-all `Exception` handler.
- `scheduler/OrderStatusScheduler.java` — scheduled integration pattern.
- `model/Order.java`, `model/Customer.java`, and `model/CustomerAddress.java` — entity relationships, cascade behavior, and address soft-delete fields.
- `README.md`, `PLAN.md`, and `IMPLEMENTATION.md` — rationale, lifecycle rules, and extension guidance.
- `.github/prompts/` contains reusable task prompts that mirror the current architecture.

