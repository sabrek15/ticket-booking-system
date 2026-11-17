# 🎟️ High-Concurrency Ticket Booking Engine

[![CI/CD Pipeline](https://github.com/sabrek15/ticket-booking-system/actions/workflows/ci.yml/badge.svg)](https://github.com/sabrek15/ticket-booking-system/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)
![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)
![Docker](https://img.shields.io/badge/Docker-Multi--Stage-2496ED?logo=docker)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

A production-grade, distributed ticket reservation system engineered to handle extreme flash-sale concurrency without overselling, data corruption, or database connection pool exhaustion.

---

## 🏗️ System Architecture

```text
                       HIGH-CONCURRENCY RESERVATION WORKFLOW
                       
[Client Request: POST /api/v1/bookings/reserve]
                   │
                   ▼
         [RateLimitingFilter] ──── (Token Bucket: 5 req/min via Redisson RRateLimiter)
                   │
                   ▼
        [JwtAuthenticationFilter] ─ (Spring Security 6 Stateless Bearer Token)
                   │
                   ▼
       [Resilience4j CircuitBreaker]
             /                  \
      (Circuit CLOSED)     (Circuit OPEN / Redis Failure)
           /                      \
  [Redisson Distributed Lock]   [PostgreSQL Pessimistic Lock]
  (In-Memory RLock)             (SELECT ... FOR UPDATE)
           │                              │
           └──────────────┬───────────────┘
                          ▼
            [TransactionTemplate (ACID)]
            • Check available capacity
            • Decrement seat inventory
            • Persist Booking (PENDING_PAYMENT)
                          │
                          ▼
                [HTTP 201 Created] (~25ms)
                          │
             (Upon Booking Confirmation)
                          ▼
             [ApplicationEventPublisher]
                          │
                          ▼
            [@TransactionalEventListener]
            (phase = TransactionPhase.AFTER_COMMIT)
            • Async notification dispatch via bounded ThreadPoolTaskExecutor
```

---

## ✨ Core Engineering Highlights

* **Distributed Locking with Fault-Tolerant Fallback:** Primary seat serialization via **Redisson `RLock`** in Redis. If Redis experiences network partition or outage, **Resilience4j Circuit Breaker** trips and gracefully degrades to **PostgreSQL Pessimistic Row Locking (`SELECT ... FOR UPDATE`)**, ensuring 100% uptime and zero overselling.
* **Zero Connection Pool Starvation:** Decouples distributed lock waiting from database transactions using programmatic `TransactionTemplate` execution, preventing HikariCP pool exhaustion under high concurrency.
* **Idempotency Protection:** Redis `SETNX` token mechanism preventing accidental duplicate submissions and double-billing on network retries.
* **Cache-Aside Pattern:** High-read event browsing cached in Redis with Jackson polymorphic type serialization (`@class`), delivering sub-5ms response times.
* **Event-Driven Architecture (EDA):** Emits domain events (`BookingConfirmedEvent`) deferred to post-commit phase (`AFTER_COMMIT`), processed asynchronously on a custom bounded `ThreadPoolTaskExecutor` with `CallerRunsPolicy` backpressure.
* **Distributed Rate Limiting:** Enforces the Token Bucket algorithm via Redisson `RRateLimiter` at the servlet filter level to defend against botnets and scalpers (`HTTP 429 Too Many Requests`).
* **Clean Modular Monolith:** Strict Maven separation across `booking-common` (contracts), `booking-core` (domain persistence), and `booking-api` (REST protocols).
* **Automated Concurrency Stress Testing:** Multi-threaded JUnit 5 test utilizing a **Two-Gate CountDownLatch** unleashing 50 concurrent threads on a 5-capacity tier (asserting exactly 5 successes, 45 rejections, 0 oversold).

---

## 📁 Multi-Module Structure

```text
ticket-booking-system
├── booking-common       # Shared DTOs, Enums, Custom Exceptions
├── booking-core         # Domain Entities, Repositories, Business Services, Redisson, Resilience4j
├── booking-api          # REST Controllers, Security Filters, JWT, Swagger, Docker Entrypoint
├── .github/workflows    # GitHub Actions CI/CD Pipeline (with Postgres & Redis Services)
├── docker-compose.yaml  # Multi-Container Orchestration (App + Postgres + Redis)
└── Dockerfile           # Multi-Stage, Multi-Architecture (ARM64/AMD64) Production Build
```

---

## 🚀 Quickstart (Docker Compose)

Launch the entire ecosystem (PostgreSQL 16, Redis 7, and Spring Boot) with a single command:

```bash
# Clone the repository
git clone https://github.com/sabrek15/ticket-booking-system.git
cd ticket-booking-system

# Build and start all services
docker compose up --build
```

### Endpoints:
* **Interactive Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
* **Health Probes & Metrics:** `http://localhost:8080/actuator/health`
* **OpenAPI 3 Docs:** `http://localhost:8080/v3/api-docs`

---

## 🔑 Default Test Credentials

The database is automatically seeded on startup with test accounts:

| Role | Email | Password | Permissions |
| :--- | :--- | :--- | :--- |
| **Admin** | `admin@example.com` | `admin123` | Create Events, Add Ticket Tiers, Publish Events |
| **Customer** | `john.doe@example.com` | `password123` | Browse Events, Reserve Tickets, Confirm Bookings |

---

## 🧪 Running Automated Tests

```bash
# Run isolated Mockito unit tests (booking-core)
mvn test -pl booking-core

# Run 50-thread concurrency race condition stress test (booking-api)
mvn test -pl booking-api -Dtest=BookingConcurrencyTest
```

---

## 📡 API Reference

### Authentication
* `POST /api/v1/auth/register` - Register a new customer account
* `POST /api/v1/auth/login` - Authenticate and receive stateless JWT token

### Events (Public & Admin)
* `GET /api/v1/events` - Browse all published concerts (Cached in Redis)
* `GET /api/v1/events/{id}` - View specific concert details and seat availability
* `POST /api/v1/events` - Create concert event `[ROLE_ADMIN]`
* `POST /api/v1/events/{id}/tiers` - Add ticket tier with capacity `[ROLE_ADMIN]`
* `PATCH /api/v1/events/{id}/publish` - Publish drafted concert `[ROLE_ADMIN]`

### Bookings (Authenticated Customer)
* `POST /api/v1/bookings/reserve` - Temporarily reserve seats (Protected by Redisson Lock & Rate Limiter)
* `POST /api/v1/bookings/{id}/confirm` - Confirm reservation and trigger async ticket dispatch (IDOR protected)
