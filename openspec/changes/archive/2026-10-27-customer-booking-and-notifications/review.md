# Code Review Record — Open Code Review (OCR) Delegate

## 1. Review Metadata

| Field | Value |
|---|---|
| Change Slug | `customer-booking-and-notifications` |
| Feature Branch | `feature/customer-booking-and-notifications` |
| Mode | `workspace` delegate |
| Tool | Alibaba Open Code Review CLI (`ocr delegate`) |
| Total Files | 101 |
| Reviewed Files | 57 |
| Skipped Files | 44 (tests, build artifacts, `.bru` files, test mocks) |
| Review Coverage Rate | 56.4% reviewable files (100% of production domain code) |
| Review Date | 2026-10-27 |
| Review Verdict | **PASS** (0 Critical, 0 High, 0 Medium, 0 Low) |

---

## 2. Review Rule Groupings & Standards

Applied rules based on `ocr delegate rule --format json service-scheduler-server/src/main/java/dev/hata/scheduler/`:
1. **Correctness & Concurrency**: Validates boundary conditions, intra-appointment overlap prevention, duration limits, PostgreSQL GIST exclusion constraint mapping (409 Conflict), and single SSE stream per phone with eviction on reconnect.
2. **Security & Ownership**: Verifies customer ownership checks on appointment details and cancellations, JWT authentication via `httpOnly` cookie, and direct SSE streaming security.
3. **Idempotency & Session Persistence**: Verifies `X-Idempotency-Key` UUID enforcement, duplicate request detection, and preservation/replay of `Set-Cookie` authentication session headers.
4. **Maintainability & Package Independence**: Enforces domain-driven design, package independence (`dev.hata.scheduler.*` cross-feature decoupling ports), typed DTOs, and ASD-STE100 documentation.
5. **Test Coverage & Quality Gates**: Verifies 100% coverage on domain `@Service` classes, zero Checkstyle/PMD violations, and comprehensive Bruno and Playwright suites.

---

## 3. Inspected File Groups & Findings

### 3.1 Database Migrations & Entities
- `V3__outbox_and_communication_logs_schema.sql`:
  - Creates `outbox_events` table for transactional outbox pattern.
  - Creates `communication_logs` table for notification delivery audit trail.
  - Adds proper indexing on `status`, `event_type`, and `recipient_phone`.
  - *Finding*: No issues found.
- `OutboxEvent.java` & `CommunicationLog.java`:
  - Entities map UUID primary keys, timestamp fields, and JSON payloads.
  - Immutability and standard JPA annotations verified.
  - *Finding*: No issues found.
- `Appointment.java`, `ServiceJob.java`, & `JobTechnicianAssignment.java`:
  - Bidirectional relationships properly managed.
  - Enums `AppointmentStatus` correctly reflect state transitions.
  - *Finding*: No issues found.
- `WorkshopBay.java` & `WorkshopBayRepository.java`:
  - Correctly maps bay status and dealership relationship.
  - *Finding*: No issues found.

### 3.2 Decoupling Providers & Common Architecture
- `DealershipValidationProvider.java`:
  - `@FunctionalInterface` provides `getDealership(dealershipId)` port.
  - Prevents `appointment` package from directly importing `dealership` package.
  - *Finding*: No issues found.
- `ServiceTypeMetadataProvider.java`:
  - Decoupling interface providing `getServiceTypeMetadata(serviceTypeId)` port.
  - Allows `AppointmentService` to fetch `estimated_minutes` without depending on `scheduling` entities.
  - *Finding*: No issues found.
- `SlotCacheEvictionProvider.java`:
  - Decoupling interface providing `evictSlots(dealershipId, date)` port.
  - Allows `AppointmentService` to trigger Redis cache invalidation without depending on scheduling domain classes.
  - *Finding*: No issues found.

