# Tasks

```
CHANGE   phase-1-bootstrap-otp-auth      BRANCH   feature/phase-1-bootstrap-otp-auth
STATES   14   done 13  current 14  next 0   skipped 0  failed 0  pending 0
TASKS    39   done 39  open 0

  #   state                     mark     proof
  1   Propose                   done     openspec validate --strict: valid + HIL-1 approval (8 rounds)
  2   Branch                    done     empty master 1611a24 + feature/phase-1-bootstrap-otp-auth
  3   Plan                      done     D2 rate limit JSON + D8 idempotency + D7 OTP slots
  4   Code                      done     auth endpoints + entities + filters + rate limit config
  5   Format                    done     spotlessApply clean; Palantir 2.71
  6   Test                      done     115 unit tests + 49 Bruno requests + 18 Playwright E2E
  7   Review                    done     ocr delegate preview clean; review.md documented
  8   Verify                    done     clean build SUCCESS + 97.7% line + 100% @Service + OpenSpec valid
  9   Request approval          done     HIL-2 approval granted by user
  10  Publish                   done     PR #1 opened vs master: https://github.com/hata83/service-scheduler/pull/1
  11  Verify PR                 done     all 6 checks green (Build, Bruno, Playwright, OpenSpec, OCR)
  12  Archive                   done     openspec archive: 2026-10-26-phase-1-bootstrap-otp-auth
  13  Review archive            done     archive verified on PR #1; all CI checks green
  14  Merge                     current  HIL-3 human gate
```

## 1. Branch setup (State 2)

- [x] 1.1 Run `git init`, commit the openspec baseline, then create `feature/phase-1-bootstrap-otp-auth` from it. Verify `git branch` shows the feature branch and `git status` is clean.

## 2. Gradle module restructure (PostgreSQL-only)

- [x] 2.1 Convert root `build.gradle.kts` to parent-only config (plugins `apply false`), add `include("service-scheduler-server")` in `settings.gradle.kts`, and create `service-scheduler-server/build.gradle.kts` carrying the moved dependencies plus new ones: `spring-boot-starter-data-redis`, `jjwt-api/impl/jackson`, `logstash-logback-encoder`, `micrometer-tracing-bridge-otel`, Testcontainers `postgresql` + `junit-jupiter`. Keep no H2 dependency anywhere. Verify `./gradlew :service-scheduler-server:dependencies` lists no H2 artifact and the tasks task succeeds. (done — commit daacced; runtimeClasspath proof: only `postgresql` present, zero `h2database` lines; Gradle-9 note: parent declares only external plugins with `apply false`, and the integrationTest Test task is registered explicitly.)
- [x] 2.2 Create `service-scheduler-server/config/checkstyle/checkstyle.xml` (no wildcard imports, 120-char lines, import order) and `service-scheduler-server/config/pmd/pmd-ruleset.xml`. Verify `./gradlew :service-scheduler-server:checkstyleMain :service-scheduler-server:pmdMain` run without config errors. (done — checkstyleMain/checkstyleTest/pmdMain all pass, errorprone-category ruleset)
- [x] 2.3 Add the `integrationTest` source set wired to Testcontainers (PostgreSQL 16 + Redis 7) matching ci.yml. Verify `./gradlew :service-scheduler-server:integrationTest` passes with zero tests. (done — integrationTest NO-SOURCE passes, wired to Testcontainers BOM 1.21.3)

## 3. Platform infrastructure (`platform-infrastructure`)

- [x] 3.1 Write `service-scheduler-server/src/main/resources/application.yml` with the profile matrix from design D3 — default: PostgreSQL + Redis from compose; `test`: same engines from the CI/Testcontainers env URLs, echo, OTP TTL 15 s, and `app.ratelimits.location` pointed at `config/rate-limits-test.json` with low windows; `prod`: fail-fast secrets; `spring.threads.virtual.enabled=true`; actuator exposure `health,prometheus`; OTel tracing bridge with exporter disabled per D12. Verify `./gradlew bootRun` (default profile) starts against the compose services.
- [x] 3.2 Add `Dockerfile` (multi-stage: Gradle build on JDK 25, runtime on JRE 25) and `docker-compose.yml` with `postgres:16-alpine` and `redis:7-alpine` (healthchecks) plus an `app` service under profile `full` per design D10. Verify `docker compose up -d postgres redis` reaches both healthchecks.
- [x] 3.3 Point the CI gradle `test` and Bruno steps at the CI postgres + redis services via `SPRING_DATASOURCE_URL` and `SPRING_DATA_REDIS_HOST` envs, since no H2 fallback exists. Verify the ci.yml parses and the env names match the profile config keys.
- [x] 3.4 Add JSON logging (logstash encoder) with a `requestId` MDC filter and trace correlation fields per design D12. Verify one sample request logs a single JSON line containing `requestId` and `traceId`.
- [x] 3.5 Lock the actuator to `health` + `prometheus`, and confirm health carries `db` and `redis` indicators per design D12. Verify `/actuator/health` returns 200 `UP` with both components, `/actuator/prometheus` includes JVM meter families, and `/actuator/env` returns 404.
- [x] 3.6 Add Bruno files `api/platform/health-db-redis.bru` and `api/platform/prometheus-metrics.bru`, executed against the compose-backed server. Verify both pass via `npx @usebruno/cli run`.
- [x] 3.7 Update the `### Capacity Mapping` table in `specs/platform-infrastructure/spec.md` if requirement names shift, then verify `system-architecture.html` and `system-data-model.html` viewers exist and pass `archify validate`. Verify one row per `### Requirement:` block.
- [x] 3.8 Create `service-scheduler-ui/tests/e2e/platform-infrastructure.spec.ts` (API-level Playwright E2E, one test per scenario) plus minimal Playwright setup, run against the compose-stacked database server. Verify it passes.

