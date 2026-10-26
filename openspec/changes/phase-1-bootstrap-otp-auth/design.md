# Design: Phase 1 Platform, Customer Registration, and OTP Login

## Context

The repo is docs-only. `build.gradle.kts` at the root applies the full Spring Boot 4.1.1 toolchain but declares no module CI can find. CI (`.github/workflows/ci.yml`) is the real bootstrap contract: it expects `service-scheduler-server/build/reports/*` paths, an `integrationTest` task with Testcontainers against PostgreSQL 16 + Redis 7 services, a `test` task under `SPRING_PROFILES_ACTIVE=test`, Bruno tests at `service-scheduler-server/src/test/bruno/`, and coverage verification of 100% on `@Service` classes and 80% overall. Per the HIL-1 round-4 gate, H2 is banned: dev/local runs on the compose stack, integration runs Testcontainers Bruno, and E2E runs against the compose database. `openspec/specs/` is empty, so all three capabilities seed new spec paths. See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**

- A runnable `service-scheduler-server` module that passes every CI task name.
- OTP register + login for the four seeded roles, behind the backend-first ladder.
- All five auth endpoints with descriptors, problem+json errors, and rate limits.

**Non-Goals:**

- Staff provisioning endpoints (admin creates managers, manager creates technicians) — later capability.
- Notification delivery (SSE stream, history, `communication_log` writes, webhooks, ZNS real integration) — next capability after this phase. The Zalo OTP channel is a stub only.
- Frontend pages, MSW mocks, and UI screenshots — UI phase. E2E specs here are API-level.
- EXCLUDE constraint tables and their concurrency tests — booking phase (invariant 1 requires PostgreSQL; migration migrations for exclusions land there).

## Decisions

