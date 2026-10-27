# Code Review Record — Open Code Review (OCR) Delegate

## 1. Review Metadata

| Field | Value |
|---|---|
| Change Slug | `workshop-metadata-and-slot-stub` |
| Feature Branch | `feature/workshop-metadata-and-slot-stub` |
| Mode | `workspace` delegate |
| Tool | Alibaba Open Code Review CLI (`ocr delegate`) |
| Total Files | 69 |
| Reviewed Files | 45 |
| Skipped Files | 24 (tests, build artifacts, `.bru` files, lockfiles) |
| Review Coverage Rate | 65.2% |
| Review Date | 2026-10-27 |
| Review Verdict | **PASS** (0 Critical, 0 High, 0 Medium, 0 Low) |

---

## 2. Review Rule Groupings & Standards

Applied rules based on `ocr delegate rule --format json service-scheduler-server/src/main/java/dev/hata/scheduler/`:
1. **Correctness**: Validates boundary conditions, null safety, exception handling, and concurrent access.
2. **Security**: Verifies absence of SQL injection, cross-site leaks, parameter tampering, and role authorization.
3. **Performance**: Inspects N+1 query prevention, indexing, Redis caching, and resource release.
4. **Maintainability**: Enforces domain-driven design, package independence, typed DTOs, and ASD-STE100 documentation.
5. **Test Coverage**: Verifies 100% coverage on domain `@Service` classes and integration contract testing.

---

## 3. Inspected File Groups & Findings

### 3.1 Database Schema & Seed Migrations
- `V2__workshop_metadata_and_appointments_schema.sql`:
  - `btree_gist` extension enabled for PostgreSQL 16 exclusion constraints.
  - `vehicle_categories` schema correctly scoped to dealership with `UNIQUE (dealership_id, brand, model, type)` and index.
  - `workshop_bays` table with PostgreSQL `TEXT[]` and `JSONB` equipment tags.
  - `service_types` table with `estimated_minutes` and `allowed_bay_types` array.
  - `service_type_costs` composite primary key `(service_type_id, vehicle_category_id)` with `ON DELETE CASCADE`.
  - `appointments` table replaces `vehicle_id` with `vehicle_category_id` while retaining `vehicle_vin` with `EXCLUDE USING gist` concurrency protection.
  - *Finding*: No critical, high, or medium issues found.

- `R__seed_metadata_fixtures.sql` & `R__seed_schedule_and_appointments.sql`:
  - Repeatable migrations with `ON CONFLICT DO NOTHING`.
  - Seeds 4 vehicle categories (`SEDAN`, `SUV`, `TRUCK`, `EV`), 3 workshop bays, 3 service types, technician requirements, and pricing matrix.
  - Seeds `CONFIRMED` appointments for Days +1, +2, +3 relative to `CURRENT_DATE`.
  - *Finding*: No issues found.

### 3.2 Decoupling Providers & Common Architecture
- `DealershipValidationProvider.java`:
  - Package: `dev.hata.scheduler.common.provider`.
  - `@FunctionalInterface` cross-feature decoupling port preventing `vehicle` and `scheduling` from directly importing `dealership` package.
  - Satisfies ArchUnit package independence rules and PMD clean code rules.
  - *Finding*: No issues found.

- `JacksonConfig.java`:
  - Package: `dev.hata.scheduler.common.config`.
  - Provides `@ConditionalOnMissingBean` `ObjectMapper` registered with `JavaTimeModule` for ISO-8601 serialisation.
  - *Finding*: No issues found.

### 3.3 Domain Models, Repositories, Services, and Controllers
- `DealershipService.java` & `DealershipController.java`:
  - Implements `DealershipValidationProvider`.
  - `findAllActive()` queries only active dealerships.
  - `GET /api/v1/dealerships` documented with SpringDoc annotations.
  - *Finding*: No issues found.

- `VehicleCategoryService.java` & `VehicleCategoryController.java`:
  - Scoped by `dealershipId`. Validates dealership presence through `DealershipValidationProvider`.
  - `VehicleCategory` entity correctly maps columns `brand`, `model`, and `type`. Does not expose or contain VIN.
  - `GET /api/v1/dealerships/{id}/vehicle-categories` returns RFC 7807 problem details on 404.
  - *Finding*: No issues found.

- `ServiceTypeService.java` & `ServiceTypeController.java`:
  - Scoped by `dealershipId`. Validates dealership existence.
  - Maps `estimated_minutes` accurately to DB column.
  - `GET /api/v1/dealerships/{id}/service-types` documented with SpringDoc annotations.
  - *Finding*: No issues found.

- `PricingService.java` & `PricingController.java`:
  - Calculates itemized and total cost from `service_type_costs`.
  - Validates dealership, vehicle category, and requested service types.
  - Rejects empty service type list or missing cost mapping with RFC 7807 problem details (400 Bad Request).
  - *Finding*: No issues found.

