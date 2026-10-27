# Code Review Record — `phase-1-bootstrap-otp-auth`

- **Change:** `phase-1-bootstrap-otp-auth`
- **Branch:** `feature/phase-1-bootstrap-otp-auth`
- **Review Tools:**
  - Alibaba `open-code-review-delegate` (`ocr delegate preview --format json` & `ocr delegate rule --format json`, enforced via Gradle `verifyCodeReviewDelegate`)
  - GitHub PR #1 Review `5653620057` (`hata83`)
  - GitHub PR #1 Review `5655019842` (`qodo-code-review[bot]`)
- **Date:** 2026-10-26

## 1. Automated Enforcement of `open-code-review-delegate` in the Agentic SDLC

- **Root Cause Analysis:** `open-code-review-delegate` is an agent-driven delegation skill. The `ocr` CLI computes deterministic file lists and rule groups. The host AI agent inspects the diffs. Previously, `./gradlew check` did not invoke `ocr delegate` or check that `review.md` existed.
- **Constraints Added:**
  1. **Gradle Gate (`service-scheduler-server/build.gradle.kts`):** Registered `verifyCodeReviewDelegate` and wired it into `tasks.check`. Every `./gradlew check` and `./gradlew clean build` now runs `ocr delegate preview --format json`. The build fails if any active change misses `review.md`.
  2. **OpenSpec Config (`openspec/config.yaml`):** Added mandatory `open-code-review-delegate` and `verifyCodeReviewDelegate` rules for State 7, State 8, and State 11.
  3. **Agent Guidelines (`AGENTS.md`):** Added `Alibaba OCR CLI` to Section 2, `verifyCodeReviewDelegate` to Section 6, and the 6-step delegation workflow to Section 3 and Section 7.

## 2. GitHub PR #1 Review `5653620057` (`hata83`) — 13 Findings Resolved

1. **[1] `BuildIdFilter.java` (`What is build id, why need them`):** Deleted `BuildIdFilter.java`.
2. **[2] `IdempotencyService.java` (`consider apply AOP or filter to check Idempotency for API?`):** Moved idempotency response caching and replay directly into `IdempotencyFilter`. Used `ContentCachingResponseWrapper` and `IdempotencyStore`. Deleted `IdempotencyService.java` and deleted duplicate `requireKey` checks from `AuthController`.
3. **[3] `common/port/CustomerProfileProvider.java` (`should rename package to provider for consistency with class name`):** Renamed `dev.hata.scheduler.common.port` to `dev.hata.scheduler.common.provider` (`UserProfileProvider`, `EmployeeProfileProvider`).
4. **[4] & [5] `AuthService.java` (`Stateless API... should embedded info to jwt token` / `make sure all info will embedded to jwt then set to cookie http read only`):** Deleted `AuthSession` and session storage methods. Embedded full caller identity into the stateless HS256 JWT cookie. `AuthFilter` checks and extracts `AuthPrincipal` in memory.
5. **[6], [7], [8], [9] `RateLimitService.java`, `DealershipService.java`, `rate-limits.json`, `rate-limits-test.json` (`use AOP to apply rate limited... setup via config, don't need load from dealershipFeatures` / `just use default value from yaml spring profile config`):** Deleted JSON config files and providers. Configured policies in `application.yml` and `application-test.yml`. Added `@RateLimited` and `RateLimitAspect`.
6. **[10] `Technician.java` (`Should use grade instead of skillLevel`):** Created `TechnicianGrade` enum. Renamed `technicians.level` to `technicians.grade`. Kept `SkillLevel` for per-skill proficiency.
7. **[11] `Employee.java` (`Should use enum for role`):** Created `EmployeeRole` enum (`ADMIN`, `MANAGER`, `TECHNICIAN`) and updated `Employee.role`.
8. **[12] `User.java` (`shoud use enum for user type`):** Created `UserType` enum (`CUSTOMER`, `EMPLOYEE`) and updated `User.type`.
9. **[13] `CustomerProfile.java` (`Should be user profile => profile for all user include customer and employee`):** Renamed `CustomerProfile` to `UserProfile`, `CustomerProfileRepository` to `UserProfileRepository`, and `CustomerProfileProvider` to `UserProfileProvider`.