## 4. Schema and seed (Flyway, PostgreSQL-only)

- [x] 4.1 Add `V1__platform_and_auth_schema.sql` for `dealerships`, `users`, `customers`, `technicians`, `technician_skills` — PostgreSQL-only DDL per design D4 (UUID PKs, `timestamptz` audit columns, role CHECK). Verify `bootRun` (compose) and `integrationTest` (Testcontainers) both apply it, and that nothing references H2.
- [x] 4.2 Add `V2__seed_dealer_and_auth.sql` seeding 1 demo dealership, 1 admin, 1 manager, 1 technician (+skills row), and 1 customer scoped to that dealership, with the fixed UUIDs and phones from design D4. Verify `integrationTest` counts 4 user rows, the matching profile rows, and the customer's dealership reference after migration. (HIL-1 round 9 note: earlier text said 5 user rows; spec scenario, proposal wording, and design D4 all pin four accounts, so the stray count is corrected instead of seeding a phantom 5th user.)

## 5. OTP login capacity (`otp-login`)

- [x] 5.1 TDD BE-1/BE-2: failing unit test for `OtpStorage` (purpose-scoped keys `otp:login:{phone}` and `otp:register:{phone}` per design D7, SecureRandom 6-digit code, configurable TTL, single-use delete, constant-time compare), then implement. Verify the test fails before and passes after.
- [x] 5.2 TDD BE-1/BE-2: failing unit test for the `RateLimitConfig` JSON loader (reads `config/rate-limits.json` with a `default` block and a `dealerships` override map per design D7; the `test` profile selects `rate-limits-test.json` with low values) and for `RedisWindowCounter` (INCR + EXPIRE per ADR-007; limits spanning send, verify, and register resolved through that loader), then implement both. Verify the tests fail before and pass after.
- [x] 5.3 TDD BE-1/BE-2: failing unit test for `ZaloStubOtpChannel` (console print per design D7, echo flag respected in non-prod only), then implement the `OtpChannel` seam. Verify the test fails before and passes after.
- [x] 5.4 Implement `POST /api/v1/auth/otp/send` (`{phone, type}` per D2, channel stub dispatch, idempotency key required with cached replay per D8, rate limited, problem+json errors, SpringDoc annotations, echo in non-prod). Add Bruno files `api/auth/otp-send-login-customer.bru`, `api/auth/otp-send-register-customer.bru`, `api/auth/otp-send-unknown-type.bru`, `api/auth/otp-send-invalid-phone.bru`, `api/auth/otp-send-rate-limited.bru`, `api/auth/otp-send-missing-idempotency-key.bru`, `api/auth/otp-send-idempotent-replay.bru`. Verify all seven pass via `npx @usebruno/cli run` against the compose server.
- [x] 5.5 TDD BE-1/BE-2: `JwtService` (jjwt HS256, claims per design D5, 8h expiry, jti) + Redis session store (`auth:session:{jti}`). Verify the unit test fails before and passes after.
- [x] 5.6 Implement `AuthFilter`, `@RequireRole`, `GET /api/v1/auth/me`, `POST /api/v1/auth/logout` per design D5/D6. Add Bruno files `api/auth/me-after-login.bru`, `api/auth/me-unauthenticated.bru`, `api/auth/logout-revokes-session.bru`. Verify all three pass.
- [x] 5.7 Implement `POST /api/v1/auth/otp/verify` (login-only: consumes LOGIN-purpose OTP, never creates rows; idempotency key required with cached replay; wrong/expired/wrong-purpose/unknown-phone 401 problem+json; unknown-phone detail names registration). Add Bruno files `api/auth/login-customer.bru`, `api/auth/login-manager.bru`, `api/auth/login-technician.bru`, `api/auth/login-admin.bru`, `api/auth/verify-wrong-code.bru`, `api/auth/verify-expired-code.bru`, `api/auth/verify-rejects-register-purpose.bru`, `api/auth/verify-unknown-phone.bru`, `api/auth/verify-missing-idempotency-key.bru`, `api/auth/verify-idempotent-replay.bru`. Verify all ten pass.
- [x] 5.8 TDD BE-1/BE-2: `ProfileService` (email for every role; names for CUSTOMER and TECHNICIAN via design D13; phone field rejected), then implement `PATCH /api/v1/auth/me` with idempotency. Add Bruno files `api/auth/me-patch-updates-profile.bru`, `api/auth/me-patch-rejects-phone.bru`, `api/auth/me-patch-rejects-staff-names.bru`, `api/auth/me-patch-missing-idempotency-key.bru`. Verify the test fails before and passes after, then all four Bruno files pass.
- [x] 5.9 Refresh `docs/diagrams/src/system/otp-login.sequence.json` if task work shifts the flow (the viewer `docs/diagrams/system/otp-login.html` was finalized and browser-checked at State 1), re-run `archify finalize sequence ... --quality showcase`. Verify `archify validate` passes and the viewer is current, then link the diagram from the capacity's spec mapping.
- [x] 5.10 Update the `### Capacity Mapping` table in `specs/otp-login/spec.md` if requirement names shift during implementation. Verify one row per `### Requirement:` block with its diagram reference.
- [x] 5.11 Create `service-scheduler-ui/tests/e2e/otp-login.spec.ts` (API-level Playwright E2E, one test per scenario) against the compose-stacked server. Verify it passes.