- `SlotAvailabilityService.java` & `SlotAvailabilityController.java`:
  - Reads Redis key `slot:grid:{dealershipId}:{serviceTypeId}:{date}`.
  - Gracefully handles cache misses or unparsed JSON with fallback to empty list `[]`.
  - Customer-facing response strictly exposes `startTime`, `endTime`, and `available` boolean without leaking internal workshop capacity or technician identities.
  - *Finding*: No issues found.

- `WorkshopSlotFixturePopulator.java`:
  - Runs on `@Profile({"dev", "test"})` via `ApplicationRunner`.
  - Populates slot grids for Days +1, +2, +3 reflecting booked and available windows.
  - *Finding*: No issues found.

### 3.4 Architecture Diagrams & Documentation
- `system-data-model.erd.json`:
  - Replaces `vehicles` entity with `vehicle_categories`.
  - Replaces `vehicle_id` foreign keys with `vehicle_category_id` in `appointments` and `service_type_costs`.
  - Validated and finalized via Archify CLI with 0 errors and 0 warnings.
- `metadata-management.sequence.json`:
  - Updates vehicle catalogue flow to vehicle category registration flow.
  - Validated and finalized via Archify CLI with 0 errors and 0 warnings.
- `docs/SYSTEM_DESIGN.md`:
  - §4.2.6 updated to `vehicle_categories` (`brand`, `model`, `type`).
  - All occurrences of `vehicle_id` updated to `vehicle_category_id`.
  - Passes ASD-STE100 linter rules.

### 3.5 Frontend Mocking & Playwright E2E Suite
- `service-scheduler-ui/tests/unit/mock/handlers.ts`:
  - Implements MSW mock handlers for all 5 metadata endpoints with typed response structures.
- `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts`:
  - Validates active dealership retrieval, vehicle category browsing without VIN, service type listings, and itemized pricing estimate.
- `service-scheduler-ui/tests/e2e/slot-availability-cache.spec.ts`:
  - Validates cached slot grid intervals, booked slot indicators, cache miss fallback, and query parameter validation.
- `service-scheduler-ui/tests/e2e/workshop-seed-data.spec.ts`:
  - Validates seeded vehicle categories across all four body types (`SEDAN`, `SUV`, `TRUCK`, `EV`), service types, confirmed slot cache across Days +1 to +3, and OpenAPI 3.1 documentation.

### 3.6 PR Code Review Findings & Resolution (Qodo)
- **Finding (Qodo Review)**: Use the workshop timezone when generating fixture dates.
  - *Context*: `WorkshopSlotFixturePopulator.run()` used `LocalDate.now()` (system default timezone), and `R__seed_schedule_and_appointments.sql` used `CURRENT_DATE` (PostgreSQL server session timezone). Around midnight, these independent dates could diverge from the workshop's local operating day (`Asia/Ho_Chi_Minh`).
  - *Fix Applied*:
    - `WorkshopSlotFixturePopulator.java`: Changed `populate(LocalDate.now())` to `populate(LocalDate.now(TIMEZONE))`.
    - `AuthFixtureReset.java`: Changed `populate(LocalDate.now())` to `populate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))`.
    - `R__seed_schedule_and_appointments.sql`: Changed `CURRENT_DATE` to `((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)` across all appointment and job seed records.
    - `slots-available-cached.bru`: Formatted `TARGET_DATE` using `Asia/Ho_Chi_Minh` timezone.
    - Playwright test suites: Updated `formatIsoDate()` helper to format target dates against `Asia/Ho_Chi_Minh`.
  - *Status*: RESOLVED and verified across all test suites.

### 3.7 PR Code Review Findings & Resolution (Kilo Code)
- **Finding 1 — Category Dealership Validation**: `PricingService` permitted estimating costs using another dealer's vehicle category.
  - *Fix*: Created `VehicleCategoryValidationProvider` cross-feature decoupling port in `common.provider`. Implemented by `VehicleCategoryService`. Injected into `PricingService` to reject non-existent or foreign dealership categories with HTTP 400.
- **Finding 2 — Pricing Deduplication & Batch Query**: Duplicate service IDs charged multiple times; one DB query ran per service ID.
  - *Fix*: Deduplicated `serviceTypeIds.stream().distinct().toList()` in `PricingService`. Batch queried costs via `costRepository.findByVehicleCategoryIdAndServiceTypeIdIn()`.
- **Finding 3 — Redis Outage Handling**: `SlotAvailabilityService` threw uncontrolled exception when Redis was down.
  - *Fix*: Wrapped Redis lookup in try/catch block. Gracefully logs a warning and returns empty list `Collections.emptyList()` on failure.
- **Finding 4 — Development Startup Resilience**: Redis outages prevented application startup in `WorkshopSlotFixturePopulator`.
  - *Fix*: Wrapped `populate()` in `run()` and `clearDemoKeys()` in try/catch blocks with warning logs so the server starts even during Redis connection issues.
- **Finding 5 — Brake Demo Slot Duration**: Demo brake slots were 90 minutes instead of the 120-minute estimated duration in `service_types`.
  - *Fix*: Updated `populateBrakeSlots()` duration from 90 to 120 minutes.