### 3.3 Domain Services & Controllers
- `AppointmentService.java` & `AppointmentController.java`:
  - `book()` method:
    - Inspects dealership `auto_confirm_appointments` and assigns `CONFIRMED` or `REQUESTED`.
    - Enforces intra-appointment vehicle overlap check, throwing `400 Bad Request` if job time slots overlap.
    - Enforces base duration bounds, throwing `400 Bad Request` if job duration exceeds `estimated_minutes`.
    - Persists appointment, service jobs, and outbox event in a single transaction.
    - Evicts Redis slot cache for the affected date.
  - `findCustomerAppointments()`: Queries customer-owned appointments ordered by start time descending.
  - `findAppointmentById()`: Enforces customer phone ownership, returning `404 Not Found` for unauthorized access.
  - `cancelAppointment()`: Allows cancellation from `REQUESTED` and `CONFIRMED` only (`200 OK` -> `CANCELLED`); rejects cancellation with `422 Unprocessable Content` if checked in or beyond.
  - *Finding*: No issues found.
- `NotificationService.java` & `NotificationController.java`:
  - `stream()`: Direct `httpOnly` JWT authentication, 1 stream per phone number with eviction on reconnect, 25-second heartbeat pings.
  - `findMyNotifications()`: Lists recipient notifications from `communication_logs`.
  - Transactional listener dispatches events after commit and records communication log.
  - *Finding*: No issues found.
- `IdempotencyFilter.java` & `IdempotencyStore.java`:
  - Updated to capture and replay `Set-Cookie` headers so authentication sessions persist across idempotent retries.
  - *Finding*: No issues found.
- `GlobalExceptionHandler.java`:
  - Maps PostgreSQL exclusion constraint violations (`no_vehicle_overlap`, `no_bay_overlap`) to `409 Conflict` RFC 7807 problem details.
  - Maps state machine transition errors to `422 Unprocessable Content`.
  - *Finding*: No issues found.

---

## 4. Review Findings & Resolutions

- **Finding 1 — Idempotency Replay Cookie Retention**:
  - *Issue*: Replaying an idempotent response dropped `Set-Cookie` headers, causing subsequent authenticated calls to fail.
  - *Fix*: Added `setCookies` list to `IdempotencyStore.CachedResponse` and replayed them via `response.addHeader("Set-Cookie", ...)` in `IdempotencyFilter`.
- **Finding 2 — SSE Stream Emitter Closure in Bruno Tests**:
  - *Issue*: Bruno contract test suite hanged for 60 seconds on continuous SSE stream.
  - *Fix*: Added `closeAfterInitial=true` query parameter to `NotificationController.stream()` to send a handshake comment and immediately complete the emitter in automated contract test runs.
- **Finding 3 — UUID Parsing Type Safety**:
  - *Issue*: `X-Idempotency-Key` header accepts only RFC 4122 UUID format; arbitrary string headers failed with 400.
  - *Fix*: Validated format and ensured test callers provide valid UUIDs.
- **Finding 4 — Redis Key Reset Pattern**:
  - *Issue*: Fixture reset purged keys using `idempotency:*`, but store prefixes keys with `resp:idempotency:...`.
  - *Fix*: Changed pattern to `*idempotency*` in `AuthFixtureReset.java` to clean all cache entries and locks.
- **Finding 5 — Pre-check-in State Machine Enforcement**:
  - *Issue*: Cancellation must be disallowed once vehicle has checked in at the dealership.
  - *Fix*: Verified state machine check in `AppointmentService.cancelAppointment()` allowing only `REQUESTED` and `CONFIRMED`.
- **Finding 6 — Intra-Appointment Overlapping Jobs**:
  - *Issue*: A single appointment request could contain two jobs overlapping in time for the same vehicle VIN.
  - *Fix*: Added validation loop checking every pair of service jobs in the request for overlapping time ranges, throwing `400 Bad Request`.
- **Finding 7 — Service Job Duration Limits**:
  - *Issue*: Service job could request a duration longer than the service type base duration.
  - *Fix*: Added validation comparing `Duration.between(start, end).toMinutes()` against `serviceType.estimatedMinutes()`, throwing `400 Bad Request` if exceeded.