## 6. Customer registration capacity (`customer-registration`)

- [x] 6.1 TDD BE-1/BE-2: failing unit test for `RegisterService` (verifies the REGISTER-purpose OTP from `otp:register:{phone}` per design D11, inserts user + profile, issues the session JWT, idempotency cache per ADR-008 with no second consumption, CUSTOMER role pin, duplicate 409, IP rate limit), then implement. Verify the test fails before and passes after.
- [x] 6.2 Implement `POST /api/v1/auth/register` (201 + `Set-Cookie: jwt`, idempotency key required with the `idempotency:register:{phone}:{key}` record per D8, IP rate limit from the JSON config, SpringDoc annotations, problem+json responses for 400/401/403/409/429). Add Bruno files `api/auth/register-customer-201.bru`, `api/auth/register-wrong-otp.bru`, `api/auth/register-rejects-login-purpose.bru`, `api/auth/register-elevates-role.bru`, `api/auth/register-duplicate-phone.bru`, `api/auth/register-rate-limited.bru`, `api/auth/register-missing-idempotency-key.bru`, `api/auth/register-idempotent-replay.bru`. Verify all eight pass.
- [x] 6.3 Refresh `docs/diagrams/src/system/customer-registration.sequence.json` if task work shifts the flow (the viewer `docs/diagrams/system/customer-registration.html` was finalized and browser-checked at State 1), re-run `archify finalize sequence ... --quality showcase`. Verify `archify validate` passes and the viewer is current, then link the diagram from the capacity's spec mapping.
- [x] 6.4 Update the `### Capacity Mapping` table in `specs/customer-registration/spec.md` if requirement names shift. Verify one row per `### Requirement:` block with its diagram reference.
- [x] 6.5 Create `service-scheduler-ui/tests/e2e/customer-registration.spec.ts` (API-level; typed send → one-shot register with echoed OTP → logged-in identity via me → profile editable) against the compose-stacked server. Verify it passes.

## 7. Documentation alignment

- [x] 7.1 Amend ADR-011: purpose-typed `otp/send`, one-shot register that creates the account and issues the session, and login-only verify that never creates rows; amend ADR-008 so the idempotency rule covers `otp/send` and `otp/verify` with their cached-replay semantics (no exemptions). Verify both ADRs state the amendment and date.
- [x] 7.2 Update `docs/SYSTEM_DESIGN.md` §14.1 to list `register` (201 with session, body with `code`), `me` (GET/PATCH), and the profile-edit semantics, and update the walk-in flow lines in §3.3 that reference registration. Verify no `otp/request` string remains in docs.
- [x] 7.3 Update `AGENTS.md` Section 6 stack table (drop the H2 row; state PostgreSQL via compose + Testcontainers) and the H2 references in the openspec context. Verify no "H2" remains in `AGENTS.md`, `openspec/config.yaml`, or the module build.

## 8. Final gates

- [x] 8.1 Run `./gradlew :service-scheduler-server:spotlessApply`, then `checkstyleMain :service-scheduler-server:checkstyleTest :service-scheduler-server:pmdMain :service-scheduler-server:test :service-scheduler-server:jacocoTestReport :service-scheduler-server:jacocoTestCoverageVerification`. Verify all green with 100% on `@Service` classes and 80% overall.
- [x] 8.2 Add the ArchUnit package-independence test (`auth` and `common` packages only; no cross-feature imports, AGENTS constraint 1). Verify it passes.
- [x] 8.3 Run `./gradlew clean build`. Verify the full gate passes with no H2 artifact on any classpath.
- [x] 8.4 Run the full Bruno suite: `npx --yes @usebruno/cli@2.14.1 run service-scheduler-server/src/test/bruno --env local` against `bootRun` on the compose stack. Verify 34 files, 0 failures.
- [x] 8.5 Run `ocr delegate preview` then `ocr delegate run --format json`; fix every critical and high finding and re-run until clean. Document all findings, reproduction steps, and proofs in `openspec/changes/phase-1-bootstrap-otp-auth/review.md`. Verify the final review reports none.
- [x] 8.6 Run `openspec validate phase-1-bootstrap-otp-auth --strict`. Verify valid. Then present States 5–8 evidence for the HIL-2 approval gate.
