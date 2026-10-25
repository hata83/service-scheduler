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
- **D4. PostgreSQL-only V1/V2.** With H2 gone, migrations target PostgreSQL only: plain `CREATE TABLE` with `UUID` PKs (Hibernate-generated, no DB default needed), `timestamptz` audit columns, role CHECK constraint. No `{vendor}` Flyway split is needed at any phase anymore. V2 seeds fixed UUIDs and phones (+84900000001 admin, ...0002 manager, ...0003 technician, ...0004 customer) plus the demo dealership. Fixed seed IDs: dealership `00000000-0000-0000-0000-000000000001`; admin id `...0002`, manager `...0003`, technician `...0004`, customer `...0005` (full form `00000000-0000-0000-0000-00000000000N`). The technician gets a skills row (`ENGINE` at `TECHNICIAN` level) so profile joins resolve, and the seeded customer row carries the demo dealership id, matching the Capacity 1 seed contract. The circular FK (`dealerships.created_by → users`, `users.dealership_id → dealerships`) resolves in V1 via ALTER and in V2 via users-first seeding plus a dealership back-fill.
- **D5. JWT via `jjwt`.** HS256 with claims `sub` (user id), `role`, `did`, `phone`, `jti`, 8-hour expiry; cookie name `jwt`, `httpOnly`, `SameSite=Strict`, `Secure` in prod. Logout deletes `auth:session:{jti}` in Redis; the filter checks signature and jti existence on every protected call (ADR-005). Alternative: hand-rolled Base64url+HMAC — rejected, crypto subtlety is worth one dependency. Alternative: Spring Security autoconfig — rejected for phase 1 ( CSRF/session defaults exceed scope); revisit when SSE or multi-surface auth lands.
- **D6. Filter-per-request auth.** One servlet `AuthFilter` (class: `common/`) parses the cookie, validates the JWT, loads the jti from Redis, and publishes a `Caller` principal; `@RequireRole` on controller methods enforces RBAC (403). No locks, no shared mutable state — virtual threads stay unpinned (constraint 2 in AGENTS §4).
- **D7. OTP channels and rate-limit config.** An `OtpChannel` interface sends the code; `ZaloStubOtpChannel` is the Phase 1 implementation and prints the code to the server console (non-prod profiles additionally echo it in the response), matching the user-approved stub for the Zalo ZNS integration that arrives with notifications. Redis stores one OTP slot per purpose: `otp:login:{phone}` and `otp:register:{phone}`, each a single-use record with a 5-minute default TTL. A `RedisWindowCounter` (INCR + EXPIRE, ADR-007) enforces the limits. Limits load from a bundled JSON resource, not hardcoded properties: `service-scheduler-server/src/main/resources/config/rate-limits.json` defines a `default` block (`otpSendPerPhonePurposePer10Min: 5`, `otpSendPerIpPerMin: 30`, `otpVerifyPerIpPerMin: 10`, `registerPerIpPerMin: 10`) plus a `dealerships` map of per-dealership overrides keyed by dealership UUID; the loader resolves a dealership's block with defaults as fallback. The `test` profile selects `config/rate-limits-test.json` (same schema, low values: 2-request windows, 10-second units, OTP TTL 15 s) so Bruno integration tests hit 429 and expiry fast. Resilience4j modules stay unadded until a later phase needs circuit breakers or retries.
- **D8. Idempotency family-wide.** Per ADR-008 no exemptions remain: every auth write endpoint carries `X-Idempotency-Key` (`otp/send`, `otp/verify`, `register`, `PATCH me`). A missing key answers 400; the first response caches for 24 hours. **Key semantics (HIL-1 round 8):** the effective cache record is `idempotency:{endpoint}:{phone}:{header-value}`, so a header value alone never collides across endpoints or phones. The recommended value is a phone-derived deterministic pattern — `otp-send-{TYPE}-{phone}`, `otp-verify-{phone}`, `register-{phone}` — which Phase 1 clients (Bruno, Playwright, Postman reviewers) use verbatim; a client may still send any unique string when it wants a genuinely fresh operation (new key = fresh op, e.g. a real resend). Replay per endpoint: send returns the cached 202 with no re-dispatch; verify and register return the cached 200/201 session with no second OTP consumption and no second insert; patch me returns the cached patch. Alternative: keying the cache on bare phone with no header — rejected, it removes the ability to resend ever and invents a second rule next to ADR-008; and: exempting send from idempotency — rejected at round 7, the cached replay stops accidental resends and keeps the header rule uniform.
- **D9. Errors and docs.** One `GlobalExceptionHandler` emits `application/problem+json` for 400/401/404/409/429 (ADR-006); SpringDoc annotates every handler; actuator exposes only `health` and `prometheus`; logging is JSON via logstash encoder with a `requestId` MDC filter.
- **D10. Docker.** Compose: `postgres:16-alpine` (healthcheck `pg_isready`), `redis:7-alpine` (`redis-cli ping`), and an optional `app` service under profile `full` to respect the AGENTS rule that the jar and the compose app never bind 8080 together. The Dockerfile is multi-stage (Gradle build → JRE 25 runtime).
- **D11. Purpose-typed consumption.** `RegisterService` runs the OTP check, row insert, and JWT issue inside one transaction boundary in its service layer: the OTP is consumed from `otp:register:{phone}` (delete-on-verify semantics), the rows commit, and the session cookie returns on the same 201. A replayed idempotency key returns the cached response and touches neither Redis nor the database. The after-commit listener pattern stays unused here because no dispatch exists; it re-enters with the notification-phase outbox (ADR-004). Alternative: an escalating multi-attempt counter per OTP — rejected for Phase 1; the single-use record plus rate limits bound abuse cheaply, and constant-time code comparison lands with the `OtpStorage` tests.
- **D12. Observability.** Micrometer Tracing over the OTel bridge with `management.tracing.enabled` sampling `always_on` locally but the OTLP exporter disabled by default, so trace ids enrich logs without shipping spans; the registry adds a structured-JSON (logstash) layout with `requestId` + `traceId` fields. Actuator is locked to `health` and `prometheus` (AGENTS runtime-safety rule), health carries `db` and `redis` indicators, and SpringDoc annotates everything it exposes. Alternative: Brave bridge + Zipkin server — rejected for footprint; the exporter flag flips the bridge later with no code change.
- **D13. Profile edit.** `PATCH /api/v1/auth/me` updates only what the schema can hold: `users.email` for every role, and `customers`/`technicians` names for CUSTOMER/TECHNICIAN. Phone is immutable (login identity). The seeded customer belongs to the demo dealership, so its `technicians`-style scoping rehearses the dealership-bound identity the booking phase will rely on. Alternative: a users-level name column — rejected, it forks the schema this change must not rewrite.

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

