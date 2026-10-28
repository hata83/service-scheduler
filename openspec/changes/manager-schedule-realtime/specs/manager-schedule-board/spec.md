# Spec Delta: manager-schedule-board

## Purpose

The manager schedule board shows the full dealership day schedule in real time and reads history,
filtered lists, and full booking details for staff roles MANAGER and ADMIN.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/schedule/day` | GET | `service-scheduler-server/src/test/bruno/api/schedule/schedule-day.bru` | Full day schedule by bay |
| `/api/v1/schedule/history` | GET | `service-scheduler-server/src/test/bruno/api/schedule/schedule-history.bru` | Filtered appointment history |
| `/api/v1/appointments/{id}` | GET | `service-scheduler-server/src/test/bruno/api/appointment/appointment-get-staff.bru` | Staff enriched detail |
| `/api/v1/jobs/{id}/notes` | POST | `service-scheduler-server/src/test/bruno/api/schedule/job-notes.bru` | Technician appends inspection note |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Day schedule view | `service-scheduler-ui/tests/e2e/manager-realtime-board.spec.ts` | `docs/diagrams/system/manager-schedule-flow.html` |
| Filtered history list | `service-scheduler-ui/tests/e2e/manager-realtime-board.spec.ts` | `docs/diagrams/system/manager-schedule-flow.html` |
| Staff booking detail | `service-scheduler-ui/tests/e2e/manager-realtime-board.spec.ts` | `docs/diagrams/system/manager-schedule-flow.html` |
| Job inspection notes | `service-scheduler-ui/tests/e2e/manager-realtime-board.spec.ts` | `docs/diagrams/system/manager-schedule-flow.html` |
| Role scope enforcement | `service-scheduler-ui/tests/e2e/manager-realtime-board.spec.ts` | `docs/diagrams/system/manager-schedule-flow.html` |

## ADDED Requirements

### Requirement: Day schedule view

The system SHALL return the full dealership schedule for one date via `GET /api/v1/schedule/day` with
one column per bay and every service job of that bay as a block.

#### Scenario: Manager reads the day schedule
- **WHEN** a MANAGER requests `GET /api/v1/schedule/day?date=2026-10-28`
- **THEN** response status is 200 OK and body lists every bay with its service job blocks including
  status, times, customer name, phone, VIN, and service name

#### Scenario: Customer cannot read the day schedule
- **WHEN** a CUSTOMER requests `GET /api/v1/schedule/day`
- **THEN** response status is 403 Forbidden with an `application/problem+json` body

### Requirement: Role-scoped schedule access

The system SHALL scope every schedule read to the staff role. A MANAGER reads only its
`dealershipId` claim. An ADMIN may pass a `dealershipId` parameter and otherwise reads all
dealerships.

#### Scenario: Manager reads only its own dealership
- **WHEN** a MANAGER of dealership A requests `GET /api/v1/schedule/history` for dealership B
- **THEN** response status is 403 Forbidden with an `application/problem+json` body

#### Scenario: Admin selects a dealership
- **WHEN** an ADMIN requests `GET /api/v1/schedule/day?date=2026-10-28&dealershipId=A`
- **THEN** response status is 200 OK and body contains only dealership A bays and jobs

### Requirement: Filtered schedule history

The system SHALL return a filtered appointment history via `GET /api/v1/schedule/history` with
optional `status`, `from`, `to`, `bayId`, and `customerPhone` filters, sorted by start time
descending, with a unique `id` on every item.

#### Scenario: Filter history by status and date range
- **WHEN** a MANAGER requests `GET /api/v1/schedule/history?status=CANCELLED&from=2026-10-01&to=2026-10-31`
- **THEN** response status is 200 OK and body contains only CANCELLED appointments in the range

#### Scenario: Filter history by bay
- **WHEN** a MANAGER requests `GET /api/v1/schedule/history?bayId=B`
- **THEN** response status is 200 OK and body contains only appointments that hold a job in bay B

### Requirement: Staff booking detail

The system SHALL return a staff detail view via `GET /api/v1/appointments/{id}` for an appointment
inside the role scope. The response adds the customer contact profile, vehicle, per-job booking
notes, estimate cost and final cost, and communication log records.

#### Scenario: Manager reads booking detail
- **WHEN** a MANAGER requests `GET /api/v1/appointments/{id}` for an appointment of its dealership
- **THEN** response status is 200 OK and body contains customer name, phone, VIN, vehicle category,
  job notes, estimate and final costs, and communication logs

#### Scenario: Manager cannot read a foreign dealership booking
- **WHEN** a MANAGER requests `GET /api/v1/appointments/{id}` for an appointment of another
  dealership
- **THEN** response status is 404 Not Found with an `application/problem+json` body

### Requirement: Job inspection notes

The system SHALL provide `POST /api/v1/jobs/{id}/notes` for the TECHNICIAN role to append an
inspection note to its assigned service job. The note text SHALL be stored in the `notes` column of
`service_jobs` and SHALL appear in the staff detail view.

#### Scenario: Technician appends an inspection note
- **WHEN** a TECHNICIAN posts a note to `POST /api/v1/jobs/{id}/notes` for an assigned job
- **THEN** response status is 200 OK and the note text is appended to the job notes column

#### Scenario: Customer cannot append notes
- **WHEN** a CUSTOMER posts a note to `POST /api/v1/jobs/{id}/notes`
- **THEN** response status is 403 Forbidden with an `application/problem+json` body
