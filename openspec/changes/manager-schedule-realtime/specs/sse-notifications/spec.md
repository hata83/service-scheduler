# Spec Delta: sse-notifications

## Purpose

SSE notifications now fan out booking and schedule events to staff boards in addition to customers,
with per-recipient staff audit logging and staff notification history.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/notifications/stream` | GET | `service-scheduler-server/src/test/bruno/api/notification/notifications-stream-staff.bru` | Open staff SSE stream |
| `/api/v1/notifications/my` | GET | `service-scheduler-server/src/test/bruno/api/notification/notifications-my-staff.bru` | Staff notification history |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Staff SSE fan-out | `service-scheduler-ui/tests/e2e/manager-realtime-board.spec.ts` | `docs/diagrams/system/manager-schedule-flow.html` |
| Staff notification history | `service-scheduler-ui/tests/e2e/manager-realtime-board.spec.ts` | `docs/diagrams/system/manager-schedule-flow.html` |

## ADDED Requirements

### Requirement: Staff SSE fan-out broadcast

The system SHALL broadcast `appointment.requested`, `appointment.confirmed`, and
`appointment.cancelled` events after commit to every connected staff session of the dealership. The
broadcast keeps the single active stream per phone number.

#### Scenario: Concurrent bookings fan out to the staff board
- **WHEN** two customers create bookings at the same time on different bays
- **THEN** the dealership staff session receives both broadcast events without any reconnect

#### Scenario: Cancellation fans out to the staff board
- **WHEN** a customer cancels an appointment
- **THEN** the dealership staff session receives `appointment.cancelled` with the appointment id

#### Scenario: Staff fan-out keeps single stream per phone
- **WHEN** a manager reconnects the staff stream while broadcasts are in flight
- **THEN** the system terminates the prior stream and delivers later events to the new stream only

### Requirement: Staff notification history and delivery logging

The system SHALL record each staff fan-out dispatch in `communication_logs` with the staff phone as
recipient, and SHALL return the log list via `GET /api/v1/notifications/my` for staff roles. A
MANAGER reads logs of its dealership. An ADMIN reads logs of all dealerships.

#### Scenario: Manager reads notification list via API
- **WHEN** a MANAGER requests `GET /api/v1/notifications/my`
- **THEN** response status is 200 OK and body carries dispatched notification records ordered by
  dispatch time descending

#### Scenario: Admin reads all dealership logs
- **WHEN** an ADMIN requests `GET /api/v1/notifications/my`
- **THEN** response status is 200 OK and body carries notification records across dealerships