- **Finding 6 — Missing Allowed Bay Types in API**: `ServiceTypeResponse` omitted `allowedBayTypes`.
  - *Fix*: Added `@JdbcTypeCode(SqlTypes.ARRAY)` mapping for `allowed_bay_types` to `ServiceType` entity and exposed `List<String> allowedBayTypes` in `ServiceTypeResponse`.
- **Finding 7 — Date Timezone Preservation**: Jackson serialized times in UTC rather than dealership offset.
  - *Fix*: Configured `WRITE_DATES_AS_TIMESTAMPS = false` and `ADJUST_DATES_TO_CONTEXT_TIME_ZONE = false` on `ObjectMapper` in `JacksonConfig.java`.
- **Finding 8 — Node 20 LTS Tooling Compatibility**: `msw@3.0.3` dropped support for Node 20 LTS.
  - *Fix*: Downgraded MSW to `^2.7.0` in `service-scheduler-ui/package.json` for full Node 20 LTS compatibility.
- **Finding 9 — Pricing Mock Query Param**: Mock handler accepted `vehicleCategoryId` query parameter unsupported by the real pricing endpoint.
  - *Fix*: Updated `service-scheduler-ui/tests/unit/mock/handlers.ts` to read only `categoryId` and return 400 Bad Request when absent.
- **Finding 10 — Startup Calendar Date Coordination**: Appointment and Redis fixture runners sampled the calendar date independently, causing relative-day fixtures to disagree across midnight rollovers.
  - *Fix*: Introduced `WorkshopFixtureOrchestrator` to capture a single `LocalDate.now(TIMEZONE)` date and pass it to both `AppointmentFixturePopulator` and `WorkshopSlotFixturePopulator`.
- **Finding 11 — Transactional Fixture Upserts**: Three fixture upserts committed independently, risking partial schedule state if an exclusion constraint failed.
  - *Fix*: Wrapped all three upserts inside a single transaction via `TransactionTemplate.executeWithoutResult()` in `AppointmentFixturePopulator.populate()`.
- **Finding 12 — Fixture Orchestrator Exception Logging**: `WorkshopFixtureOrchestrator` caught slot population exceptions and logged only `ex.getMessage()`, omitting the exception cause and stack trace.
  - *Fix*: Passed `ex` as the trailing argument to `log.warn()` in `WorkshopFixtureOrchestrator.java` to record the full trace.
- **Finding 13 — TypeMismatch Parameter Identification**: `handleTypeMismatch` identified failed parameters using `getPropertyName()`, which returned null for method arguments.
  - *Fix*: Extracted `mismatch.getName()` from `MethodArgumentTypeMismatchException` in `GlobalExceptionHandler.java`.
- **Finding 14 — Mock Whitespace-Padded UUID Normalization**: The pricing mock rejected whitespace-padded UUIDs accepted by Spring's parameter converters.
  - *Fix*: Added `.trim()` normalization before validating UUID format and mapping items in `handlers.ts`.
- *Status*: All 14 findings RESOLVED and covered by unit and E2E tests.

---

## 4. Verification Proofs

The following quality gates and test suites confirm all changes:

1. **OCR Delegate Preview**:
   ```sh
   ocr delegate preview --format json
   # total_files: 69, reviewable_count: 45, excluded_count: 24, coverage_rate: 65.2%
   ```
2. **Gradle Code Review Gate**:
   ```sh
   ./gradlew :service-scheduler-server:verifyCodeReviewDelegate
   # BUILD SUCCESSFUL - review.md validated
   ```
3. **Format & Lint Quality Gates**:
   ```sh
   ./gradlew :service-scheduler-server:spotlessApply
   ./gradlew :service-scheduler-server:checkstyleMain
   ./gradlew :service-scheduler-server:pmdMain
   # 0 style or PMD violations
   ```
4. **Backend Unit, Integration, & Architecture Tests**:
   ```sh
   ./gradlew test jacocoTestReport
   # 142 tests completed, 0 failed, 0 skipped
   # ArchUnit package independence rules: PASSED
   ```
5. **JaCoCo Coverage Gate**:
   - `DealershipService`: **100.0%**
   - `VehicleCategoryService`: **100.0%**
   - `ServiceTypeService`: **100.0%**
   - `PricingService`: **100.0%**
   - `SlotAvailabilityService`: **100.0%**
   - Overall Instruction Coverage: **92.6%** (gate threshold: 80%)
6. **Bruno API Contract Suite**:
   ```sh
   npx --yes @usebruno/cli@2.14.1 run src/test/bruno --env local --disable-cookies
   # 56 requests passed, 0 failed, 68 tests passed
   ```
7. **Playwright E2E Suite**:
   ```sh
   npm test
   # 34 tests passed, 0 failed (including customer-registration, otp-login, platform-infrastructure, workshop-metadata, slot-availability-cache, workshop-seed-data)
   ```
8. **Archify Validation**:
   - `docs/diagrams/system/system-data-model.html`: Finalized (quality: showcase, browser-check: pass).
   - `docs/diagrams/system/metadata-management.html`: Finalized (quality: showcase, browser-check: pass).