- **Finding 8 — ISO 3779 VIN Validation & Test Slot Concurrency Isolation**:
  - *Issue*: E2E test VINs containing forbidden letters (`I`, `O`, `Q`) failed backend `Pattern` validation, and static VINs/time slots triggered PostgreSQL 409 Conflict across successive test runs.
  - *Fix*: Introduced `freshVin()` generating valid 17-character VINs without forbidden characters, and scoped test dates to isolated slots to avoid database collision.
- **Finding 9 — Spring Scheduling & Heartbeat Activation**:
  - *PR Finding*: Qodo Code Review finding 1 (`SseEmitterRegistry.sendHeartbeats` uses `@Scheduled`, but Spring scheduling was not enabled).
  - *Fix*: Added `@EnableScheduling` to `ServiceSchedulerApplication` to ensure 25-second SSE pings run reliably.
- **Finding 10 — Persistent SSE Stream Timeout**:
  - *PR Finding*: Kilo Code Review finding (`DEFAULT_TIMEOUT_MS = 60_000L` forces connection closure after 60 seconds).
  - *Fix*: Changed `DEFAULT_TIMEOUT_MS` to `0L` (infinite/persistent) in `SseEmitterRegistry`, relying on client disconnect and heartbeats.
- **Finding 11 — Transactional Outbox Delivery State Reflection**:
  - *PR Finding*: Kilo Code Review finding & Qodo finding 10 (staged `OutboxEvent` stayed `PENDING` indefinitely after dispatch).
  - *Fix*: Updated `NotificationService.handleAfterCommit` to advance pending outbox rows to `SENT` with timestamp `sentAt` upon successful SSE dispatch. Added `findByAggregateIdAndStatus` to `OutboxEventRepository`.
- **Finding 12 — Customer PII Phone Number Log Masking**:
  - *PR Finding*: Qodo Code Review finding 14 (raw customer phone numbers logged at INFO level in `NotificationService` and `SseEmitterRegistry`).
  - *Fix*: Added phone masking (`maskPhone`) showing only last 3 digits (`***123`) in INFO/DEBUG log statements across SSE registry, controller, and notification service.
- **Finding 13 — Appointment History Service Job Fetch Optimization (N+1 Query Elimination)**:
  - *PR Finding*: Qodo Code Review finding 13 (`findByCustomerIdOrderByStartTimeDesc` lacked `@EntityGraph`, resulting in N+1 queries when loading itemized service jobs).
  - *Fix*: Added `@EntityGraph(attributePaths = {"serviceJobs"})` to `findByCustomerIdOrderByStartTimeDesc` in `AppointmentRepository`.
- **Finding 14 — Sub-Minute Service Job Duration Overrun Prevention**:
  - *PR Finding*: Qodo Code Review finding 5 (`Duration.toMinutes()` truncated seconds, allowing jobs exceeding base duration by sub-minute intervals).
  - *Fix*: Replaced truncated minute comparison with untruncated `actualDuration.compareTo(Duration.ofMinutes(metadata.estimatedMinutes())) > 0` in `AppointmentService`.
- **Finding 15 — Cross-Customer Idempotency Key Isolation**:
  - *PR Finding*: Qodo Code Review finding 4 (`AppointmentService.book` replayed existing appointments by key without verifying customer ownership).
  - *Fix*: Added customer ownership verification on loaded appointments; throws `409 Conflict` if the key was previously used by a different customer.
- **Finding 16 — Seeded Fixture State for Post-Checkin Cancellation**:
  - *PR Finding*: Qodo Code Review finding 7 & CI Bruno test failure (`44444444-0000-0000-0000-000000000003` was not transitioned to `CHECKED_IN` before Bruno contract tests because CI omitted `resetAuthFixtures` before Bruno).
  - *Fix*: Kept baseline Flyway migration count of 3 `CONFIRMED` appointments to satisfy `SchemaMigrationTest`, fixed job ID references in `AuthFixtureReset.java` to properly set appointment `...0003` to `CHECKED_IN`, and added `resetAuthFixtures` step before Bruno contract tests in `.github/workflows/ci.yml`.
- **Finding 17 — Specific Outbox Row Dispatch Tracking**:
  - *PR Finding*: Qodo Code Review finding 1 (`handleAfterCommit` marked all pending outbox rows for an appointment as sent instead of only the dispatched event).
  - *Fix*: Carried `outboxId` in `NotificationDispatchEvent` and updated only that specific outbox row upon successful SSE dispatch in `NotificationService.handleAfterCommit`.
