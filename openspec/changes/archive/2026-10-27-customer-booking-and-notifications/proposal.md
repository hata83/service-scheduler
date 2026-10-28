# Proposal: Customer Booking and SSE Notifications

## Why

Customers need to book workshop service appointments and track their status in real time. Dealerships configure automatic confirmation to streamline customer requests. Customers must view their past appointment history and cancel upcoming appointments before arriving for `check-in`. Real-time Server-Sent Events (SSE) notify customers immediately when the dealership confirms the booking.

## What Changes

- Implement `POST /api/v1/appointments` to book an appointment with mandatory `X-Idempotency-Key` header.
- Support booking appointments with multiple service types for a vehicle VIN.
- Require that service job time slots within an appointment do not overlap each other.
- Require that each service job time slot duration does not exceed the service type base duration.
- Map intra-appointment overlap and duration limit failures to RFC 7807 problem details with HTTP 400 Bad Request.
- Evaluate dealership `auto_confirm_appointments` setting: set status to `CONFIRMED` when true, or `REQUESTED` when false.
- Enforce PostgreSQL exclusion constraints (`no_vehicle_overlap` and `no_bay_overlap`) to prevent concurrent booking collisions.
- Map PostgreSQL exclusion constraint violations to RFC 7807 problem details with HTTP 409 Conflict.
- Support idempotency replay: return cached 201 response when a client replays an idempotency key.
- Implement `GET /api/v1/appointments` to list appointment history for the authenticated customer.
- Implement `GET /api/v1/appointments/{id}` to view detailed appointment information.
- Enforce state machine rules: allow cancellation (`DELETE /api/v1/appointments/{id}`) before `check-in` only (`REQUESTED` and `CONFIRMED`).
- Reject cancellation with HTTP 422 Problem Details when an appointment enters `CHECKED_IN` or later states.
- Evict Redis slot availability cache for the affected date upon booking or cancellation.
- Keep slot availability service unchanged as a demo stub.
- Implement `GET /api/v1/notifications/stream` for real-time SSE alerts using direct `httpOnly` JWT cookie authentication.
- Enforce a single active SSE connection per user phone number and emit periodic heartbeat comments.
- Persist domain events in `outbox_events` and dispatch SSE events via `@TransactionalEventListener(AFTER_COMMIT)`.
- Record notification dispatches in `communication_logs` and provide `GET /api/v1/notifications/my`.

## Capabilities

### New Capabilities
- `customer-booking`: Covers appointment creation, multi-job validation, auto-confirmation, idempotency replay, and database exclusion concurrency protection.
- `appointment-history`: Covers customer appointment history query, status inspection, and detailed record retrieval.
- `appointment-state-machine`: Enforces lifecycle transitions, restricting customer cancellation to pre-`check-in` states (`REQUESTED` and `CONFIRMED`).
- `sse-notifications`: Delivers real-time Server-Sent Events, single stream per phone, transactional outbox dispatch, and communication logs.

### Modified Capabilities
None. Existing specifications remain unchanged.

## Verification Ladder Mapping

This change follows the backend-first verification ladder in AGENTS.md Section 5:
- **BE-1 / BE-2**: Write failing JUnit 5 repository and service tests before writing implementation code.
- **BE-3**: Apply Spotless formatting.
- **BE-4**: Run database exclusion constraints, state machine transitions, and outbox event dispatch in integration tests.
- **BE-5**: Add Bruno `.bru` contract files for all booking, history, cancellation, and notification endpoints.
- **BE-6**: Reach 100% test coverage on new `@Service` classes and at least 80% overall.
- **BE-7**: Run Alibaba OCR code review delegation.
- **FE-1 to FE-5**: Provide MSW mock handlers, UI service integration, and Playwright E2E coverage.

## Delivered Artifacts

### Bruno API Test Files
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-create.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-multi-job.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-duration-exceeded.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-intra-job-overlap.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-duplicate-key.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-overlap-conflict.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointments-list-my.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-get-by-id.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-cancel.bru`
- `service-scheduler-server/src/test/bruno/api/appointment/appointment-cancel-post-checkin-rejected.bru`
- `service-scheduler-server/src/test/bruno/api/notification/notifications-stream.bru`
- `service-scheduler-server/src/test/bruno/api/notification/notifications-my.bru`

### Playwright E2E Specs
- `service-scheduler-ui/tests/e2e/customer-booking.spec.ts`
- `service-scheduler-ui/tests/e2e/appointment-history.spec.ts`
- `service-scheduler-ui/tests/e2e/appointment-state-machine.spec.ts`
- `service-scheduler-ui/tests/e2e/sse-notifications.spec.ts`

### Archify Diagrams
- `docs/diagrams/system/booking-sequence.html` (sequence)
- `docs/diagrams/system/appointment-lifecycle.html` (lifecycle)
- `docs/diagrams/system/sse-notification.html` (sequence)
