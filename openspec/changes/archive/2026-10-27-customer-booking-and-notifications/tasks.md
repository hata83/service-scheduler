# Tasks

```
CHANGE   customer-booking-and-notifications               BRANCH   feature/customer-booking-and-notifications
STATES   14   done 13  current 1  next 0  skipped 0  failed 0  pending 0
TASKS    31   done 31  open 0

  #   state                     mark     proof
  1   Propose                   done     openspec validate --strict: valid
  2   Branch                    done     branch is feature/customer-booking-and-notifications
  3   Plan                      done     31 tasks tracked across 4 specs
  4   Code                      done     all backend legs, models, and mock handlers complete
  5   Format                    done     spotlessApply, checkstyle, and pmd zero violations
  6   Test                      done     217 backend tests pass, JaCoCo 100% services, 93.2% overall
  7   Review                    done     review.md recorded, verifyCodeReviewDelegate passed
  8   Verify                    done     openspec validate --strict and ./gradlew clean build passed
  9   Approval                  done     HIL-2 approved by user
 10   Publish                   done     PR #4 opened: https://github.com/hata83/service-scheduler/pull/4
 11   Verify PR                 done     all CI checks green, Qodo & Kilo reviews resolved
 12   Archive                   done     change archived to openspec/changes/archive/2026-10-27-customer-booking-and-notifications/
 13   Review archive            done     archived specs & CI run 40546256930 passed (all 5 checks green)
 14   Merge                     current  HIL-3 human gate awaiting merge instructions
```

## 1. Branch Setup (State 2)

- [x] 1.1 Create feature branch `feature/customer-booking-and-notifications` from `master`. Verify `git branch --show-current` outputs `feature/customer-booking-and-notifications`.

## 2. Outbox & Communication Logs Schema & Entities (`sse-notifications`)

- [x] 2.1 Add Flyway migration `V3__outbox_and_communication_logs_schema.sql` creating `outbox_events` and `communication_logs` tables. Verify `./gradlew test` executes migration successfully.
- [x] 2.2 Create JPA entities `OutboxEvent` and `CommunicationLog` with Spring Data repositories. Verify repository tests pass.

## 3. Real-Time SSE Stream Backend Leg (`sse-notifications`)

- [x] 3.1 TDD BE-1/BE-2: Implement `SseEmitterRegistry` managing active `SseEmitter` by phone number with automatic eviction and 25s ping heartbeats. Verify unit tests pass.
- [x] 3.2 TDD BE-1/BE-2: Implement `OutboxEventService` and `@TransactionalEventListener(AFTER_COMMIT)` publishing events to `SseEmitterRegistry` and logging to `CommunicationLogRepository`. Verify unit tests pass.
- [x] 3.3 Implement `GET /api/v1/notifications/stream` and `GET /api/v1/notifications/my` in `NotificationController` with SpringDoc annotations. Add Bruno files `notifications-stream.bru` and `notifications-my.bru`. Verify Bruno tests pass.

## 4. Customer Booking Core Backend Leg (`customer-booking`)

- [x] 4.1 TDD BE-1/BE-2: Create `Appointment`, `ServiceJob`, and `JobTechnicianAssignment` entities and Spring Data repositories. Verify repository tests pass.
- [x] 4.2 TDD BE-1/BE-2: Implement `AppointmentService.book()` evaluating dealership auto-confirm, inserting appointment and child jobs, staging outbox events, and evicting date slot cache. Verify unit tests pass.
- [x] 4.3 TDD BE-1/BE-2: Implement multi-job intra-appointment validation rejecting overlapping service job slots for a vehicle VIN with HTTP 400 Bad Request. Verify unit tests pass.
- [x] 4.4 TDD BE-1/BE-2: Implement service job duration bound validation rejecting job slots exceeding service type base duration (`estimated_minutes`) with HTTP 400 Bad Request. Verify unit tests pass.
- [x] 4.5 Implement `POST /api/v1/appointments` requiring `X-Idempotency-Key` and returning 201 Created. Add Bruno files `appointment-create.bru`, `appointment-create-multi-job.bru`, `appointment-create-duration-exceeded.bru`, and `appointment-create-intra-job-overlap.bru`. Verify Bruno tests pass.

## 5. Appointment History Backend Leg (`appointment-history`)

