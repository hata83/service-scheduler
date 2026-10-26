# ADR-009: ArchUnit for Feature-Based Package Independence

- **Status:** Accepted
- **Date:** 2026-10-23
- **Amended:** 2026-10-26
- **Project:** Unified Service Scheduler

---

## Context

Uncontrolled cross-package dependencies cause tight coupling. Code becomes hard to test, refactor, and maintain.

---

## Decision

Structure backend code into feature-based packages under `dev.hata.scheduler.*`:
`auth`, `customer`, `dealership`, `appointment`, `resource`, `vehicle`, `notification`, `scheduling`, and `common`.

Each feature package holds nested packages:
- `controller/` (REST endpoints, filters, and HTTP mapping)
- `service/` (domain services and business workflows)
- `repository/` (Spring Data repositories and storage implementations)
- `model/` (JPA entities, enums, and domain identity objects)
- `dto/` (request and response records)
- `event/` (domain events)
- `handler/` (event listeners)

The `ArchitectureTest` class enforces six rules with ArchUnit:
1. Feature packages must not import other feature packages.
2. Feature packages must not import `infra`.
3. The `common` package must not import any feature package or `infra`.
4. The `infra` package must not import any feature package.
5. Controllers in `controller/` only reach the service layer (`controller`, `service`, `dto`, `event`, `model`, `exception`, `common`). They never access repositories directly.
6. Service classes in `service/` never import `jakarta.servlet` or `javax.servlet` types.

---

## Reason

Feature-based packaging models the business domain directly. It keeps related code together while preventing dependency cycles. Shared interfaces in `common` provide clean decoupling contracts.

---

## Consequences

### Positive

- Feature packages remain independent and testable in isolation.
- Presentation code cannot bypass business validation to query databases directly.
- The build gate fails immediately when a new import violates architecture rules.

### Negative

- Cross-feature calls require provider interfaces in `common` or Spring domain events.
- Creating a new feature requires defining clean interface boundaries.

---

## Alternatives Considered

- Horizontal layer-first packages (`controllers/`, `services/`, `repositories/`).
- Three-level hexagonal onion layering (`domain/`, `application/`, `adapter/`, `infra/`).
- Unrestricted imports without automated ArchUnit assertions.