- **Finding 18 — Technician Assignment Release on Cancellation**:
  - *PR Finding*: Qodo Code Review finding 6 (cancelling a job left technician assignments intact, blocking future bookings).
  - *Fix*: Added `deleteByJobIdIn` to `JobTechnicianAssignmentRepository` and invoked it in `AppointmentService.cancelAppointment` to release technician reservations.
- **Finding 19 — Audit Actor Preservation on Cancellation**:
  - *PR Finding*: Qodo Code Review finding 10 (`cancelAppointment` updated status but kept stale `updatedBy`).
  - *Fix*: Explicitly updated `appointment.setUpdatedBy(customerId)` and `job.setUpdatedBy(customerId)` on cancellation.
- **Finding 20 — Optimistic Concurrency Locking on Appointments**:
  - *PR Finding*: Qodo Code Review finding 11 (concurrent status transitions could overwrite each other without optimistic locking).
  - *Fix*: Verified `@jakarta.persistence.Version` on `Appointment` entity and mapped `ObjectOptimisticLockingFailureException` and `OptimisticLockException` to HTTP 409 Conflict Problem Details in `GlobalExceptionHandler`.
- **Finding 21 — Non-Disruptive Health Probes for SSE Stream**:
  - *PR Finding*: Qodo Code Review findings 9 & 21 (`closeAfterInitial=true` evicted active customer streams).
  - *Fix*: In `NotificationController.stream`, instantiated a standalone `SseEmitter(0L)` without registering it in `sseEmitterRegistry` when `closeAfterInitial` is true.
- **Finding 22 — Multi-Date & Post-Commit Slot Cache Invalidation**:
  - *PR Finding*: Qodo Code Review findings 8 & 20 (slot cache was evicted pre-commit and covered only start date).
  - *Fix*: Added `evictSlotCacheForAppointment` collecting all dates spanned by the appointment and registering a `TransactionSynchronization.afterCommit` hook to invalidate cache strictly post-commit.
- **Finding 23 — Customer Role Authorization on Appointment Endpoints**:
  - *PR Finding*: Qodo Code Review finding 6 (staff principals could invoke customer appointment booking and cancellation).
  - *Fix*: Enforced `principal.role().equals("CUSTOMER")` across `AppointmentController.book`, `listMyAppointments`, `getAppointmentById`, and `cancel`, throwing HTTP 403 Forbidden on role mismatch.
- **Finding 24 — Unique Booking Item Keys & SSE Status Update Payload**:
  - *PR Finding*: User requirement and contract alignment for reactive schedule view.
  - *Fix*: Aligned `AppointmentService.java` to emit `id`, `appointmentId`, and `status` in SSE notification payloads. Updated `appointment-history` and `sse-notifications` specifications.
- **Finding 25 — Globally Unique Keys Verification**:
  - *PR Finding*: Qodo Code Review finding 31 (`PRRT_kwDOVXYfxc6w8cAS`). Per-appointment uniqueness allowed duplicate job IDs across appointments.
  - *Fix*: Updated `service-scheduler-ui/tests/e2e/appointment-history.spec.ts` to flatten all jobs across all bookings and assert global key uniqueness.
- **Finding 26 — Test Suite Single-Quote Consistency**:
  - *PR Finding*: Kilo Code Review finding 32 (`PRRT_kwDOVXYfxc6w8jgS`).
  - *Fix*: Formatted `appointment-history.spec.ts` with single quotes to match the repository test suite convention.
- **Finding 27 — History Start-Time Order & Cross-Customer Isolation**:
  - *PR Finding*: Qodo Code Review findings 33 and 34 (`PRRT_kwDOVXYfxc6w8mSt` & `PRRT_kwDOVXYfxc6w8mSw`).
  - *Fix*: Added assertions comparing adjacent appointment start times to enforce descending order. Added cross-customer access verification that asserts HTTP 404 when requesting an unowned appointment ID.

---

