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

#### Scenario: A closed staff stream stops its dealership routing
- **WHEN** the staff stream of a phone fails during a broadcast or a heartbeat
- **THEN** the system removes the staff routing of that phone and later broadcasts skip it

#### Scenario: A late callback of a replaced stream keeps the new routing
- **WHEN** the callback of a replaced stream runs after a new stream of the same phone started
- **THEN** the dealership routing of the new stream stays unchanged

#### Scenario: A reconnect during a stream failure keeps the new routing
- **WHEN** a stream fails while a new stream of the same phone registers
- **THEN** the new stream keeps its dealership routing and receives later events

#### Scenario: A reconnected stream receives no event of the prior dealership
- **WHEN** a manager reconnects with another dealership while the fan-out of the prior dealership runs
- **THEN** the new stream receives no event of the prior dealership

### Requirement: Staff notification history and delivery logging

The system SHALL record each staff fan-out dispatch in `communication_logs` with the staff phone as
recipient, and SHALL return the log list via `GET /api/v1/notifications/my` for staff roles. A
MANAGER reads logs of its dealership. An ADMIN reads logs of all dealerships. A MANAGER without a
dealership assignment SHALL receive HTTP 403 Forbidden.

#### Scenario: Manager reads notification list via API
- **WHEN** a MANAGER requests `GET /api/v1/notifications/my`
- **THEN** response status is 200 OK and body carries dispatched notification records ordered by
  dispatch time descending

#### Scenario: Admin reads all dealership logs
- **WHEN** an ADMIN requests `GET /api/v1/notifications/my`
- **THEN** response status is 200 OK and body carries notification records across dealerships

#### Scenario: Manager without a dealership reads no logs
- **WHEN** a MANAGER without a dealership assignment requests `GET /api/v1/notifications/my`
- **THEN** response status is 403 Forbidden with an `application/problem+json` body
