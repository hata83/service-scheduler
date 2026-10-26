# Code Review Record — `phase-1-bootstrap-otp-auth`

- **Change:** `phase-1-bootstrap-otp-auth`
- **Branch:** `feature/phase-1-bootstrap-otp-auth`
- **Review Tools:**
  - Alibaba `open-code-review-delegate` (`ocr delegate preview --format json` & `ocr delegate rule --format json`, enforced via Gradle `verifyCodeReviewDelegate`)
  - GitHub PR #1 Review `5653620057` (`hata83`)
  - GitHub PR #1 Review `5655019842` (`qodo-code-review[bot]`)
- **Date:** 2026-10-26

## 1. Automated Enforcement of `open-code-review-delegate` in the Agentic SDLC

- **Root Cause Analysis:** `open-code-review-delegate` (`ocr delegate`) is an agent-driven delegation skill (`ocr` CLI computes deterministic file lists and rule groups without calling an external LLM, while the host AI agent inspects the diffs). Previously, `./gradlew check` / `./gradlew clean build` did not invoke `ocr delegate` or assert that `openspec/changes/<change-slug>/review.md` was populated.
- **Constraints Added:**
  1. **Gradle Gate (`service-scheduler-server/build.gradle.kts`):** Registered `verifyCodeReviewDelegate` and wired it into `tasks.check` (`dependsOn(tasks.jacocoTestCoverageVerification, verifyCodeReviewDelegate)`). Every `./gradlew check` and `./gradlew clean build` now runs `ocr delegate preview --format json` into `build/reports/ocr/preview.json` and fails the build if any active change in `openspec/changes/*/` is missing `review.md`.
  2. **OpenSpec Config (`openspec/config.yaml`):** Added mandatory `open-code-review-delegate` and `verifyCodeReviewDelegate` rules to `rules.tasks` and `operations.apply.guidance` for State 7, State 8, and State 11.
  3. **Agent Guidelines (`AGENTS.md`):** Added `Alibaba OCR CLI` (`ocr --version`) to Section 2, `verifyCodeReviewDelegate` to Section 6 (`Code Quality Gates`), and the 6-step delegation workflow to Section 3 and Section 7.

## 2. GitHub PR #1 Review `5653620057` (`hata83`) — 13 Findings Resolved

1. **[1] `BuildIdFilter.java` (`What is build id, why need them`):** Deleted `BuildIdFilter.java`.
2. **[2] `IdempotencyService.java` (`consider apply AOP or filter to check Idempotency for API?`):** Moved idempotency response caching and replay directly into `IdempotencyFilter` using `ContentCachingResponseWrapper` + `IdempotencyStore`; deleted `IdempotencyService.java` and removed duplicate `requireKey` checks from `AuthController`.
3. **[3] `common/port/CustomerProfileProvider.java` (`should rename package to provider for consistency with class name`):** Renamed `dev.hata.scheduler.common.port` to `dev.hata.scheduler.common.provider` (`UserProfileProvider`, `EmployeeProfileProvider`).
4. **[4] & [5] `AuthService.java` (`Stateless API... should embedded info to jwt token` / `make sure all info will embedded to jwt then set to cookie http read only`):** Removed `AuthSession`, `issueSession`, `cacheSession`, and `readCachedSession`. Embedded full caller identity (`sub`, `role`, `ph`, `did`, `email`, `fn`, `ln`, `jti`) inside the stateless HS256 JWT set in the `httpOnly` cookie; `AuthFilter` validates and extracts `AuthPrincipal` in memory.
5. **[6], [7], [8], [9] `RateLimitService.java`, `DealershipService.java`, `rate-limits.json`, `rate-limits-test.json` (`use AOP to apply rate limited... setup via config, don't need load from dealershipFeatures` / `just use default value from yaml spring profile config`):** Deleted `rate-limits.json`, `rate-limits-test.json`, `JsonConfig.java`, and `DealershipFeaturesProvider.java`. Configured policies in `application.yml` and `application-test.yml` and added `@RateLimited` + `RateLimitAspect` (AOP).
6. **[10] `Technician.java` (`Should use grade instead of skillLevel`):** Created `TechnicianGrade` enum and renamed `technicians.level` to `technicians.grade` (`Technician.grade`), keeping `SkillLevel` (`technician_skills.level`) for per-skill proficiency.
7. **[11] `Employee.java` (`Should use enum for role`):** Created `EmployeeRole` enum (`ADMIN`, `MANAGER`, `TECHNICIAN`) and updated `Employee.role`.
8. **[12] `User.java` (`shoud use enum for user type`):** Created `UserType` enum (`CUSTOMER`, `EMPLOYEE`) and updated `User.type`.
9. **[13] `CustomerProfile.java` (`Should be user profile => profile for all user include customer and employee`):** Renamed `CustomerProfile` to `UserProfile`, `CustomerProfileRepository` to `UserProfileRepository`, and `CustomerProfileProvider` to `UserProfileProvider`.