The clarified flow (purpose-typed `otp/send`, one-shot register, login-only verify, Zalo stub, profile edit) is captured in two sequence diagrams that are already finalized and browser-checked at State 1; apply only refreshes and exports SVGs from the viewers:

![OTP login flow](../../docs/diagrams/system/otp-login.svg)

Viewer: `docs/diagrams/system/otp-login.html`

![Customer registration flow](../../docs/diagrams/system/customer-registration.svg)

Viewer: `docs/diagrams/system/customer-registration.html`

![Platform runtime and data model](../../docs/diagrams/system/system-architecture.svg)

Viewer: `docs/diagrams/system/system-architecture.html` (reused with no content change; cited by `platform-infrastructure` alongside `system-data-model.svg`)

## Traceability

| Capacity | API | Bruno | Playwright | Archify diagram |
|---|---|---|---|---|
| `platform-infrastructure` | `GET /actuator/health`, `GET /actuator/prometheus` | 2 files (see spec `## Mapping`) | `service-scheduler-ui/tests/e2e/platform-infrastructure.spec.ts` | `docs/diagrams/system/system-architecture.svg` + `system-data-model.svg` |
| `otp-login` | `POST /auth/otp/send`, `POST /auth/otp/verify`, `GET /auth/me`, `PATCH /auth/me`, `POST /auth/logout` | 24 files, one per scenario | `service-scheduler-ui/tests/e2e/otp-login.spec.ts` | `docs/diagrams/system/otp-login.svg` + viewer |
| `customer-registration` | `POST /auth/register` (+ Zalo-stub OTP dispatch) | 8 files, one per scenario | `service-scheduler-ui/tests/e2e/customer-registration.spec.ts` | `docs/diagrams/system/customer-registration.svg` + viewer |

## AGENTS Section 4 constraint mapping

| Constraint | Applies here |
|---|---|
| 1. Package independence | `auth/` and `common/` only; ArchUnit test in the final gates; `auth` imports nothing from other feature packages (none exist yet). |
| 2. Virtual threads | `spring.threads.virtual.enabled=true`; filter and limiter hold no `synchronized` and no pinning structures. |
| 3. Pure Java constraint evaluator | Not touched this phase (no service-type logic). |
| 4. PostgreSQL EXCLUDE booking gates | Not touched (no booking tables in V1); noted as intentionally deferred. |
| 5. Transactional outbox | No appointment mutation and no notification persistence exist. The after-commit OTP dispatch (D11) previews the boundary discipline; outbox design untouched. |
| 6. Idempotency keys | `register` and `PATCH me` enforce `X-Idempotency-Key` (400 without it, replay-safe). D8 records the `otp/send` exemption for ratification at review. |
| 7. OpenAPI 3.1 + RFC 7807 | SpringDoc on all five auth endpoints; one problem+json handler for 400/401/404/409/429. |

## Open Questions

None. The register-vs-verify conflict (D2) is the one product-facing call; it is flagged for the HIL-1 gate along with this proposal.