- [x] 5.1 TDD BE-1/BE-2: Implement `AppointmentService.findCustomerAppointments()` querying customer-owned appointments ordered by start time descending. Verify unit tests pass.
- [x] 5.2 TDD BE-1/BE-2: Implement `AppointmentService.findAppointmentById()` enforcing customer ownership and returning HTTP 404 for other customers' appointments. Verify unit tests pass.
- [x] 5.3 Implement `GET /api/v1/appointments` and `GET /api/v1/appointments/{id}` in `AppointmentController` with SpringDoc annotations. Add Bruno files `appointments-list-my.bru` and `appointment-get-by-id.bru`. Verify Bruno tests pass.

## 6. Pre-`check-in` State Machine Cancellation Backend Leg (`appointment-state-machine`)

- [x] 6.1 TDD BE-1/BE-2: Implement `AppointmentService.cancelAppointment()` allowing cancellation from `REQUESTED` and `CONFIRMED` only, releasing bay reservations, staging outbox events, and evicting date slot cache. Verify unit tests pass.
- [x] 6.2 TDD BE-1/BE-2: Verify cancellation is rejected with HTTP 422 Problem Details when status is `CHECKED_IN`, `IN_PROGRESS`, `COMPLETED`, or `INVOICED`. Verify unit tests pass.
- [x] 6.3 Implement `DELETE /api/v1/appointments/{id}` in `AppointmentController`. Add Bruno files `appointment-cancel.bru` and `appointment-cancel-post-checkin-rejected.bru`. Verify Bruno tests pass.

## 7. Concurrency Overlap & Database Exclusion Integration Tests (`customer-booking`)

- [x] 7.1 Implement integration test verifying multi-job booking with 2 service types succeeds with HTTP 201 Created. Add Bruno file `appointment-create-multi-job.bru`. Verify test passes.
- [x] 7.2 Implement integration test verifying intra-appointment overlapping jobs reject with HTTP 400 Bad Request. Add Bruno file `appointment-create-intra-job-overlap.bru`. Verify test passes.
- [x] 7.3 Implement integration test verifying job duration exceeding base duration rejects with HTTP 400 Bad Request. Add Bruno file `appointment-create-duration-exceeded.bru`. Verify test passes.
- [x] 7.4 Implement integration test verifying PostgreSQL `no_vehicle_overlap` EXCLUDE constraint rejects overlapping bookings for the same vehicle VIN with HTTP 409 Conflict. Add Bruno file `appointment-create-overlap-conflict.bru`. Verify test passes.
- [x] 7.5 Implement integration test verifying PostgreSQL `no_bay_overlap` EXCLUDE constraint rejects overlapping bookings for the same workshop bay with HTTP 409 Conflict. Verify test passes.
- [x] 7.6 Implement integration test verifying `X-Idempotency-Key` replay returns cached response without duplicate database insertion. Add Bruno file `appointment-create-duplicate-key.bru`. Verify test passes.

## 8. Frontend MSW Mocks & Playwright E2E Integration Leg

- [x] 8.1 Add MSW mock handlers for appointments (`POST`, `GET`, `DELETE`) and SSE stream in `service-scheduler-ui/tests/unit/mock/handlers.ts`. Verify `npm run typecheck` passes.
- [x] 8.2 Implement Playwright E2E test `customer-booking.spec.ts` covering multi-job appointment booking with 2 service types, auto-confirm status, and idempotency replay. Verify test passes.
- [x] 8.3 Implement Playwright E2E test `appointment-history.spec.ts` covering appointment history list and itemized details inspection. Verify test passes.
- [x] 8.4 Implement Playwright E2E test `appointment-state-machine.spec.ts` covering cancellation before `check-in` and rejection after `check-in`. Verify test passes.
- [x] 8.5 Implement Playwright E2E test `sse-notifications.spec.ts` covering SSE stream connection, event delivery, and notification history. Verify test passes.

## 9. Architecture Diagrams and Documentation Alignment

- [x] 9.1 Finalize Archify sequence viewer `docs/diagrams/system/booking-sequence.html`. Verify `node .agents/skills/archify/archify/bin/archify.mjs validate sequence docs/diagrams/src/system/booking-sequence.sequence.json` passes.
- [x] 9.2 Finalize Archify lifecycle viewer `docs/diagrams/system/appointment-lifecycle.html`. Verify `node .agents/skills/archify/archify/bin/archify.mjs validate lifecycle docs/diagrams/src/system/appointment-lifecycle.lifecycle.json` passes.
- [x] 9.3 Finalize Archify sequence viewer `docs/diagrams/system/sse-notification.html`. Verify `node .agents/skills/archify/archify/bin/archify.mjs validate sequence docs/diagrams/src/system/sse-notification.sequence.json` passes.