- **D1. Gradle restructure.** Root becomes a parent: Boot/Spotless/Checkstyle/PMD/JaCoCo plugins applied with `apply false` at the parent, versions set there; features configured in `service-scheduler-server/build.gradle.kts`. Alternatives: keep a single-module root and rewrite AGENTS/CI paths — rejected, because CI and AGENTS already name the module; and `feature/<slug>` tooling mirrors openboard's two-module shape.
- **D2. Endpoint shape.** Canonical paths: `otp/send` (with a purpose `type`), `otp/verify`, `register`, `me` (GET + PATCH), `logout`. ADR-011's `otp/request` merges into `otp/send`. Registration is one call: `POST /api/v1/auth/register` carries phone + profile fields + the OTP code; the backend first verifies the REGISTER-purpose OTP, inserts the CUSTOMER row and profile, and creates the JWT session in the response, so the register response is the customer's login. `verify` is login-only and never creates a user or profile: it consumes a LOGIN-purpose OTP and resolves an existing account, answering 401 with a register hint for unknown phones. This amends ADR-011 steps 5-6 (the old draft's verify-time auto-register dies here). OTP purpose binding: each Redis OTP record carries its issue purpose; a LOGIN verify rejects an OTP issued for REGISTER and vice versa. Alternative: nullable profile columns with placeholders — rejected, it hides the manager's intake job and pollutes audit data.
- **D3. Profile matrix, PostgreSQL-only.** No H2 in dependencies or configs ever. `test` profile (CI gradle `test`, Bruno CI runs): PostgreSQL + Redis from the CI service containers via `SPRING_DATASOURCE_URL`/`SPRING_DATA_REDIS_HOST` envs, `app.auth.otp.echo=true` (code echoed in responses), 15-second OTP TTL property for the expiry test, reduced rate-limit budgets. Default profile (local dockerized dev): PostgreSQL 16 + Redis 7 from `docker compose up`, echo on. `prod`: echo absent, JWT secret required at boot (fail-fast). TDD honesty: the echo property is a profile-gated stub, the production path never reaches it.
- **D4. PostgreSQL-only V1/V2.** With H2 gone, migrations target PostgreSQL only: plain `CREATE TABLE` with `UUID` PKs (application-generated, no DB default needed), `timestamptz` audit columns, account `type` CHECK constraint on `users` (`CUSTOMER` or `EMPLOYEE`). No `{vendor}` Flyway split is needed at any phase anymore. `users` is the global IAM identity table (`id`, `phone`, `email`, `type` in `CUSTOMER`/`EMPLOYEE`, `is_active`) without `dealership_id` or duplicated `role`. `profiles` (`user_id` PK/FK, `first_name`, `last_name`, `locale`) centralizes personal profile details for all accounts (`CUSTOMER` and `EMPLOYEE`) so profile reads and updates work uniformly across every account. `employees` (`user_id` PK/FK to `profiles`, `employee_id` UNIQUE NOT NULL, `role` in `ADMIN`/`MANAGER`/`TECHNICIAN`, `dealership_id` FK) models the employee abstraction: a CHECK constraint enforces `dealership_id IS NULL` for global `ADMIN` and `dealership_id IS NOT NULL` for local `MANAGER` and `TECHNICIAN`. `technicians` (`user_id` PK/FK to `employees`, `level` in `TRAINEE`/`JUNIOR`/`TECHNICIAN`/`SENIOR`/`MASTER`, `is_available`) extends `employees` with technician career grade, and `technician_skills` (`technician_id`, `skill`, `level`) holds multi-skill qualifications with per-skill proficiency level. V2 seeds fixed UUIDs and phones (+84900000001 admin `...0002` with `type = 'EMPLOYEE'`, +84900000002 manager `...0003` with `type = 'EMPLOYEE'`, +84900000003 technician `...0004` with `type = 'EMPLOYEE'`, +84900000004 customer `...0005` with `type = 'CUSTOMER'`), 4 `profiles` rows, the demo dealership (`00000000-0000-0000-0000-000000000001`), 3 `employees` rows (`ADM-0001` with `role = 'ADMIN'` and `dealership_id = NULL`, `MGR-0001` with `role = 'MANAGER'` and `dealership_id = ...0001`, `TEC-0001` with `role = 'TECHNICIAN'` and `dealership_id = ...0001`), 1 `technicians` row (`level = 'SENIOR'`), and 2 `technician_skills` rows (`ENGINE` at `SENIOR`, `BRAKES` at `TECHNICIAN`).
- **D5. JWT via `jjwt`.** Stateless HS256 with claims `sub` (user id), `role`, `did` (active dealership id resolved from `employees.dealership_id`: `null` for global `CUSTOMER` and `ADMIN`, and the local `dealership_id` for `MANAGER` and `TECHNICIAN`), `ph` (phone), `jti`, 8-hour expiry; cookie name `jwt`, `httpOnly`, `SameSite=Strict`, `Secure` in prod. Logout clears the client cookie (`Max-Age=0`). Alternative: hand-rolled Base64url+HMAC — rejected, crypto subtlety is worth one dependency. Alternative: Spring Security autoconfig — rejected for phase 1 (CSRF/session defaults exceed scope); revisit when SSE or multi-surface auth lands.
- **D6. Filter-per-request auth.** One servlet `AuthFilter` (class: `common/filter/`) parses the cookie, cryptographically validates the JWT via `JwtService`, and publishes an `AuthPrincipal` in request scope. No locks, no shared mutable state — virtual threads stay unpinned (constraint 2 in AGENTS §4).
- **D7. OTP channels and rate-limit config.** `OtpService` saves the OTP to `OtpStorage` and publishes an `OtpGeneratedEvent` to `OtpNotificationListener`, which dispatches through `ZaloStubNotificationChannel` (prints the code to the server console; non-prod profiles additionally echo it in the response). Redis stores one OTP slot per purpose: `otp:login:{phone}` and `otp:register:{phone}`, each a single-use record with a 5-minute default TTL. `RedisWindowCounter` (INCR + EXPIRE, ADR-007) enforces the limits. Per-dealership rate limits resolve dynamically from `dealerships.features` JSONB via `DealershipFeaturesProvider` (ADR-013) with fallback to `config/rate-limits.json` defaults (`otpSendPerPhonePurposePer10Min: 5`, `otpSendPerIpPerMin: 30`, `otpVerifyPerIpPerMin: 10`, `registerPerIpPerMin: 10`). The `test` profile selects `config/rate-limits-test.json` (low values: 2-request windows, 10-second units, OTP TTL 15 s) so Bruno integration tests hit 429 and expiry fast. Resilience4j modules stay unadded until a later phase needs circuit breakers or retries.
- **D8. Idempotency family-wide.** Per ADR-008 no exemptions remain: every auth write endpoint carries `X-Idempotency-Key` (`otp/send`, `otp/verify`, `register`, `PATCH me`). A missing key answers 400; the first response caches for 24 hours. **Key semantics (HIL-1 round 8):** the effective cache record is `idempotency:{endpoint}:{phone}:{header-value}`, so a header value alone never collides across endpoints or phones. The recommended value is a phone-derived deterministic pattern — `otp-send-{TYPE}-{phone}`, `otp-verify-{phone}`, `register-{phone}` — which Phase 1 clients (Bruno, Playwright, Postman reviewers) use verbatim; a client may still send any unique string when it wants a genuinely fresh operation (new key = fresh op, e.g. a real resend). Replay per endpoint: send returns the cached 202 with no re-dispatch; verify and register return the cached 200/201 session with no second OTP consumption and no second insert; patch me returns the cached patch. Alternative: keying the cache on bare phone with no header — rejected, it removes the ability to resend ever and invents a second rule next to ADR-008; and: exempting send from idempotency — rejected at round 7, the cached replay stops accidental resends and keeps the header rule uniform.
- **D9. Errors and docs.** One `GlobalExceptionHandler` emits `application/problem+json` for 400/401/404/409/429 (ADR-006); SpringDoc annotates every handler; actuator exposes only `health` and `prometheus`; logging is JSON via logstash encoder with a `requestId` MDC filter.
- **D10. Docker.** Compose: `postgres:16-alpine` (healthcheck `pg_isready`), `redis:7-alpine` (`redis-cli ping`), and an optional `app` service under profile `full` to respect the AGENTS rule that the jar and the compose app never bind 8080 together. The Dockerfile is multi-stage (Gradle build → JRE 25 runtime).
- **D11. Purpose-typed consumption.** `RegisterService` runs the OTP check, row insert, and JWT issue inside one transaction boundary in its service layer: the OTP is consumed from `otp:register:{phone}` (delete-on-verify semantics), the rows commit, and the session cookie returns on the same 201. A replayed idempotency key returns the cached response and touches neither Redis nor the database. The after-commit listener pattern stays unused here because no dispatch exists; it re-enters with the notification-phase outbox (ADR-004). Alternative: an escalating multi-attempt counter per OTP — rejected for Phase 1; the single-use record plus rate limits bound abuse cheaply, and constant-time code comparison lands with the `OtpStorage` tests.
- **D12. Observability.** Micrometer Tracing over the OTel bridge with `management.tracing.enabled` sampling `always_on` locally but the OTLP exporter disabled by default, so trace ids enrich logs without shipping spans; the registry adds a structured-JSON (logstash) layout with `requestId` + `traceId` fields. Actuator is locked to `health` and `prometheus` (AGENTS runtime-safety rule), health carries `db` and `redis` indicators, and SpringDoc annotates everything it exposes. Alternative: Brave bridge + Zipkin server — rejected for footprint; the exporter flag flips the bridge later with no code change.
- **D13. Centralized profile and employee abstraction.** Personal profile attributes (`first_name`, `last_name`, `locale`) are centralized in `profiles` (`user_id` PK/FK to `users(id)`) for all four roles (`CUSTOMER`, `ADMIN`, `MANAGER`, `TECHNICIAN`). `PATCH /api/v1/auth/me` updates `users.email` and `profiles` names uniformly for any authenticated role through `CustomerProfileProvider` (`ProfileService`). Phone is immutable (login identity). Staff roles (`ADMIN`, `MANAGER`, `TECHNICIAN`) share the `employees` table (`Employee` base entity with `employee_id`, `role`, `dealership_id`), where `ADMIN` is global (`dealership_id IS NULL`), `MANAGER` is local (`dealership_id IS NOT NULL`), and `TECHNICIAN` (`Technician extends Employee`) is local (`dealership_id IS NOT NULL`) with career `level` and multi-skill `technician_skills`.

## Risks / Trade-offs

- [OTP echo could leak codes] → The echo property ships only in `test` and default profiles; `prod` fails fast on a missing secret and never imports the echo bean's conditional.
- [PostgreSQL required for every run, including plain unit tests] → Docker Desktop/Compose is the documented prerequisite (README updates land with the docs tasks); CI already runs postgres + redis services, and `@SpringBootTest` suites that touch no DB mock the repositories so they stay database-free.
- [`SameSite=Strict` cookie blocks cross-port localhost UI] → Bruno and the E2E specs are same-origin against `:8080`; the UI phase re-evaluates and may amend the ADR.
- [Reuse-based Bruno tests could collide on rate limits] → The 429 test uses its own fresh phone from the env file; seeded phones stay under their own budgets.
- [Redis required at boot] → Compose healthchecks gate readiness; the readme task documents `docker compose up -d postgres redis` before `bootRun`.
- [OTP auto-consumption races] → The purpose-typed single-use record plus an idempotency key on register makes double submission safe: one insert, one session, one OTP consumption.

## Migration Plan

Greenfield: `docker compose up -d postgres redis` → `./gradlew bootRun` → Flyway applies V1+V2 (seed online) → Bruno env `local` (compose-backed). `ci.yml` changes once: point the gradle `test` and Bruno steps at its own postgres + redis services (env URLs), since the `test` profile no longer assumes an H2 fallback. Rollback is `docker compose down -v` plus deleting the feature branch — no data survives to migrate.

## Diagrams

The clarified flow (purpose-typed `otp/send`, one-shot register, login-only verify, Zalo stub, profile edit) is captured in diagrams with interactive viewers:

- **OTP Login Flow:** [Interactive Viewer](../../docs/diagrams/system/otp-login.html)
- **Customer Registration Flow:** [Interactive Viewer](../../docs/diagrams/system/customer-registration.html)
- **Platform Architecture & Data Model:** [Interactive Viewer](../../docs/diagrams/system/system-architecture.html) · [Data Model Viewer](../../docs/diagrams/system/system-data-model.html)

## Traceability

| Capacity | API | Bruno | Playwright | Archify diagram |
|---|---|---|---|---|
| `platform-infrastructure` | `GET /actuator/health`, `GET /actuator/prometheus` | 2 files (see spec `## Mapping`) | `service-scheduler-ui/tests/e2e/platform-infrastructure.spec.ts` | `docs/diagrams/system/system-architecture.html` + `system-data-model.html` |
| `otp-login` | `POST /auth/otp/send`, `POST /auth/otp/verify`, `GET /auth/me`, `PATCH /auth/me`, `POST /auth/logout` | 24 files, one per scenario | `service-scheduler-ui/tests/e2e/otp-login.spec.ts` | `docs/diagrams/system/otp-login.html` |
| `customer-registration` | `POST /auth/register` (+ Zalo-stub OTP dispatch) | 8 files, one per scenario | `service-scheduler-ui/tests/e2e/customer-registration.spec.ts` | `docs/diagrams/system/customer-registration.html` |

## AGENTS Section 4 constraint mapping

| Constraint | Applies here |
|---|---|
| 1. Package independence | Feature packages `auth/`, `customer/`, `dealership/`, `resource/`, plus `common/` and `infra/`; ArchUnit test enforces no cross-feature imports, no servlet imports in services, and controller-to-service layering. |
| 2. Virtual threads | `spring.threads.virtual.enabled=true`; filter and limiter hold no `synchronized` and no pinning structures. |
| 3. Pure Java constraint evaluator | Not touched this phase (no service-type logic). |
| 4. PostgreSQL EXCLUDE booking gates | Not touched (no booking tables in V1); noted as intentionally deferred. |
| 5. Transactional outbox | No appointment mutation and no notification persistence exist. The after-commit OTP dispatch (D11) previews the boundary discipline; outbox design untouched. |
| 6. Idempotency keys | `register` and `PATCH me` enforce `X-Idempotency-Key` (400 without it, replay-safe). D8 records the `otp/send` exemption for ratification at review. |
| 7. OpenAPI 3.1 + RFC 7807 | SpringDoc on all five auth endpoints; one problem+json handler for 400/401/404/409/429. |

## Open Questions

None. The register-vs-verify conflict (D2) is the one product-facing call; it is flagged for the HIL-1 gate along with this proposal.