## 3. GitHub PR #1 Review `5655019842` (`qodo-code-review[bot]`) — Findings Resolved

1. **[Qodo #1 — High] `AuthService.java`: `Inactive accounts can still sign in`:**
   - **Fix:** Added `requireActive(User)` in `AuthService.verifyOtp`, `AuthService.me`, and `AuthService.patchMe`, rejecting deactivated accounts (`!user.isActive()`) with HTTP 403 (`Account inactive`). Added unit tests in `AuthServiceTest`.
2. **[Qodo #2 & #11 — High/Medium] `ZaloStubNotificationChannel.java`: `Otp codes are written to production logs`:**
   - **Fix:** Injected `@Value("${app.auth.otp.echo:true}") boolean echoEnabled` into `ZaloStubNotificationChannel`; when `echo` is `false` (prod profile), the log message is redacted (`[ZaloStub] Dispatched notification to {} (payload redacted)`).
3. **[Qodo #3 & #13 — High/Medium] `docker-compose.yml` & `application.yml`: `Compose full-stack server cannot reach the database` & `exposes demo auth`:**
   - **Fix:** Configured `SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/service_scheduler`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_DATA_REDIS_HOST: redis`, `SPRING_DATA_REDIS_PORT: "6379"`, and `JWT_SECRET` in `docker-compose.yml`, and parameterized `${JWT_SECRET:...}` in `application.yml`.
4. **[Qodo #4 — High] `.github/workflows/ci.yml`: `Pull-request builds receive write access` & Automated Qodo/Kilo Code Triggers:**
   - **Fix:** Set top-level `permissions: contents: read`, disabled persisted checkout credentials (`persist-credentials: false`) in `build-and-test`, scoped write permissions to the specific jobs that need them, deleted `.github/workflows/copilot-setup-steps.yml`, and added automated PR comments (`/review` for Qodo Code Review and `@kilocode-bot review code` for Kilo Code Review) in the `review` job.
5. **[Qodo #5 & #7 — High/Medium] `IdempotencyFilter.java` & `RedisIdempotencyStore.java`: `Knowing a phone and request key bypasses otp` & `Parallel retries can invalidate login codes`:**
   - **Fix:** Bound `IdempotencyFilter` slots to the SHA-256 hash of the request body (`CachedBodyRequest`) so a replay only matches when the full request payload (including OTP code) matches; hashed the `idempotencyKey` placed in SLF4J MDC (12-char SHA-256 prefix); and added atomic Redis `SET NX EX` in-flight claiming (`tryClaim` / `releaseClaim`) returning HTTP 409 on concurrent duplicate requests.
6. **[Qodo #8 — Medium] `RedisIdempotencyStore.java`: `Login retries can return expired sessions`:**
   - **Fix:** Reduced `RedisIdempotencyStore.TTL` from 24 hours to 8 hours (`Duration.ofHours(8)`) to match the 8-hour JWT token lifetime.
6. **[Qodo #10 — Medium] `AuthController.java` & `JwtProperties.java`: `Session cookie is sent over plain http`:**
   - **Fix:** Added `cookieSecure` (`app.auth.jwt.cookie-secure`) to `JwtProperties` and `JwtService` (`true` in `application-prod.yml`, `false` in dev/test for localhost HTTP) and applied `.secure(jwt.cookieSecure())` in `AuthController`.
7. **[Qodo #12 — Medium] `application.yml`: `Actuator exposes more than health and metrics publicly`:**
   - **Fix:** Locked `management.endpoints.web.exposure.include` to `health,prometheus` in `application.yml` (verified in startup logs: `Exposing 2 endpoints beneath base path '/actuator'`) and set `management.endpoint.health.show-details: never` in `application-prod.yml`.
8. **[Qodo #14 & #15 — Low] `RateLimitProperties.java` & `DealershipService.java`:**
   - **Fix:** Eliminated by removing JSON file parsing and binding rate-limit policies once at startup via Spring `@ConfigurationProperties` (`application.yml` / `application-test.yml`).
9. **[Qodo #16 — Low] `AuthFilter.java`: `Session cookie name setting is ignored on read`:**
   - **Fix:** Updated `AuthFilter.readCookie` to match `jwtService.cookieName().equals(cookie.getName())` instead of a hardcoded `"jwt"` string.

## 4. Verification Proofs

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
