# Local Code Review Record — `phase-1-bootstrap-otp-auth`

- **Change:** `phase-1-bootstrap-otp-auth`
- **Branch:** `feature/phase-1-bootstrap-otp-auth`
- **Review Tool:** `open-code-review-delegate` (`ocr delegate preview --format json` & `ocr delegate rule --format json`)
- **Date:** 2026-10-26

## 1. Architectural Scope & Refactoring Findings

### Findings Addressed
1. **Eliminate Role Duplication Between `users` and `employees` (`users.type` vs `employees.role`) (Critical):**
   - **Finding:** Storing `role` (`CUSTOMER`, `ADMIN`, `MANAGER`, `TECHNICIAN`) on `users` AND `role` (`ADMIN`, `MANAGER`, `TECHNICIAN`) on `employees` duplicated staff role state across two tables.
   - **Fix:** Replaced `users.role` with `users.type VARCHAR(20) NOT NULL CHECK (type IN ('CUSTOMER','EMPLOYEE'))` in `V1__platform_and_auth_schema.sql`, `V2__seed_dealer_and_auth.sql`, and `User.java`. When `users.type = 'CUSTOMER'`, the caller's role is `CUSTOMER` (`dealershipId = null`). When `users.type = 'EMPLOYEE'`, `AuthService` resolves the employee's authorization `role` (`ADMIN`, `MANAGER`, `TECHNICIAN`) and `dealershipId` (`null` for global `ADMIN`, local `dealership_id` for `MANAGER` and `TECHNICIAN`) from `employees`.
2. **Centralized `profiles` Abstraction Across All Accounts (Critical):**
   - **Finding:** Personal profile fields (`first_name`, `last_name`, `locale`) were previously split across `customers` and `technicians`.
   - **Fix:** Centralized personal profile attributes into `profiles` (`CustomerProfile` entity mapped to `profiles`) for all accounts (`CUSTOMER` and `EMPLOYEE`). Seeded 4 `profiles` rows in `V2__seed_dealer_and_auth.sql`. `AuthService.me` and `AuthService.patchMe` read and update personal names uniformly through `CustomerProfileProvider`.
3. **Employee Hierarchy & Technician Career/Skill Taxonomy (High):**
   - **Finding:** Staff accounts (`ADMIN`, `MANAGER`, `TECHNICIAN`) require an abstract employee model with `employee_id`, `role`, and `dealership_id` scope (`ADMIN` global `NULL`, `MANAGER`/`TECHNICIAN` local `NOT NULL`), while technicians require a career `level` (`technicians.level`) and multi-skill proficiency rows (`technician_skills.skill` + `technician_skills.level`).
   - **Fix:** Added `employees`, `technicians`, and `technician_skills` tables in `V1` and seeded `ADM-0001`, `MGR-0001`, and `TEC-0001` (`SENIOR` career level, `ENGINE` at `SENIOR`, `BRAKES` at `TECHNICIAN`) in `V2`. Implemented `Employee`, `Technician`, `TechnicianSkill`, `SkillLevel`, `TechnicianSkillCode`, and `TechnicianService` in `dev.hata.scheduler.resource`.
4. **Swagger UI & OpenAPI 3.1 on Spring Boot 4.1.1 Default Profile (Critical):**
   - **Finding:** `springdoc-openapi-starter-webmvc-ui:2.8.5` targeted Spring Boot 3.x auto-configuration classes and did not initialize on Spring Boot 4.1.1, causing `/swagger-ui.html` and `/v3/api-docs` to return 404.
   - **Fix:** Upgraded to `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2` (SpringDoc v3 for Spring Boot 4) and enabled `springdoc.show-actuator: true` in `application.yml`. Added E2E verification in `platform-infrastructure.spec.ts` proving `/swagger-ui/index.html` and `/v3/api-docs` return HTTP 200 and expose all endpoints.

## 2. Verification Proofs

- **OCR Delegate Review Summary:**
  - `total_files`: 85, `reviewed_files`: 46, `skipped_files`: 39, `coverage_rate`: 100%.
  - Critical/High findings remaining: 0.
- **Formatting & Static Analysis Gates:**
  - Command: `./gradlew :service-scheduler-server:spotlessCheck :service-scheduler-server:checkstyleMain :service-scheduler-server:checkstyleTest :service-scheduler-server:pmdMain :service-scheduler-server:pmdTest`
  - Result: BUILD SUCCESSFUL (0 violations).
- **Full Build & Coverage Gate:**
  - Command: `./gradlew clean build`
  - Result: BUILD SUCCESSFUL (152 unit + Testcontainers integration tests executed, 0 failures).
  - Coverage: 100% line coverage on every `*Service` class (`AuthService` 145/145, `TechnicianService` 22/22, `DealershipService` 25/25, `CustomerService` 13/13, `OtpService` 15/15, `RateLimitService` 21/21, `IdempotencyService` 7/7, `JwtService` 42/42), 94.3% overall line coverage.
- **Bruno Integration API Suite:**
  - Command: `npx --yes @usebruno/cli@2.14.1 run --env local --disable-cookies`
  - Result: 49/49 requests passed, 60/60 tests green.
- **Playwright E2E Suite:**
  - Command: `npx playwright test` in `service-scheduler-ui`
  - Result: 20/20 tests passed across `platform-infrastructure.spec.ts` (including Swagger UI + OpenAPI 3.1 verification), `otp-login.spec.ts`, and `customer-registration.spec.ts`.
- **OpenSpec Strict Validation:**
  - Command: `openspec validate phase-1-bootstrap-otp-auth --strict`
  - Result: `Change 'phase-1-bootstrap-otp-auth' is valid`.
- **Archify Diagram Validation:**
  - Command: `node .agents/skills/archify/archify/bin/archify.mjs finalize erd docs/diagrams/src/system/system-data-model.erd.json docs/diagrams/system/system-data-model.html --quality showcase --json`
  - Result: `status: pass` (`validate: pass`, `deliver: pass`, `check: pass`, `browser-check: pass`).
