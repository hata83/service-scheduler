# Proposal: Phase 1 Platform, Customer Registration, and OTP Login

## Why

The repository is docs-only today. CI, the AGENTS gates, and the Bruno contract paths already expect a `service-scheduler-server` module that does not exist. Phase 1 becomes three approved capacities: a runnable platform with seed data, customer registration by phone, and OTP login with profile editing for all four roles.

## What Changes

- Restructure Gradle: the root project becomes the parent, and a new `service-scheduler-server` module matches the CI task paths (`:service-scheduler-server:*`, `integrationTest`, `jacocoTestCoverageVerification`).
- **Capacity 1 — infrastructure**: Docker Compose with PostgreSQL 16 and Redis 7 plus a multi-stage Dockerfile; Spring Boot skeleton with tracing, JSON logging, and monitoring; health that proves DB and Redis connectivity; seed data with 1 demo dealership, 1 admin, 1 manager, 1 technician, and 1 customer that belongs to that dealership. H2 leaves the stack entirely.
- **Capacity 2 — customer registration**:
  - `POST /api/v1/auth/otp/send` with `{phone, type: "REGISTER"}` — the customer first gets a register-purpose OTP through the Zalo channel stub (the console print is the stub).
  - `POST /api/v1/auth/register` — the customer submits phone plus profile info plus the OTP code in one call; the backend verifies the REGISTER-purpose OTP, checks profile info, inserts the user and profile, and creates the JWT session as the customer's login.
- **Capacity 3 — OTP login for every role**:
  - `POST /api/v1/auth/otp/send` with `{phone, type: "LOGIN"}` — customer/admin/manager/technician submit a phone; the OTP leaves through the Zalo channel stub (console print in this phase), five-minute default TTL, single use.
  - `POST /api/v1/auth/otp/verify` — verify the pair; the response sets a JWS session in an `httpOnly` cookie (ADR-005, ADR-011) and carries the resolved identity; roles resolve from the `users` table. Verify is login-only: it never creates a user or profile, and an unregistered phone receives 401 that names registration as the next step.
  - `GET /api/v1/auth/me` — user detail view.
  - `PATCH /api/v1/auth/me` — edit profile after login: email for every role, names for CUSTOMER and TECHNICIAN. The phone is the login identity and is never editable.
  - `POST /api/v1/auth/logout` — revoke the Redis session and clear the cookie.
- PostgreSQL everywhere: dev/local runs on the compose database, the gradle integration suite runs Testcontainers Bruno against PostgreSQL 16, and Playwright E2E runs against the compose-stacked database. `AGENTS.md`'s H2 stack row and the openspec context note drop H2 in the same change.
- Rate-limit OTP issuance with Redis INCR/EXPIRE windows per ADR-007: `otp/send` 5 per phone-purpose pair per 10 minutes plus 30 per IP per minute, `otp/verify` 10 per IP per minute, `register` 10 per IP per minute. Require `X-Idempotency-Key` on `otp/send`, `otp/verify`, `register`, and `PATCH me` per ADR-008 — no exemptions. Bind every OTP to its issue purpose (`REGISTER` or `LOGIN`); a purpose mismatch fails verification.
- Amend ADR-011: the register call itself creates the account and the login session from a REGISTER-purpose OTP; `verify` never creates user or profile rows. The endpoint name standardizes on `otp/send` with a type field.
- Add 34 Bruno `.bru` files under `service-scheduler-server/src/test/bruno/` — one file per scenario, per capacity. Every spec's `## Endpoint Contracts` section gives copy-ready request/response header and body blocks; the openspec config now mandates that format for future proposals too.
- Add API-level Playwright E2E specs: `platform-infrastructure.spec.ts`, `otp-login.spec.ts`, `customer-registration.spec.ts` under `service-scheduler-ui/tests/e2e/`.
- Update diagrams: regenerate `otp-login`, create `customer-registration`, and finalize interactive HTML viewers for `otp-login.html`, `customer-registration.html`, `system-architecture.html`, and `system-data-model.html`. Each business capacity gets its diagram reference inside the openspec design and its delta spec's Capacity Mapping.
- Document every endpoint with a request/response contract: the spec `## Mapping` API tables carry Request and Response columns per endpoint (see the customer register and user login capacities).
- Update docs: ADR-011 amendment and `docs/SYSTEM_DESIGN.md` §14.1 endpoint list (register, me, patch me).

 Notifications (SSE stream, history) moved out of Phase 1 by the HIL-1 re-scope; they stay in the design backlog as the next capability.

## Capabilities

### New Capabilities

- `platform-infrastructure`: compose topology, Spring Boot skeleton, tracing/logging/monitoring, DB + Redis healthy connectivity, and the seed fixtures with the customer scoped to the demo dealership. This capability owns platform-level runnability contracts as later domains land on top.
- `customer-registration`: registration by phone plus profile info plus a REGISTER-purpose OTP behind one idempotency key, role pinning to CUSTOMER, duplicate-phone rejection, and the created session acting as the customer's first login.
- `otp-login`: OTP issuance through the Zalo stub with a purpose type, verify-to-login with an `httpOnly` JWT cookie for all four roles (login-purpose OTP only), session introspection, user detail view and edit, and logout. This capability owns phone-OTP session lifecycle.

### Modified Capabilities

(none — `openspec/specs/` is empty; all three capabilities seed new spec paths)

## Impact

- **Code**: new module `service-scheduler-server` with packages `dev.hata.scheduler.auth.*` and `common.*`; root build slims to parent-only config; checkstyle/PMD configs move into the module.
- **APIs**: the 5 auth endpoints listed above. All error responses use `application/problem+json` (ADR-006). OpenAPI 3.1 via SpringDoc on every handler.
- **Data**: `dealerships`, `users`, `customers`, `technicians`, `technician_skills` in V1 (PostgreSQL-only DDL, audit columns on every table). EXCLUDE-based booking tables wait for the booking phase (invariant 1). No `communication_log` write path this phase.
- **Dependencies added**: `spring-boot-starter-data-redis`, `jjwt`, `logstash-logback-encoder`, `micrometer-tracing-bridge-otel` (exporters off by default), Testcontainers `postgresql` + `junit-jupiter`.
- **Tests**: Bruno files gate each endpoint (BE-5). JaCoCo gates 100% on `@Service` classes and 80% overall (BE-6). ArchUnit gates package independence. `ocr delegate` gates the diff (BE-7).
- **Verification ladder**: BE-1/BE-2 (TDD per component) → BE-3 (spotless) → BE-4 (constraint-evaluator item is N/A this phase; ArchUnit covers architecture) → BE-5 (Bruno per endpoint) → BE-6 (coverage) → BE-7 (review delegate). Frontend-leg items are limited to the API-level E2E specs; MSW mocks and page integration arrive with the UI change.
- **Breaking changes**: none for users (greenfield). ADR-011 text is amended before any implementation exists.
