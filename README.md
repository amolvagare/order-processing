# Order Processing System

A backend REST API for an E-commerce Order Processing System built with **Java 17 + Spring Boot 3.2**.

Customers can place orders, track their status, and cancel pending orders.
A background job automatically promotes `PENDING` orders to `PROCESSING` every 5 minutes.

---

## Quick Start

### Prerequisites

| Tool | Version |
|---|---|
| Java | 17+ |
| Maven | 3.8+ |

> **No Maven installed?** Use the one bundled in IntelliJ IDEA, or the cached wrapper at  
> `~/.m2/wrapper/dists/apache-maven-3.9.5-bin/.../bin/mvn`

### Run locally

```bash
# Run all tests
mvn clean test

# Start the application (H2 in-memory database)
mvn spring-boot:run
```

| URL | Description |
|---|---|
| `http://localhost:8080/api/orders` | REST API base |
| `http://localhost:8080/swagger-ui.html` | Interactive API documentation |
| `http://localhost:8080/h2-console` | Database console (dev only) |

> H2 console credentials: JDBC URL `jdbc:h2:mem:orderdb` · User `sa` · Password *(empty)*

### Run with PostgreSQL (production)

```bash
DB_USERNAME=myuser DB_PASSWORD=mypass mvn spring-boot:run -Dspring.profiles.active=prod
```

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/orders` | Place a new order |
| `GET` | `/api/orders/{id}` | Get order by ID |
| `GET` | `/api/orders?status=PENDING` | List all orders (optional status filter) |
| `PUT` | `/api/orders/{id}/status` | Update order status |
| `DELETE` | `/api/orders/{id}` | Cancel order *(PENDING only)* |

### Order status lifecycle

```
[CREATE] ──► PENDING ──► PROCESSING ──► SHIPPED ──► DELIVERED
               │          ▲
               │          └── background job (every 5 min)
               └──► CANCELLED  (cancel endpoint — PENDING only)
```

### Example: Place an order

```http
POST /api/orders
Content-Type: application/json

{
  "customerName": "Alice Smith",
  "customerEmail": "alice@example.com",
  "items": [
    { "productName": "Laptop", "quantity": 1, "unitPrice": 999.99 },
    { "productName": "Mouse",  "quantity": 2, "unitPrice": 29.99  }
  ]
}
```

Response `201 Created` — `totalAmount` is computed server-side (`1059.97`).

---

## Project Structure

```
src/main/java/com/ecommerce/orderprocessing/
├── controller/      OrderController.java          ← HTTP layer
├── service/         OrderService.java             ← All business logic
├── repository/      OrderRepository.java          ← JPA data access
├── model/           Order.java, OrderItem.java    ← JPA entities
├── dto/             Request & Response DTOs       ← API contract
├── enums/           OrderStatus.java              ← Status enum
├── scheduler/       OrderStatusScheduler.java     ← 5-min background job
├── exception/       Exceptions + GlobalHandler    ← Error handling
└── OrderProcessingApplication.java               ← Entry point
```

---

## Documentation

| Document | Read this to understand… |
|---|---|
| [PLAN.md](PLAN.md) | *Why* — language choice, data model design, API decisions, status lifecycle |
| [IMPLEMENTATION.md](IMPLEMENTATION.md) | *How* — every class explained, config reference, test coverage, how to extend |

---

## Copilot / AI Integration

This project includes GitHub Copilot context files for consistent AI-assisted development.

### Auto-loaded context (VS Code)

[`.github/copilot-instructions.md`](.github/copilot-instructions.md) is automatically loaded by
GitHub Copilot in VS Code. It provides the project's coding conventions, naming rules,
architecture patterns, and what to avoid — so Copilot suggestions conform to the project style
from the first keystroke.

### Prompt files for common tasks

Reusable prompts in `.github/prompts/` — open Copilot Chat, type `/`, and select the prompt:

| Prompt | Use when you need to… |
|---|---|
| [`add-endpoint.prompt.md`](.github/prompts/add-endpoint.prompt.md) | Add a new REST endpoint (service + controller + tests) |
| [`add-entity.prompt.md`](.github/prompts/add-entity.prompt.md) | Add a new JPA entity (model + repository + DTOs) |
| [`write-tests.prompt.md`](.github/prompts/write-tests.prompt.md) | Write unit or MockMvc tests for a feature |
| [`add-scheduler.prompt.md`](.github/prompts/add-scheduler.prompt.md) | Add a new background scheduled job |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Persistence | Spring Data JPA + Hibernate 6 |
| Database (dev/test) | H2 in-memory |
| Database (prod) | PostgreSQL 17 |
| Build | Maven |
| Boilerplate | Lombok |
| API Docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5 + Mockito + MockMvc |

---

## Tests

```bash
mvn clean test
```

```
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
```

| Test class | Type | Tests |
|---|---|---|
| `OrderServiceTest` | Unit (Mockito) | 9 |
| `OrderControllerTest` | MockMvc | 8 |
| `OrderStatusSchedulerTest` | Unit (Mockito) | 2 |