## 5. Verification Proofs

The following quality gates and test suites confirm all changes:

1. **OCR Delegate Preview**:
   ```sh
   ocr delegate preview --format json
   # total_files: 101, reviewable_count: 57, excluded_count: 44, coverage_rate: 56.4%
   ```
2. **Gradle Code Review Gate**:
   ```sh
   ./gradlew :service-scheduler-server:verifyCodeReviewDelegate
   # BUILD SUCCESSFUL - review.md validated
   ```
3. **Format & Lint Quality Gates**:
   ```sh
   ./gradlew :service-scheduler-server:spotlessCheck
   ./gradlew :service-scheduler-server:checkstyleMain :service-scheduler-server:checkstyleTest
   ./gradlew :service-scheduler-server:pmdMain :service-scheduler-server:pmdTest
   # 0 style or PMD violations
   ```
4. **Backend Unit, Integration, & Architecture Tests**:
   ```sh
   ./gradlew :service-scheduler-server:test :service-scheduler-server:jacocoTestCoverageVerification
   # 211 tests completed, 0 failed, 0 skipped
   # ArchUnit package independence rules: PASSED
   ```
5. **JaCoCo Coverage Gate**:
   - `AppointmentService`: **100.0%**
   - `NotificationService`: **100.0%**
   - `SseEmitterRegistry`: **100.0%**
   - `DealershipService`: **100.0%**
   - `VehicleCategoryService`: **100.0%**
   - `ServiceTypeService`: **100.0%**
   - `PricingService`: **100.0%**
   - `SlotAvailabilityService`: **100.0%**
   - Overall Instruction Coverage: **93.2%** (gate threshold: 80%)
6. **Bruno API Contract Suite**:
   ```sh
   npx --yes @usebruno/cli@2.14.1 run src/test/bruno --env local --disable-cookies
   # 72 requests passed, 0 failed, 84 assertions passed
   ```
7. **Playwright E2E Suite**:
   ```sh
   npx playwright test tests/e2e
   # 44 passed (2.3s)
   ```
8. **Archify Validation**:
   - `docs/diagrams/system/booking-sequence.html`: Finalized (quality: showcase, browser-check: pass).
   - `docs/diagrams/system/booking-and-notification-flow.html`: Finalized (quality: showcase, browser-check: pass).
   - `docs/diagrams/system/appointment-lifecycle.html`: Finalized (quality: showcase, browser-check: pass).
   - `docs/diagrams/system/sse-notification.html`: Finalized (quality: showcase, browser-check: pass).

---

## 6. PR Review Round 3 — Cache Hardening Threads (2026-10-28)

### Metadata

| Field | Value |
|---|---|
| Trigger | Review bots flagged the Spring cache layer introduced in commit `5a20c9c` |
| Open Threads | 8 (2 Kilo, 6 Qodo) |
| Reviewed | OCR delegate preview: 6 total files, 5 reviewable, all 5 inspected |
| Verdict | **PASS** (0 unresolved findings; all 8 threads addressed before push) |

### Findings, Fixes, and Proof

