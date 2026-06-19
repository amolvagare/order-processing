---
applyTo: "order-processing/**"
---

# GitHub Copilot Instructions — Order Processing System

## Project Context

This is a **Spring Boot 3.2 / Java 17 REST API** for an e-commerce order processing backend.
It handles order creation, retrieval, status management, and a scheduled background job that
automatically promotes `PENDING` orders to `PROCESSING` every 5 minutes.

**Swagger UI**: `http://localhost:8080/swagger-ui.html`  
**H2 Console** (dev only): `http://localhost:8080/h2-console`  
**Base package**: `com.ecommerce.orderprocessing`

---

## Coding Conventions

### Naming
- **Methods and local variables**: `snake_case` (e.g., `create_order`, `pending_orders`, `item_req`)
- **Class names**: `PascalCase` (e.g., `OrderService`, `GlobalExceptionHandler`)
- **Constants**: `UPPER_SNAKE_CASE`
- **Database columns**: `snake_case` via `@Column(name = "column_name")`
- **Java field names on entities/DTOs**: `camelCase` (Jackson/Lombok compatibility) mapped to snake_case DB columns

### Function design
- Each method has a **single responsibility** — avoid multi-purpose methods
- Keep methods short — extract helpers for repeated logic (e.g., `find_order_or_throw`)
- No nested conditionals — use early returns or guard clauses

### Logging
- Use `@Slf4j` (Lombok) on every class
- `log.info(...)` on controller methods (log the request)
- `log.debug(...)` on service methods (log parameters)
- `log.error(...)` on exception handlers and scheduler error paths

### Comments
- Add a comment above each method explaining its purpose
- Add inline comments for non-obvious logic (e.g., why `total_amount` is computed server-side)
- Add section divider comments for private helpers: `// --- Private helpers ---`

---

## Architecture Patterns

### Layered Architecture (strict)
```
Controller → Service → Repository → Database
```
- Controllers: HTTP only — receive, validate, delegate, respond. Zero business logic.
- Services: All business logic and `@Transactional` boundaries.
- Repositories: JPA interfaces only — no query logic in service.
- DTOs separate the API contract from JPA entities — never return entity objects directly.

### Entity → Response Mapping
Always map entities to response DTOs in the service layer using private helper methods:
```java
private OrderResponse map_to_response(Order order) { ... }
private OrderItemResponse map_item_to_response(OrderItem item) { ... }
```

### Exception Handling
- Throw domain-specific exceptions (`OrderNotFoundException`, `OrderCancellationException`) from the service.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) catches and formats all exceptions.
- New exception types should extend `RuntimeException` and take the relevant ID + context in the constructor.

---

## Key Files

| File | Role |
|---|---|
| `OrderService.java` | All business logic — the most important file |
| `OrderController.java` | REST endpoints — delegates to service |
| `OrderStatusScheduler.java` | 5-min `@Scheduled` job |
| `GlobalExceptionHandler.java` | Centralized error handling |
| `OrderRepository.java` | JPA data access |
| `application.yml` | H2 dev config |
| `application-prod.yml` | PostgreSQL prod config |

---

## Testing Conventions

- **Unit tests** (`@ExtendWith(MockitoExtension.class)`): mock the repository, test service logic directly
- **Controller tests** (`@WebMvcTest` + `MockMvc`): mock the service, test HTTP contract
- Test method names describe the scenario: `cancel_order_should_throw_when_order_is_not_pending`
- Use `assertThat` from AssertJ (not JUnit `assertEquals`)
- Always test both the happy path and the error path for each feature
- Import `GlobalExceptionHandler` in controller tests with `@Import(GlobalExceptionHandler.class)`

---

## Dependencies in `pom.xml`

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST controllers, embedded Tomcat |
| `spring-boot-starter-data-jpa` | Hibernate ORM |
| `spring-boot-starter-validation` | `@Valid`, `@NotBlank`, etc. |
| `h2` (runtime) | In-memory DB for dev/test |
| `postgresql` (runtime) | Production DB driver |
| `lombok` | Boilerplate reduction |
| `springdoc-openapi-starter-webmvc-ui:2.3.0` | Swagger UI |
| `spring-boot-starter-test` (test) | JUnit 5, Mockito, MockMvc |

---

## What to Avoid

- Do **not** add business logic to controllers
- Do **not** return JPA entities directly from controllers — always use DTOs
- Do **not** put `@Transactional` on controller methods
- Do **not** add `print` statements — use `log.debug` or `log.info`
- Do **not** hardcode database credentials — use environment variables (`${DB_USERNAME}`)
- Do **not** call `orderRepository` directly from the scheduler — go through the service
- Do **not** let exceptions propagate out of the scheduler method (wrap in try-catch)
