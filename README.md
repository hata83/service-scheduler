# Unified Service Scheduler

[![Build Status](https://github.com/hata/service-scheduler/actions/workflows/ci.yml/badge.svg)](https://github.com/hata/service-scheduler/actions)
[![Java 25](https://img.shields.io/badge/Java-25%20LTS-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-green.svg)](https://spring.io/projects/spring-boot)
[![OpenAPI](https://img.shields.io/badge/OpenAPI-3.1-blue.svg)](http://localhost:8080/swagger-ui.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## OpenWiki (Code Wiki)

This repository uses [OpenWiki](https://github.com/langchain-ai/openwiki) to maintain a searchable, versioned code wiki. See `.agents/skills/openwiki/openwiki/quickstart.md` for the quickstart, and `.agents/skills/openwiki/` for the local skill bundle.

- Initialize or update: follow the OpenWiki CLI (`openwiki`) workflow (`init`, `update`, `archive`).
- Source evidence lives in versioned repository files; generated wiki outputs go to `openwiki/`.
- For host-driven generation (coding-agent integrations), refer to `.agents/skills/openwiki/openwiki/integrations/coding-agents.md`.

## AI Agent Onboarding

New AI agents should read these onboarding documents in order:

1. `AGENTS.md` — Agentic SDLC, skills, verification ladder, and architecture rules (non-negotiables).
2. `docs/SYSTEM_DESIGN.md` — Design source of truth.
3. `.agents/skills/openwiki/openwiki/quickstart.md` — OpenWiki orientation and task-routing map.
4. `.agents/skills/openwiki/openwiki/workflows/repository-generation.md` — Resumable page-job lifecycle.
5. `.agents/skills/openwiki/openwiki/integrations/coding-agents.md` — Host-driven generation and MCP operations.
6. Skill bundles (`.agents/skills/`): `asd-ste100-skill`, `springboot-patterns`, `java-coding-standards`, `open-code-review-delegate`, `archify`, `karpathy-ponytail`, `vue-patterns`, `e2e-testing`, `verification-loop`.

Always load `AGENTS.md` and initialize OpenSpec (`openspec init`) before proposing changes.

## OpenWiki Auto-Update

The OpenWiki documentation agent runs through `openwiki --update`. For scheduled self-updates in CI, see `.agents/skills/openwiki/openwiki/operations/ci-scheduling.md` and `.agents/skills/openwiki/openwiki/integrations/coding-agents.md`. Automatic updates open a docs PR; archive (`/opsx-archive`) only after all checks and PR comments resolve.

An enterprise automotive dealership service scheduling platform built for modern workshop capacity management.

This system gives dealership service advisors, managers, and technicians the tools to match incoming service demand to available workshop capacity in real time.

---

## At a Glance

- **Domain:** Automotive Retail Platform — Workshop Capacity & Appointment Management.
- **Architecture:** Spring Boot 4.1.1 Web MVC powered by Java 25 Virtual Threads.
- **Booking Safety:** PostgreSQL `EXCLUDE USING gist` prevents double-bookings at the database level across bays, vehicles, and technicians.
- **Constraint Engine:** Pure Java `ServiceConstraintEvaluator` evaluates service type resource requirements with microsecond latency.
- **Real-Time Push:** Direct cookie-authenticated Spring MVC `SseEmitter` streams schedule changes to manager dashboards.
- **Transactional Outbox:** At-least-once delivery for webhook dispatches and notification events.
- **Integration Tests:** Bruno CLI suite proves every endpoint against a running server.

---

## Architecture Overview

The system has four layers: client, application, data, and observability. It uses one cache technology, one constraint evaluator, and one database booking gate.

- **Booking safety:** PostgreSQL `EXCLUDE USING gist` rejects overlapping bay bookings, vehicle VIN collisions, and technician assignment conflicts at the database level.
- **Constraint engine:** Java `ServiceConstraintEvaluator` evaluates relational constraints. It matches allowed bay types and required technician skills with zero external native binary dependencies.
- **Flexible capacity:** One bay carries several bay types, and one technician holds several skills, each with its own qualification level.
- **Cost estimate:** The customer sees an instant estimate per vehicle when they select a service type. The manager adjusts job costs and slots at check-in and records the agreed amount in `total_cost`.
- **Real-time push:** `SseEmitter` streams each committed change to connected manager dashboards with single-stream enforcement per user.

See [`docs/SYSTEM_DESIGN.md`](docs/SYSTEM_DESIGN.md) for the component roles, the schema, and the state machines. See [`docs/diagrams/index.html`](docs/diagrams/index.html) for the diagram dashboard.

### Feature-Based Package Layout

The backend uses feature-based packages under `dev.hata.scheduler`. ArchUnit tests enforce package independence and layering:

```
dev.hata.scheduler
├── auth/                     # authentication feature: controller, service, repository, model (User, UserType), dto
├── customer/                 # customer feature: model (UserProfile -> profiles), repository, service
├── dealership/               # dealership feature: model (Dealership), repository, service
├── resource/                 # resource feature: model (Employee, EmployeeRole, Technician, TechnicianGrade, TechnicianSkill, SkillLevel), repository, service
├── infra/                    # infrastructure: redis window counter, flyway migrations
└── common/                   # shared kernel
    ├── filter/               # ClientIpResolver, AuthFilter, IdempotencyFilter
    ├── security/             # JwtService, AuthPrincipal, JwtProperties
    ├── otp/                  # OtpService, OtpStorage, OtpType, OtpProperties
    ├── ratelimit/            # RateLimited, RateLimitAspect, RateLimitService, RateLimitProperties
    ├── idempotency/          # IdempotencyStore
    ├── exception/            # RFC 7807 ApiProblem, AuthException, GlobalExceptionHandler
    ├── provider/             # cross-feature decoupling providers (UserProfileProvider, EmployeeProfileProvider)
    └── architecture/         # ArchUnit executable assertions
```

- A feature package must not import another feature package.
- The shared kernel `common` must not import a feature package.
- Controllers only reach the service layer. They never access repositories directly.
- Service classes never import servlet types (`jakarta.servlet.*`). Controllers handle HTTP serialization.

See [`docs/adr/ADR-009-archunit-for-package-independence.md`](docs/adr/ADR-009-archunit-for-package-independence.md) for the decision and trade-offs.

---

## Features

### 1. Appointment Booking with Constraint Evaluation (CR-1, CR-2)

Customers or service advisors book an appointment. The system:
1. Calls `ServiceConstraintEvaluator` to evaluate service type requirements: allowed bay types, required technician skills, minimum skill levels, and duration.
2. Queries available bays with no overlapping appointment using `SlotAvailabilityService`.
3. Verifies that qualified, unassigned technicians are available to satisfy all role slots.
4. Inserts the appointment and service jobs inside a single transaction guarded by PostgreSQL `EXCLUDE USING gist` constraints.

All write requests require an `X-Idempotency-Key` header with a client UUID v4. Retries return the cached response. The system never creates a duplicate appointment.

### 2. Real-Time Schedule View (CR-3)

Service managers connect to `GET /api/v1/notifications/stream` and receive live schedule updates. Every appointment state change pushes an SSE event within 100ms.
- The connection uses direct `httpOnly` JWT cookie authentication (`withCredentials: true`).
- The backend enforces a single active stream per phone number.
- A 25-second heartbeat prevents proxy timeouts on idle connections.

### 3. Full Appointment Lifecycle with Actor Constraints (CR-4)

Appointments move through clear lifecycle states:
`REQUESTED → CONFIRMED → CHECKED_IN → IN_PROGRESS → ON_HOLD → COMPLETED → INVOICED`

Terminal states: `CANCELLED`, `REJECTED`, `NO_SHOW`.

Each transition enforces who can trigger it:
- Customers receive instant cost estimates on service type selection and can cancel before `CHECKED_IN`.
- Managers control confirmations, check-in negotiations (recording final agreed amount in `total_cost`), rejections, and no-show determinations.
- Once status reaches `CHECKED_IN` or later, the appointment cannot transition to `CANCELLED` or `REJECTED`.
- Technicians update job-level execution status: `PENDING → IN_PROGRESS → ON_HOLD → DONE`.

### 4. Relational Resource Constraint Engine

The database stores service type constraints as structured metadata in `service_types` and `service_type_technician_requirements`. The `ServiceConstraintEvaluator` evaluates constraints at booking time in microseconds. Service managers edit constraints via API without code redeployments.

### 5. Transactional Outbox and Multi-Channel Notifications

Every appointment state change writes an `OUTBOX_EVENTS` row in the same database transaction. The outbox poller delivers notifications:
- **SSE:** Live real-time dashboard updates.
- **Webhook:** HMAC-SHA256 signed HTTP POST to registered external endpoints (`X-Scheduler-Signature`).
- **Communication Log:** Full audit trail for future SMS, Email, and messaging relays.

### 6. Observability

Prometheus metrics at `/actuator/prometheus` include slot utilization, booking conflict count, booking latency percentiles (p50, p95, p99), evaluator latency, and technician utilization.

The system formats all log entries as structured JSON with trace ID, span ID, appointment ID, and dealership ID in MDC context.

---

## Technology Stack

| Component | Technology | Reason |
|---|---|---|
| Language | Java 25 LTS | Virtual Threads, records, pattern matching |
| Framework | Spring Boot 4.1.1 (MVC) | Industry standard, mature JPA ecosystem |
| Concurrency | Java 25 Virtual Threads | High concurrency without reactive complexity |
| Constraint Engine | Pure Java / Spring Service | Microsecond constraint evaluation, zero native binary overhead |
| Database | PostgreSQL 16+ | The only database. Real GIST exclusion, SKIP LOCKED, and ACID safety (Docker Compose for dev, Testcontainers for CI) |
| Migrations | Flyway 10+ | Versioned, reproducible schema management |
| Cache | Redis 7 | Distributed slot cache and idempotency response cache |
| Real-time Push | Spring MVC SseEmitter | Direct cookie-authenticated Server-Sent Events |
| Rate Limiting | @RateLimited AOP + Redis | Fixed window rate limiting per IP and per entity across pods |
| Resilience | Resilience4j | Circuit breaker and retries for webhook dispatch |
| API Documentation | SpringDoc OpenAPI 3.1 | Auto-generated Swagger UI and schema export |
| Build | Gradle 9.x | Multi-module build |
| Formatter | Spotless + Palantir Java Format 2.71 | Automated code formatting |
| Static Analysis | Checkstyle 10, PMD 7 | Style and code quality gates |
| Architecture Tests | ArchUnit 1.x | Package independence enforcement |
| Integration Tests | Bruno CLI | Executable API contracts against a running server |
| Diagrams | Archify | Interactive architecture and lifecycle diagrams |

---

## Pre-Requirements

### Development Environment

- **Java 25 LTS** installed and on `PATH`. Set `JAVA_HOME`.
- **Node.js 20+** and npm 10+ (for Bruno CLI and OpenSpec).
- **Docker Desktop** (for PostgreSQL + Redis in production mode).
- **Git 2.40+**.

### Production Environment

- **Docker** and Docker Compose v2+.
- A PostgreSQL 16+ instance (provided by Docker Compose `prod` profile).
- A Redis 7+ instance (provided by Docker Compose).
- A CDN or WAF (Cloudflare, AWS WAF) in front for DDOS protection.

### Environment Variables

```sh
# Required for production (the dev profile points at Docker Compose)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/scheduler
SPRING_DATASOURCE_USERNAME=scheduler
SPRING_DATASOURCE_PASSWORD=<secret>

SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379

SCHEDULER_JWT_SECRET=<256-bit-secret>   # Never commit to Git

# Optional
SCHEDULER_WEBHOOK_SIGNING_SECRET=<secret>
SPRING_PROFILES_ACTIVE=prod             # prod requires the JWT secret and the PostgreSQL URL
```

**Never commit `.env` files to Git.** Use environment-specific secret management in production.

---

## Quick Start (Local Development with Docker Compose)

### Profiles and Backing Services

The default profile connects to local Docker Compose services:
- PostgreSQL 16 on `localhost:5433` (mapped from container port 5432)
- Redis 7 on `localhost:6379`

Start PostgreSQL and Redis before you start the application:

```sh
# Start PostgreSQL 16 and Redis 7
docker compose up -d postgres redis
```

### Run from Source

```sh
# Clone the repository
git clone https://github.com/hata/service-scheduler.git
cd service-scheduler

# Build the application
./gradlew build

# Start with the default profile
./gradlew bootRun
```

The application starts on `http://localhost:8080`.

| URL | Purpose |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive Swagger UI API documentation |
| `http://localhost:8080/v3/api-docs` | OpenAPI 3.1 schema (JSON) |
| `http://localhost:8080/actuator/health` | Health endpoint (proves DB and Redis connectivity) |
| `http://localhost:8080/actuator/prometheus` | Prometheus metrics |

### Interactive API Testing with Swagger UI

Swagger UI runs at `http://localhost:8080/swagger-ui.html`.
It loads the OpenAPI 3.1 specification from `http://localhost:8080/v3/api-docs`.

To simulate API requests in Swagger UI:
1. Open `http://localhost:8080/swagger-ui.html` in your web browser.
2. Expand an endpoint, such as `POST /api/v1/auth/otp/send`.
3. Notice that `X-Idempotency-Key` and the request body contain prefilled values.
4. Click **Execute** to send the request.
5. Review the response status and the response body.
6. Copy the issued `otpCode` from the 202 response body.
7. Expand `POST /api/v1/auth/otp/verify`. Enter the phone number and code. Click **Execute**.
8. The server responds with 200 OK and sets the `jwt` session cookie in your browser.
9. Expand `GET /api/v1/auth/me`. Click **Execute** to view your authenticated identity.

### Run with Docker Compose (PostgreSQL + Redis)

```sh
# Start all services (PostgreSQL 16, Redis 7, Spring Boot)
docker compose up --build -d

# View logs
docker compose logs -f service-scheduler
```

---

## Development and Testing Guide

### 1. Build and Quality Verification

```sh
# Run all verification tasks (Spotless, Checkstyle, PMD, ArchUnit, unit tests)
./gradlew check

# Apply automatic code formatting
./gradlew :service-scheduler-server:spotlessApply

# Generate JaCoCo coverage report
./gradlew test jacocoTestReport
# View report: service-scheduler-server/build/reports/jacoco/test/html/index.html

# Run all verification tasks plus build
./gradlew clean build
```

### 2. Integration Tests

Integration testing verifies database constraints and API contracts:

- **Unit Tests:** The JUnit 5 suite runs without a database (`./gradlew test`). It covers the auth use cases, the filters, and the rate-limit loader.
- **Testcontainers Integration Tests:** Real PostgreSQL instances run via Testcontainers to enforce and prove the schema migrations (`./gradlew test` runs `SchemaMigrationTest`).
- **Bruno API Tests:** The Bruno CLI suite runs against the backend running locally with Docker Compose PostgreSQL and Redis (`docker compose up -d postgres redis`).
- **Playwright E2E Tests:** The API-level Playwright suite runs against the same backend (`cd service-scheduler-ui && npx playwright test`).

```sh
# 1. Start local PostgreSQL and Redis services
docker compose up -d postgres redis

# 2. Stop any server that already holds port 8080.
#    A stale server serves the old build and the suite then tests the wrong code.
#    The backend refuses to start when the port is taken, so read the log.

# 3. Start the backend with the test profile
SPRING_PROFILES_ACTIVE=test ./gradlew bootRun

# 4. Reset the fixtures so the suite starts clean
./gradlew :service-scheduler-server:resetAuthFixtures

# 5. Execute Bruno API contract test suite
cd service-scheduler-server/src/test/bruno && npx --yes @usebruno/cli@2.14.1 run --env local --disable-cookies && cd ../../../..

# 6. Execute the Playwright E2E suite
cd service-scheduler-ui && npx playwright test

# 7. Run the full build gate, including the Testcontainers suite
cd ../.. && ./gradlew clean build
```

---

## Project Structure

```text
service-scheduler/
├── .github/
│   └── workflows/ci.yml        # GitHub Actions CI/CD
├── docs/
│   ├── SYSTEM_DESIGN.md        # Architecture, schema, taxonomy, state machines, API
│   ├── adr/                    # Architecture Decision Records (ADR-001 to ADR-013)
│   └── diagrams/               # Archify diagrams (JSON source + HTML viewers)
├── gradle/                     # Gradle wrapper
├── .gitignore                  # Excludes .agents/, build/, secrets, IDE state
├── AGENTS.md                   # AI agent development guide (Agentic SDLC workflow)
├── build.gradle.kts            # Root build script
├── gradle.properties           # Gradle properties
├── README.md                   # This file
├── settings.gradle.kts         # Gradle project configuration
└── docker-compose.yml          # Local stack (PostgreSQL 16, Redis 7)
```

Documentation lives in `docs/`:

| Path | Purpose |
|---|---|
| `docs/SYSTEM_DESIGN.md` | Architecture, schema, resource taxonomy, state machines, and API. The design source of truth. |
| `docs/diagrams/index.html` | Diagram dashboard. Every viewer, grouped by purpose. |
| `docs/adr/` | Architecture Decision Records (ADR-001 through ADR-013). |

---

## Architecture Diagrams

`docs/SYSTEM_DESIGN.md` is the source of truth for the design. Every diagram follows it.

The diagram dashboard lives in [`docs/diagrams/index.html`](docs/diagrams/index.html). It groups every diagram by layer, data model, state machine, use case, and delivery.

Archify commands for generating and validating a diagram live in `AGENTS.md` Section 7.

---

## AI Collaboration Narrative

The full engineering narrative lives in [`AGENTS.md`](AGENTS.md) — AI Collaboration Narrative.

| Topic | Where |
|---|---|
| Strategy for guiding the AI | `AGENTS.md` — AI Collaboration Narrative, part 1 |
| Process for checking the output | `AGENTS.md` — AI Collaboration Narrative, part 2 |
| Process for refining the output | `AGENTS.md` — AI Collaboration Narrative, part 3 |
| How the team ensured final quality | `AGENTS.md` — AI Collaboration Narrative, part 4 |
| Design-phase AI assistance and its limits | [`docs/SYSTEM_DESIGN.md`](docs/SYSTEM_DESIGN.md) Section 18 |

---

## License

The Apache License 2.0 licenses this project. See [LICENSE](LICENSE) for details.