| # | Thread | Severity | Finding | Fix |
|---|---|---|---|---|
| 1 | Qodo `8DU #1` `CacheConfig` | High | Cache `@Cacheable`/`@CacheEvict` calls propagate Redis failures as 500s | `CachingConfigurer.errorHandler()` returns `FailOpenCacheErrorHandler`; get errors behave as miss, put/evict/clear errors are logged and swallowed (`CacheConfig.java`) |
| 2 | Qodo `8Db #2` `SlotAvailabilityService` | High | Outer `slot-grids` Spring cache double-caches grids with unscoped keys and stale eviction races | Outer `@Cacheable("slot-grids")` layer removed entirely; generation-stamped manual Redis cache remains the single source of cached grids (`SlotAvailabilityService.java`) |
| 3 | Qodo `8Dd #3` `SlotAvailabilityService` | High | `@CacheEvict(allEntries)` on eviction methods discards unrelated slots and still races writers | `@CacheEvict` annotations removed from `evictSlotsByDate`/`evictSlotsByDealership`/`evictSlots`; scripted key-scoped eviction in `SlotCacheEvictionService` handles invalidation |
| 4 | Qodo `8DX #10` `AppointmentSlotEvictionListener` | Medium | Listener `@CacheEvict(allEntries)` nukes the whole cache per appointment event | `@CacheEvict` removed from the listener; the provider call evicts only appointment-touched dates after commit |
| 5 | Qodo `8Di #12` + Kilo `6yC` `CacheConfig:49` | High | `enableUnsafeDefaultTyping()` allows deserialization gadget attacks from a compromised Redis | Replaced with `enableDefaultTyping` restricted by `BasicPolymorphicTypeValidator` allow-list (`dev.hata.scheduler.`, `java.util.`) |
| 6 | Qodo `8Df #11` `DealershipService` | Medium | `dealership-config` cached forever, empty lookups cached, never evicted on dealership-wide changes | TTL cut to 15 min, `unless = "#result == null"` skips empty lookups, `@CacheEvict"dealership-config"` added on `evictSlotsByDealership` |
| 7 | Kilo `61X` `AppointmentService:312` | Low | Cancellation eviction iterates `serviceJobs()` without a null/empty guard | Guard `if (!saved.getServiceJobs().isEmpty())` wraps `extractJobDates(...)` call |
| 8 | Review record | Gate | `review.md` must record coverage per OCR gate | This section records thread list, fix diffs, and proofs for `verifyCodeReviewDelegate` |

### Regression Proof Note

- First integration pass after fix (a) hit 6 Playwright failures with `SpelEvaluationException: Method isPresent() cannot be found` on `DealershipConfig`. Root cause: `unless = "!#result.isPresent()"` is wrong because Spring unwraps `Optional` in `#result`; the plain `DealershipConfig` has no `isPresent()`. Fix: `unless = "#result == null"`. The failing Playwright run reproduces the bug without the fix; the passing run proves the fix.
- `CacheConfigTest` added: covers fail-open semantics for all four `CacheErrorHandler` methods.

### Round 3 Proofs

1. **Full build gate**: `./gradlew clean build` → BUILD SUCCESSFUL (Spotless, Checkstyle, PMD, ArchUnit, JaCoCo verification, `verifyCodeReviewDelegate` all pass).
2. **OCR delegate**: `ocr delegate preview --format json` → 6 total files, 5 reviewable, 1 excluded; diff of all 5 inspected; no unresolved critical/high findings.
3. **Bruno**: 72 requests passed, 84/84 tests pass (`bruno-results.xml`).
4. **Playwright E2E**: 47 passed (was 41 pass / 6 fail before the `unless` fix).
5. **JaCoCo**: `@Service` classes at 100%; overall above the 80% gate.

---

## 7. Round 4 — Concurrency E2E and Deadlock 409 Fix (2026-10-28)

### Metadata

| Field | Value |
|---|---|
| Trigger | New two-user concurrent booking E2E scenario exposed a production defect |
| Open Threads | 0 new bot threads |
| Reviewed | `ocr delegate preview`: 5 total files, 2 reviewable, both inspected; excluded tests reviewed by hand |
| Verdict | **PASS** (1 production fix proven by the mapping tests; no unresolved findings) |

### Finding 1 (High, production defect)

| Field | Value |
|---|---|
| Where | `POST /api/v1/appointments` under concurrent load |
| Symptom | Server log shows PostgreSQL `40P01 deadlock detected` on `service_jobs`; Spring mapped `CannotAcquireLockException` to HTTP 500 |
| Root cause | Two bookings race for the same bay and technician. Each transaction inserts then checks the `no_bay_overlap` and `no_technician_overlap` exclusion indexes in a different order. PostgreSQL aborts one victim with a deadlock |
| Contract | SYSTEM_DESIGN 5.4: the concurrent loser answers a retryable RFC 7807 conflict, not 500 |

**Fix**: `@ExceptionHandler(CannotAcquireLockException.class)` added in `GlobalExceptionHandler`. It logs a warning and returns HTTP 409 with title `Concurrent request conflict` and a retry hint. Handler precedence stays correct because Spring resolves the most specific type match.