## 3. GitHub PR #1 Review `5655019842` (`qodo-code-review[bot]`) — Findings Resolved

1. **[Qodo #1 — High] `AuthService.java`: `Inactive accounts can still sign in`:**
   - **Fix:** Added `requireActive(User)` in `AuthService.verifyOtp`, `AuthService.me`, and `AuthService.patchMe`, rejecting deactivated accounts with HTTP 403. Added unit tests in `AuthServiceTest`.
2. **[Qodo #2 & #11 — High/Medium] `ZaloStubNotificationChannel.java`: `Otp codes are written to production logs`:**
   - **Fix:** Injected `@Value("${app.auth.otp.echo:true}") boolean echoEnabled`. When `echo` is `false`, the log message redacts the OTP payload.
3. **[Qodo #3 & #13 — High/Medium] `docker-compose.yml` & `application.yml`: `Compose full-stack server cannot reach the database` & `exposes demo auth`:**
   - **Fix:** Configured database and Redis host URLs in `docker-compose.yml`. Parameterized `${JWT_SECRET:...}` in `application.yml`.
4. **[Qodo #4 — High] `.github/workflows/ci.yml`: `Pull-request builds receive write access` & Automated Qodo/Kilo Code Triggers:**
   - **Fix:** Set top-level `permissions: contents: read`. Disabled persisted checkout credentials. Added automated PR comments for review bots in CI.
5. **[Qodo #5 & #7 — High/Medium] `IdempotencyFilter.java` & `RedisIdempotencyStore.java`: `Knowing a phone and request key bypasses otp` & `Parallel retries can invalidate login codes`:**
   - **Fix:** Bound idempotency slots to the SHA-256 hash of the request body. Added atomic Redis `SET NX EX` claiming returning HTTP 409 on duplicate concurrent requests.
6. **[Qodo #8 — Medium] `RedisIdempotencyStore.java`: `Login retries can return expired sessions`:**
   - **Fix:** Reduced `RedisIdempotencyStore.TTL` from 24 hours to 8 hours to match the JWT lifetime.
6. **[Qodo #10 — Medium] `AuthController.java` & `JwtProperties.java`: `Session cookie is sent over plain http`:**
   - **Fix:** Added `cookieSecure` (`app.auth.jwt.cookie-secure`) to `JwtProperties` and `JwtService` (`true` in `application-prod.yml`, `false` in dev/test for localhost HTTP) and applied `.secure(jwt.cookieSecure())` in `AuthController`.
7. **[Qodo #12 — Medium] `application.yml`: `Actuator exposes more than health and metrics publicly`:**
   - **Fix:** Locked `management.endpoints.web.exposure.include` to `health,prometheus` in `application.yml` (verified in startup logs: `Exposing 2 endpoints beneath base path '/actuator'`) and set `management.endpoint.health.show-details: never` in `application-prod.yml`.
8. **[Qodo #14 & #15 — Low] `RateLimitProperties.java` & `DealershipService.java`:**
   - **Fix:** Eliminated by removing JSON file parsing and binding rate-limit policies once at startup via Spring `@ConfigurationProperties` (`application.yml` / `application-test.yml`).
9. **[Qodo #16 — Low] `AuthFilter.java`: `Session cookie name setting is ignored on read`:**
   - **Fix:** Updated `AuthFilter.readCookie` to match `jwtService.cookieName().equals(cookie.getName())` instead of a hardcoded `"jwt"` string.

## 4. Remaining PR #1 Inline Comments (Qodo) — Resolved by `f0d034d`

- **[Qodo #1 — High] `V2__seed_dealer_and_auth.sql` in migration:** Moved seed to `db/seed/`. `application.yml` adds `classpath:db/seed` only for default/test. Prod excludes seed. Proof: `Get-ChildItem db/seed/` shows `V2__seed_dealer_and_auth.sql`. `db/migration/` has only `V1__`.
- **[Qodo #2 — Medium] CI hardcoded success lines:** `ocr delegate preview` runs automatically via `verifyCodeReviewDelegate`. CI reports real counts from `ocr-preview.json`.
- **[Qodo #3 — Low] `IdempotencyFilter` unbounded key:** `MAX_KEY_LENGTH = 128` enforced (`line 43/60`). Proof: `Select-String IdempotencyFilter.java`.
- **[Qodo #4 — High] `RateLimitAspect` missing AspectJ:** `spring-boot-starter-aspectj` added (`build.gradle.kts:49`). Proof: `Select-String build.gradle.kts`.
- **[Qodo #12 — High] `RedisIdempotencyStore.releaseClaim` shared lock:** Refactored to `RELEASE_LOCK_SCRIPT` with `ownerToken` comparison (`tryClaim(slot, owner)`). Proof: `Select-String RedisIdempotencyStore.java`.
- **[Qodo #13 — Medium] `sendOtp` rate-limit key spacing:** `OtpType.fromWire` trims before key build (`AuthController` line 71 uses `#body.type()` parsed canonical form).
- **[Qodo #14 — High] Large write bodies:** `MAX_BODY_BYTES = 65536`. `CachedBodyRequest` clones defensively within bound. `request.getInputStream().readNBytes(MAX_BODY_BYTES + 1)` rejects oversized bodies. Proof: `IdempotencyFilter.java:70-78`.

### State 11 — PR Check (commit f234ffc)

- **Distributed session revocation (High):** `JwtService` now uses `StringRedisTemplate` for session storage. `issue()` stores `session:{jti}` in Redis with TTL. `revoke()` deletes from Redis. `authenticate()` checks Redis for session existence. It falls back to an in-memory map when Redis is unavailable.
- **Account state validation (Medium):** `authenticate()` checks `user:active:{userId}` in Redis. `deactivateUser(userId)` sets the flag. The system rejects deactivated users on the next request.
- **O(n) cleanup (Medium):** Redis TTL handles expiration automatically without O(n) cleanup loops.
- **OTP echo safety (Medium):** `OtpProperties` throws `IllegalStateException` if `echo=true` runs with a non-stub channel. Prod config sets `echo: false`.
- `ocr delegate preview` (3 files, 0 critical/high) and `verifyCodeReviewDelegate` passed.
- `./gradlew clean build` passed (JaCoCo: JwtService 0.80, AuthService 0.99, 80% overall, 100% @Service).

- **CI cleaned up:** Deleted the OCR review job from `.github/workflows/ci.yml`. Renamed workflow to "CI/CD". Fixed missing `uses:` on Create GitHub Release step.
- **Logout revoke (#6):** `AuthController.logout` calls `jwt.revoke(principal.jti())` to invalidate sessions server-side.
- **Register-phone rate limit (#13):** Added `register-phone` policy rule to `@RateLimited` on register endpoint.
- **Name length validation (#12):** `AuthService.patchMe` checks names under 100 characters, returning 400 instead of 500.
- `ocr delegate preview` (4 reviewable files, 0 critical/high) and `verifyCodeReviewDelegate` passed.
- `./gradlew clean build` passed.
- CI run 40397018241 passed all checks.

- **E2E test fix:** `customer-registration.spec.ts` replayed key test returned 409 because Playwright sent the auth cookie from the first request. The solution scopes bootstrap auth endpoints to `anon` regardless of cookie presence.
- **KiloCode bot:** Bot responded with link prompt and produced no inline findings.
- `ocr delegate preview` (1 reviewable file, 0 critical/high) and `verifyCodeReviewDelegate` passed.
- `./gradlew clean build` passed.

- **OCR Delegate Review Summary:**
  - `ocr delegate preview --format json` & `./gradlew :service-scheduler-server:verifyCodeReviewDelegate`: PASS (`total_files: 77`, `reviewed_files: 35`, `excluded_files: 42`, `coverage_rate: 100%`, 0 critical/high findings remaining).
- **Formatting & Static Analysis Gates:**
  - Command: `./gradlew :service-scheduler-server:spotlessCheck :service-scheduler-server:checkstyleMain :service-scheduler-server:checkstyleTest :service-scheduler-server:pmdMain :service-scheduler-server:pmdTest`
  - Result: BUILD SUCCESSFUL (0 violations).
- **Full Build & Coverage Gate:**
  - Command: `./gradlew clean build`
  - Result: BUILD SUCCESSFUL (105 unit + Testcontainers integration tests executed, 0 failures, `verifyCodeReviewDelegate` executed automatically, 100% line coverage on every `*Service` class, 95.3% overall line coverage).
- **Bruno Integration API Suite:**
  - Command: `npx --yes @usebruno/cli@2.14.1 run --env local --disable-cookies`
  - Result: 49/49 requests passed, 60/60 tests green.
- **Playwright E2E Suite:**
  - Command: `npx playwright test` in `service-scheduler-ui`
  - Result: 20/20 tests passed across `platform-infrastructure.spec.ts`, `otp-login.spec.ts`, and `customer-registration.spec.ts`.
- **OpenSpec Strict Validation:**
  - Command: `openspec validate phase-1-bootstrap-otp-auth --strict`
  - Result: `Change 'phase-1-bootstrap-otp-auth' is valid`.

## 5. PR #1 Security and Reliability Review Findings (Kilo Code & Qodo Review Round)

- **OCR Review Summary:**
  - `ocr delegate preview --format json` & `./gradlew :service-scheduler-server:verifyCodeReviewDelegate`: PASS.
  - Reviewable files: 5. Excluded files: 8. Total files: 13. Coverage rate: 100%. 0 critical/high findings remaining.
- **Critical Findings Resolved:**
  1. **OTP reuse and replay:** `AuthService.verifyOtp` consumes the OTP in Redis before querying users. This eliminates replay and user enumeration.
  2. **Rate limit bypass mitigation:** Added `@RateLimited.Rule(policy = "otp-verify-phone")` to `verifyOtp`. Rate limits apply before any handler runs.
  3. **Stale JWT and privilege escalation:** Added `AccountStateProvider` port and `DefaultAccountStateProvider`. `JwtService.authenticate` verifies user active status, role, and dealership against Redis and PostgreSQL.
  4. **Redis session revocation:** `JwtService` writes `session:{jti}` to Redis on issue and deletes it on logout or revoke.
- **High Findings Resolved:**
  5. **Flyway migration ordering:** Replaced `V2` seed file with repeatable migration `R__seed_dealer_and_auth.sql`.
  6. **Redis OTP key scoping:** Updated `OtpStorage` and `RedisOtpStorage` with tenant-scoped keys `otp:{tenant}:{type}:{phone}`.
  7. **Virtual thread pinning audit:** Added an ArchUnit test in `ArchitectureTest` asserting no synchronized methods exist.
  8. **RFC 7807 consistency:** `IdempotencyFilter` writes `application/problem+json` and preserves cached `Content-Type`.
- **Verification Gates Passed:**
  - Spotless and Checkstyle: clean pass.
  - PMD: clean pass.
  - Full build: `./gradlew clean build` passed (106 tests, 100% service coverage).
  - Bruno API tests: 50 requests passed (61/61 tests green).
  - Playwright E2E tests: 20/20 tests passed.
  - OpenSpec validation: `openspec validate phase-1-bootstrap-otp-auth --strict` valid.

## 6. PR #1 Follow-up Review Findings (Kilo Code & Qodo PR Inline Reviews)

- **Findings Resolved:**
  1. **Qodo High (`JwtService.java`):** Deleted the 5-minute positive cache in `validateAccountState`. The system queries `AccountStateProvider` on each request. Privilege changes take effect immediately.
  2. **Qodo Medium (`JwtService.java`):** Deleted caching of negative provider results. Added `activateUser` and deleted `user:active` on `invalidateUserState`. Reactivated accounts log in without delay.
  3. **Compose & JWT Fail-Fast Gate (`docker-compose.yml` & `JwtProperties.java`):** Configured `JWT_SECRET: ${JWT_SECRET:-}` in `docker-compose.yml` so `docker compose up -d postgres redis` parses on developer machines without `JWT_SECRET`. Added strict validation in `JwtProperties` asserting secrets contain at least 32 characters, preventing production runs with missing keys.
- **Verification Gates Passed:**
  - Spotless and Checkstyle: clean pass.
  - PMD: clean pass.
  - Unit tests: `./gradlew :service-scheduler-server:test` passed.
  - JaCoCo coverage: 100% on `@Service` classes.
  - Delegate review: `verifyCodeReviewDelegate` passed.

