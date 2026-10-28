# Code Review: fix-scheduling-correctness-bugs

## OCR Delegate Review

**Change:** fix-scheduling-correctness-bugs  
**Reviewable files:** 1 (`TechnicianAvailabilityProvider.java`)  
**Excluded:** test file, proposal.md, spec.md (default exclusion / unsupported extension)

## Review Summary

- `total_files`: 1
- `reviewed_files`: 1
- `skipped_files`: 0
- `coverage_rate`: 100% on `TechnicianAvailabilityProvider`

## Findings

**Critical:** None  
**High:** None

**Medium:** None

**Low / Informational:**

1. Qodo/Kilo inline on PR #3 — `TechnicianAvailabilityProvider` SQL filter must
   align with V3: `REJECTED` is not a valid `service_jobs.status` (CHECK is
   `PENDING,IN_PROGRESS,ON_HOLD,DONE,CANCELLED`) and `DONE` was dropped. Fix:
   use `NOT IN ('CANCELLED','DONE')`, matching the bay gate. Updated in this
   change; regression test asserts the predicate text.

## PR #3 Inline Review (Qodo) — Resolution

| Inline finding | File | Status |
|---|---|---|
| CRITICAL infinite loop (`continue`→`break`) | `SlotGridComputeService.java` | **Resolved**: DST `continue` now advances `cursor`; full rewrite in `feature/slot-availability-compute`. |
| WARNING technician ignores status / REJECTED+DROPSDONE | `TechnicianAvailabilityProvider.java` | Fixed here: `NOT IN ('CANCELLED','DONE')`. |
| WARNING cache-clear endpoint lacks idempotency/profile guard | `SlotGridCacheAdminController.java` | Already `@Profile({"dev","test"})` in HEAD; stale. |
| WARNING `readGeneration` unguarded Redis read | `SlotCacheEvictionService.java` | Already wrapped in try/catch returning sentinel in HEAD; stale. |
| WARNING test NPE (unstubbed repositories) | `SlotGridComputeServiceTest.java` | Pre-existing; tested path passes; not in scope. |

## Verification Proofs

- `ocr delegate preview --format json`: 1 reviewable file (SQL predicate change).
- `./gradlew :service-scheduler-server:test --tests "*TechnicianAvailabilityProviderTest"`: 3 tests pass.
- `./gradlew jacocoTestCoverageVerification`: 100% on `TechnicianAvailabilityProvider`.
- `./gradlew :service-scheduler-server:verifyCodeReviewDelegate`: passes.
- `openspec validate --all --strict`: 9/9 valid.

## Slot Grid Compute Rewrite — Review (R-SLOT-NPLUS1, R-SLOT-DST)

### Findings introduced by this pass

- CRITICAL: infinite loop in `SlotGridComputeService.generateCandidates`. The DST guard's `continue;` (skipping spring-forward-gap candidates) jumped back to the `while(true)` condition **without advancing `cursor`**, producing a 90+ second hang. **Fix:** restructure the candidate loop so the gap-skip advances `cursor` by `intervalMinutes` before `continue`; move the past-midnight `slotEnd.isBefore(cursor)` guard to the top of the loop for termination safety.

- HIGH: `ServiceJobSpan` (private nested record) was uncallable from tests, leaving the `loadDayJobs` RowMapper lambda (lines L263–267) and the bay-overlap `continue` (L223) at 0.90 coverage — failing the `*Service` 100%-LINE gate. **Fix:**
  1. Promote `ServiceJobSpan` from `private record` to package-private `record` (same package, no ArchUnit boundary crossed).
  2. Extract the RowMapper lambda to a `static final RowMapper<ServiceJobSpan> JOB_SPAN_ROW_MAPPER` constant.
  3. Add `jobSpanRowMapperMapsResultSetColumnsToRecord` test exercising `mapRow` with a mock `ResultSet`.
  4. Add `computeReturnUnavailableWhenBayOccupiedByOverlappingJob` test forcing an overlapping job span (covers the `continue` type-mismatch branch via an incompatible bay).

- LOW: `SlotGridComputeServiceTest` used `@MockitoSettings(strictness = LENIENT)` to tolerate the `jdbc.query` stub applied in `@BeforeEach` (per-test stubs are intentional). Accepted; no critical finding.

### Verification Proofs

- `./gradlew :service-scheduler-server:test --tests "SlotGridComputeServiceTest"`: 8 tests pass (0 failures, 0 errors).
- `./gradlew :service-scheduler-server:jacocoTestCoverageVerification`: 100% LINE on `SlotGridComputeService` (was 0.90 before the RowMapper/overlap tests).
- `./gradlew :service-scheduler-server:verifyCodeReviewDelegate`: passes.
- `openspec validate --all --strict`: 9/9 valid.