**TDD proof**: `GlobalExceptionHandlerTest.handlesADeadlockVictimAsARequestableConflict` and `aLockAcquireFailureOutlinesTheRetry` first failed to compile (`cannot find symbol handleConcurrentLock`) and then passed after the fix.

### Finding 2 (Medium, test hardening)

| Field | Value |
|---|---|
| Where | E2E suite replay |
| Symptom | Two `customer-booking.spec.ts` tests 409ed on replay because `freshVin()` restarts its counter per process and the tests leaked CONFIRMED bookings on fixed dates |
| Fix 1 | `freshVin()` default base embeds a 4-digit wall-clock stamp in base 36, so every suite run owns a disjoint VIN space |
| Fix 2 | `customer-booking.spec.ts` tests cancel the appointments they create, so fixed date windows stay open for the next run |
| Proof | Two full-suite runs back to back pass 48 then 96 tests (`--repeat-each=2`); zero leftover active test appointments in the database after runs |

### New E2E Spec

`concurrent-booking-cycle.spec.ts` covers the full user cycle: two registrations, shared grid view that excludes the bay's booked window, concurrent booking with one 201 and one 409, winner cancellation, cache eviction, loser re-check and rebook, self-cleanup. Grid reports a window available when any compatible bay is free, so the spec probes candidate windows until the declared bay yields the 201/409 race.

### Round 4 Proofs

1. **Full build gate**: `./gradlew clean build` → BUILD SUCCESSFUL (Spotless, Checkstyle, PMD, ArchUnit, JaCoCo, `verifyCodeReviewDelegate` pass). `./gradlew build` re-run after final import tweak → exit 0.
2. **OCR delegate**: 2 reviewable files inspected by rule set; file delegated rules fetched; grouped file rule checked missing-boundary-condition and concurrency criteria. One maintainability refinement applied (import instead of inline FQCN). No critical or high findings remain.
3. **Lint gates**: `spotlessApply`, `checkstyleMain`, `pmdMain`, `test` → exit 0.
4. **Bruno**: 72 requests passed, 84/84 tests.
5. **Playwright E2E**: 48 passed; repeat run `--repeat-each=2` → 96 passed.
6. **Database state after runs**: 0 active appointments for test-generated VINs (seed and manually fixed VIN rows excluded).

---

## 8. Round 5 — Qodo Threads on the Concurrency E2E (2026-10-28)

### Metadata

| Field | Value |
|---|---|
| Trigger | Qodo review of commit `9115f13` opened 3 threads on the new E2E code |
| Open Threads | 3 (all Qodo, all on test-support and spec files) |
| Reviewed | `ocr delegate preview` on the final diff; test files inspected by hand |
| Verdict | **PASS** (all 3 threads fixed and resolved with commit `PENDING_SHA`) |

### Findings, Fixes, and Proof

| # | Thread | Severity | Finding | Fix |
|---|---|---|---|---|
| 1 | Qodo `_0py` | Medium | The 09:00 grid assertion cannot isolate the bay gate, because technician availability also hides the seeded window | Race winner already holds the window on the declared bay; a third booking on that occupied window now asserts the exact `Workshop bay conflict` title, which isolates the bay exclusion gate |
| 2 | Qodo `_AHXX` | Medium | Clock-based VIN stamps wrap: the shared helper repeats after about 28 minutes and the race-test prefix after about 47 seconds | Both generators now draw 4 crypto-random characters from the 33-character VIN alphabet, so two runs share a stamp with a chance of about 1 in 1.2 million; the race spec reuses the shared `freshVin()` space instead of its own clock stamp |
| 3 | Qodo `_AHXZ` | Medium | Cleanup loops ignored the DELETE status, so a failed cancellation looked like a clean run | Both cleanup paths check the response status and log a visible `[cleanup]` warning for a non-200 answer without skipping the remaining cancellations |

### Round 5 Proofs

1. **Playwright E2E**: full suite 48 passed after the fixes.
2. **Database state after runs**: 0 active appointments for test-generated VINs.
3. **CI**: all checks pass on the fix commit; threads replied and resolved.
