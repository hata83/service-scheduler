# Proposal: Manager Real-Time Schedule Board

## Why

Dealership managers must watch and run the day schedule in real time. A customer booking or
cancellation must appear on the schedule board without a page refresh. Managers also need a filtered
history list and a full booking detail view. They must read notifications both from the SSE stream
and from the notification history API. SYSTEM_DESIGN sections 3.3, 9.1, 10.2, and 14.1 already
define these capabilities. This change implements the designed manager slice.

## What Changes

- Implement `GET /api/v1/schedule/day?date=YYYY-MM-DD`. Role `MANAGER` sees its dealership schedule.
  Role `ADMIN` may pass an optional `dealershipId` filter and read any dealership.
- Broadcast SSE events to connected staff after commit. Events `appointment.requested`,
  `appointment.confirmed`, and `appointment.cancelled` fan out to all staff sessions of the
  dealership. The frontend schedule updates without refresh.
- Extend `SseEmitterRegistry` with a dealership staff fan-out that keeps the single-stream-per-phone
  rule and the 25-second heartbeat.
- Record each staff fan-out dispatch as a `communication_logs` row with the manager staff phone as
  recipient. `GET /api/v1/notifications/my` then works for staff too.
- Implement `GET /api/v1/schedule/history` with filters: `status`, `from`, `to`, `bayId`,
  `customerPhone`. MANAGER reads its dealership. ADMIN filters by `dealershipId`.
- Extend `GET /api/v1/appointments/{id}` for staff access inside the role scope. The staff detail
  response adds the customer contact profile, vehicle info, per-job booking notes, estimate cost
  against final cost, and the communication log list.
- Feedback requirement decision: feedback data is the per-job booking notes plus estimate versus
  final cost. No new feedback table. User decision recorded 2026-10-28.
- Schedule access decision: roles MANAGER and ADMIN open the schedule, history, detail, and board.
  User decision recorded 2026-10-28.
- Add a Playwright E2E spec for concurrent bookings at the same time on different bays. The manager
  board must show every booking in real time and must drop each cancelled booking in real time.
- Add an Archify sequence diagram for the customer to manager schedule flow.

## Capabilities

### New Capabilities
- `manager-schedule-board`: Day schedule view, filtered history list, staff booking detail, and
  role-scoped access for MANAGER and ADMIN.

### Modified Capabilities
- `sse-notifications`: Adds staff fan-out broadcast, staff notification history, and staff delivery
  logging to the existing stream and audit requirements.
- `appointment-history`: Adds staff access to appointment detail with the enriched response.

## Verification Ladder Mapping

This change follows the backend-first verification ladder in AGENTS.md Section 5:
- **BE-1 / BE-2**: Write failing JUnit 5 tests for the schedule service, staff fan-out, and role
  scope before implementation.
- **BE-3**: Run Spotless formatting.
- **BE-4**: Run constraint and concurrency integration tests with Testcontainers.
- **BE-5**: Add Bruno `.bru` files for every new or changed endpoint.
- **BE-6**: Run `./gradlew test jacocoTestReport`. Hold 100 percent coverage on new `@Service`
  classes and at least 80 percent overall.
- **BE-7**: Run the OCR review delegate and record rounds in `review.md`.
- **FE-1 to FE-5**: Mock handlers, unit tests, integration, and Playwright E2E with one screenshot
  per acceptance criterion.

## Risk and Dependency Notes

- The outbox already stages `appointment.requested` and `appointment.cancelled`. No schema migration
  is required. All fan-out work lives in the notification service and registry.
- Staff sessions authenticate through the existing OTP login and JWT cookie. The seed provides
  manager phone `+84900000002` for tests.
