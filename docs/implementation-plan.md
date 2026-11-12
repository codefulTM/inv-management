# Inventory Management — Implementation Plans

## 1. Goal

Build a backend API that helps a small shop track products, stock, and orders.

**In scope**

- Product catalog (create, read, update, delete, search)
- Many warehouses, stock per warehouse
- Stock in / stock out with a full movement history
- Suppliers and purchase orders (receive goods)
- Sales orders that take stock out
- Low stock alerts
- Login with roles (admin, staff, viewer)
- Audit log for every change

**Out of scope (for now)**

- Frontend UI (use Swagger UI and Postman for demo)
- Payment, shipping, invoicing
- Multi-tenant / multi-company
- Mobile app

---

## 2. Tech stack

Already in `pom.xml`:

- Java 17, Spring Boot 4.1.2, Maven wrapper
- Spring Web MVC, Spring Data JPA
- Test starters

To add during the plan:

| Need | Choice | Why |
|---|---|---|
| Database (dev) | PostgreSQL in Docker | Real database, same as production |
| Database (test) | Testcontainers PostgreSQL | Tests run on a real database |
| Schema migration | Flyway | Versioned SQL, no `ddl-auto` |
| Validation | `spring-boot-starter-validation` | `@NotBlank`, `@Positive` |
| Security | Spring Security + JWT (jjwt) | Stateless auth for an API |
| API docs | springdoc-openapi | Free Swagger UI |
| Metrics | Actuator + Micrometer | Prove the app is healthy |
| Build/CI | GitHub Actions | Green checks on every push |
| Deploy | Dockerfile + docker compose, then Fly.io or Render | Live demo link |

Do not add Kafka, Redis, or microservices.

---

## 3. Data model

The whole database is created by Flyway scripts, starting with `V1__init.sql`. Hibernate only checks the tables (`ddl-auto=validate`); it never creates them.

There are eleven tables. Users live in `app_user`, where each row has a username, a BCrypt password hash, a role (ADMIN, STAFF or VIEWER) and an enabled flag. Products live in `product`, with a unique SKU, a name, an optional category, a unit, a cost price, a sell price, a low stock threshold and an `active` flag. Categories are just id and name. Warehouses are stored in `warehouse` with a unique code, a name and an address. Suppliers are stored in `supplier` with a name, email and phone.

Stock is the interesting part. Each row in `stock_item` links one product to one warehouse, and the pair must be unique. It holds the current quantity and a `version` number for optimistic locking. Every change to that quantity also writes a row in `stock_movement`, which records the product, the warehouse, the type of change, the quantity delta, a reason, an optional reference (for example the purchase order that added the goods), who did it and when. `stock_movement` is append only: we never update or delete a row.

Orders come in two kinds. A purchase order has a unique code, a supplier, a status and dates, plus lines in `purchase_order_line` that list the product, quantity and unit cost. A sales order has a unique code, a customer name, a status, an idempotency key and a creation time, plus lines in `sales_order_line` that point to a product and a warehouse and carry a quantity and a unit price. Both order tables use a small status machine, and only the service layer may move an order from one status to the next.

The last table is `audit_log`, which records the actor, the action, the entity type and id, the old and new values as JSON, and the time. It is filled by the service layer on purpose, not by database triggers, so the code stays easy to read and test.

A few rules apply everywhere. Money is always `BigDecimal` with scale 2, never `double`. Time is `Instant` stored in UTC. Quantities are whole numbers, and `stock_item` has a `CHECK (quantity >= 0)` constraint so the database itself refuses negative stock. Current stock is kept in `stock_item.quantity` as a fast cache, while `stock_movement` is the permanent history, so a stock change is one update plus one insert in the same transaction.

---

## 4. API design

Base path: `/api/v1`.

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/auth/login` | public | Get a JWT |
| GET | `/products` | VIEWER | List with paging, search by name/sku, filter by category |
| POST | `/products` | STAFF | Create product |
| GET | `/products/{id}` | VIEWER | Get one |
| PUT | `/products/{id}` | STAFF | Update |
| DELETE | `/products/{id}` | ADMIN | Soft delete (`active = false`) |
| GET | `/warehouses` | VIEWER | List |
| POST | `/warehouses` | ADMIN | Create |
| GET | `/stock?warehouseId=&productId=` | VIEWER | Current stock |
| POST | `/stock/adjust` | STAFF | Manual stock in/out with a reason |
| GET | `/stock/movements?productId=&from=&to=` | VIEWER | Movement history |
| POST | `/purchase-orders` | STAFF | Create a draft PO |
| POST | `/purchase-orders/{id}/order` | STAFF | Change status to ORDERED |
| POST | `/purchase-orders/{id}/receive` | STAFF | Receive goods, add stock |
| POST | `/sales-orders` | STAFF | Create and confirm a sale, needs `Idempotency-Key` header |
| GET | `/alerts/low-stock` | VIEWER | Products below threshold |
| GET | `/reports/stock-value` | VIEWER | Total stock value by warehouse |
| GET | `/audit-logs` | ADMIN | Recent changes |

Response rules:

- Use JSON. Errors use `ProblemDetail` (RFC 9457): `type`, `title`, `status`, `detail`, `instance`.
- Lists always return a page object: `content`, `page`, `size`, `totalElements`.
- Never return entities directly. Use `record` DTOs.
- Timestamps in ISO-8601.

---

## 5. Testing plan

| Level | Tool | What to test |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service rules, state machines, stock math |
| Slice | `@WebMvcTest` | Status codes, validation errors, JSON shape |
| Data | `@DataJpaTest` + Testcontainers | Queries, constraints, unique keys |
| Integration | `@SpringBootTest` + MockMvc | Login → adjust stock → check history |
| Concurrency | `ExecutorService` + `CountDownLatch` | Last-unit race, double submit, double receive |
| Load | JMeter or Gatling | p50/p95 latency, throughput under 200 concurrent users |

Write the concurrency tests first when possible. They are the fastest way to find design bugs.

---

## 6. Package layout

```
com.codefultm.inventory_management
├── common/          # errors, paging helpers, base entity
├── security/        # JWT filter, security config, user details
├── product/         # Product, Category
├── warehouse/       # Warehouse
├── stock/           # StockItem, StockMovement, StockService
├── supplier/        # Supplier
├── order/           # PurchaseOrder, SalesOrder, state machines
├── report/          # queries for dashboard and CSV
├── audit/           # AuditLog
└── config/          # OpenAPI, Jackson, clock
```

Rules:

- Controller → Service → Repository. Never call a repository from a controller.
- One service per use case group. Keep methods short.
- Use constructor injection. No field `@Autowired`.
- Put business rules in the service, not in the controller or the entity setter.

---

## 7. Definition of done (for the whole project)

- [ ] All milestones finished and tests green in CI
- [ ] Live demo URL in the README
- [ ] Swagger UI reachable on the demo
- [ ] Concurrency test and idempotency test both pass and are mentioned in the README
- [ ] Benchmark numbers in the README
- [ ] `docker compose up` works on a clean machine
- [ ] No secrets in the repo (`.env` is ignored, keys from env vars)

---


