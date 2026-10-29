# Tasks: Manager Real-Time Schedule Board

CHANGE   manager-schedule-realtime     BRANCH   feature/manager-schedule-realtime
STATES   14   done 10  current 1  next 1  skipped 0  failed 0  pending 3
TASKS    16   done 16  open 0

  #   state                     mark     proof
  1   Propose                   done     openspec validate --strict: valid; HIL-1 approved
  2   Branch                    done     branch is feature/manager-schedule-realtime
  3   Plan                      done     tasks.md T1-T16 enumerated
  4   Code                      done     T1-T12 backend and frontend legs implemented
  5   Format                    done     spotlessApply and spotlessCheck green
  6   Test                      done     gradlew test jacocoTestReport (100% service coverage, 80% overall)
  7   Review                    done     ocr delegate review.md recorded; 0 critical or high findings
  8   Verify                    done     openspec validate strict, clean build, Bruno (83/83), Playwright (50/50)
  9   Request approval          done     HIL-2 human gate: approved by human
 10   Publish                   done     committed bdc614c, pushed origin, opened PR #6
 11   PR verify                 current  CI in progress, monitoring Qodo and Kilo reviews
 12   Archive                   next     /opsx-archive, diagram moved to docs/diagrams
 13   Review archive            pending  archive pushed to same pull request
 14   Merge                     pending  human answers how to proceed

## Backend Tasks

- [x] T1. Staff scope resolver in `common/security`: resolve dealership scope from `AuthPrincipal`
      role and claims. Failing unit test first.
- [x] T2. `ScheduleService` day view: per-bay columns with job blocks for one date, dealership
      scoped. Failing repo and service tests first. Bruno file `schedule-day.bru`.
- [x] T3. Role scope enforcement on schedule endpoints: MANAGER own dealership, ADMIN parameter
      scope, CUSTOMER HTTP 403. Tests and Bruno rejections.
- [x] T4. `ScheduleService` history list with `status`, `from`, `to`, `bayId`, `customerPhone`
      filters, start time descending, unique ids. Bruno file `schedule-history.bru`.
- [x] T5. Staff detail view on `GET /api/v1/appointments/{id}`: customer profile, vehicle, job
      notes, estimate and final cost, communication logs. Out-of-scope HTTP 404. Bruno file
      `appointment-get-staff.bru`.
- [x] T5a. V5 migration: add `notes` text column to `service_jobs`.
- [x] T5b. `POST /api/v1/jobs/{id}/notes`: TECHNICIAN appends an inspection note to an assigned job.
      Customer role HTTP 403. Bruno file `job-notes.bru`.
- [x] T6. Staff fan-out in `NotificationService` and `SseEmitterRegistry`: broadcast requested,
      confirmed, cancelled events to dealership staff sessions after commit. Concurrency test with
      two overlapping bookings on different bays.
- [x] T7. Staff notification history: log each staff dispatch, read `GET /api/v1/notifications/my`
      for staff with MANAGER and ADMIN scope. Bruno file `notifications-my-staff.bru`.
- [x] T8. ArchUnit rules still pass. `./gradlew :service-scheduler-server:test` green.

## Frontend Tasks

- [x] T9. MSW mock handlers in `tests/unit/mock/handlers.ts` for schedule day, history, staff
      detail, staff notifications. `vue-reviewer` review of the handlers.
- [x] T10. Manager board page: day grid by bay, SSE subscription, live add and remove without
      refresh. Unit tests against the mock.
- [x] T11. History page with filters and detail drawer. Unit tests against the mock.
- [x] T12. Playwright E2E `manager-realtime-board.spec.ts`: two concurrent bookings at the same
      time on different bays appear without refresh; cancellation drops without refresh; SSE
      notification text shows; notification list via API shows the same events. One screenshot per
      acceptance criterion.

## Diagram, Review, and Release Tasks

- [x] T13. Archify sequence diagram `manager-schedule-board`: customer booking to manager board,
      including the concurrent different-bay scenario. Validate, finalize HTML, link from
      `docs/diagrams/index.html` and the relevant document.
- [x] T14. OCR review delegate rounds recorded in `openspec/changes/manager-schedule-realtime/review.md`.
- [x] T15. Full gate: `openspec validate manager-schedule-realtime --strict`, `./gradlew clean build`,
      Bruno suite, Playwright suite.

--- PR Loop Update (State 11) ---
- Fixed Qodo #11 (AppointmentService concurrent note append) + Kilo warnings.
- Added UUID import to NotificationController.
- Updated ScheduleServiceTest pagination, ServiceJobControllerTest, NotificationServiceTest.
- Committed 11d07cb, pushed to PR #6.
- ./gradlew test: 344 pass. Bruno: pass (83/83 previous). Playwright: connection refused (env, not code).
- Waiting for PR #6 review comments before archive (State 12).

--- PR Loop Update (State 11, Round 2) ---
- Resolved 13 open review threads on PR #6 (5 Qodo, 8 Kilo).
- Qodo High: centralized SSE emitter eviction, stable history page order, local date of every
      dealership for a global date filter.
- Qodo Medium: page and size validation with long offset arithmetic, OpenAPI `anyOf` with
      required distinguishing properties.
- Kilo: conditional customer name build, logical `&&`, identity checked eviction, manager scope
      and job note tenancy verified.
- Added `ScheduleQueryRepositoryTest` for the page order and the per-dealership date filter.
- Added Bruno contracts for the page limit, the size limit, and the OpenAPI response schema.
- `./gradlew clean build`: SUCCESS. Bruno: 86/86 requests, 98/98 tests. Playwright: 50/50 tests.
- Pushed to PR #6, waiting for the next review round before archive (State 12).
