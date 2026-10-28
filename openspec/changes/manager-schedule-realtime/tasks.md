# Tasks: Manager Real-Time Schedule Board

CHANGE   manager-schedule-realtime     BRANCH   feature/manager-schedule-realtime
STATES   14   done 1  current 1  next 2  skipped 0  failed 0  pending 13
TASKS    0    done 0  open 0

  #   state                     mark     proof
  1   Propose                   current  openspec validate --strict pending user approval
  2   Branch                    pending  branch feature/manager-schedule-realtime
  3   Plan                      pending  next task from tasks.md
  4   Code                      pending  TDD ladder per task
  5   Format                    pending  spotlessApply green
  6   Test                      pending  gradlew test jacocoTestReport green
  7   Review                    pending  ocr delegate, no critical or high findings
  8   Verify                    pending  openspec validate strict, clean build, Bruno, Playwright
  9   Request approval          pending  user approves diff before push
 10   Publish                   pending  commit, push, open pull request
 11   PR verify                 pending  CI green, all review threads resolved
 12   Archive                   pending  /opsx-archive, diagram moved to docs/diagrams
 13   Review archive            pending  archive pushed to same pull request
 14   Merge                     pending  human answers how to proceed

## Backend Tasks

- [ ] T1. Staff scope resolver in `common/security`: resolve dealership scope from `AuthPrincipal`
      role and claims. Failing unit test first.
- [ ] T2. `ScheduleService` day view: per-bay columns with job blocks for one date, dealership
      scoped. Failing repo and service tests first. Bruno file `schedule-day.bru`.
- [ ] T3. Role scope enforcement on schedule endpoints: MANAGER own dealership, ADMIN parameter
      scope, CUSTOMER HTTP 403. Tests and Bruno rejections.
- [ ] T4. `ScheduleService` history list with `status`, `from`, `to`, `bayId`, `customerPhone`
      filters, start time descending, unique ids. Bruno file `schedule-history.bru`.
- [ ] T5. Staff detail view on `GET /api/v1/appointments/{id}`: customer profile, vehicle, job
      notes, estimate and final cost, communication logs. Out-of-scope HTTP 404. Bruno file
      `appointment-get-staff.bru`.
- [ ] T6. Staff fan-out in `NotificationService` and `SseEmitterRegistry`: broadcast requested,
      confirmed, cancelled events to dealership staff sessions after commit. Concurrency test with
      two overlapping bookings on different bays.
- [ ] T7. Staff notification history: log each staff dispatch, read `GET /api/v1/notifications/my`
      for staff with MANAGER and ADMIN scope. Bruno file `notifications-my-staff.bru`.
- [ ] T8. ArchUnit rules still pass. `./gradlew :service-scheduler-server:test` green.

## Frontend Tasks

- [ ] T9. MSW mock handlers in `tests/unit/mock/handlers.ts` for schedule day, history, staff
      detail, staff notifications. `vue-reviewer` review of the handlers.
- [ ] T10. Manager board page: day grid by bay, SSE subscription, live add and remove without
      refresh. Unit tests against the mock.
- [ ] T11. History page with filters and detail drawer. Unit tests against the mock.
- [ ] T12. Playwright E2E `manager-realtime-board.spec.ts`: two concurrent bookings at the same
      time on different bays appear without refresh; cancellation drops without refresh; SSE
      notification text shows; notification list via API shows the same events. One screenshot per
      acceptance criterion.

## Diagram, Review, and Release Tasks

- [ ] T13. Archify sequence diagram `manager-schedule-board`: customer booking to manager board,
      including the concurrent different-bay scenario. Validate, finalize HTML, link from
      `docs/diagrams/index.html` and the relevant document.
- [ ] T14. OCR review delegate rounds recorded in `openspec/changes/manager-schedule-realtime/review.md`.
- [ ] T15. Full gate: `openspec validate manager-schedule-realtime --strict`, `./gradlew clean build`,
      Bruno suite, Playwright suite.
