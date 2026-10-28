# Spec Delta: appointment-history

## Purpose

Staff roles gain access to appointment detail for management work. The customer contract keeps its
current shape and behavior.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/appointments/{id}` | GET | `service-scheduler-server/src/test/bruno/api/appointment/appointment-get-staff.bru` | Staff enriched detail view |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Staff detail access and enrichment | `service-scheduler-ui/tests/e2e/manager-realtime-board.spec.ts` | `docs/diagrams/system/manager-schedule-flow.html` |

## ADDED Requirements

### Requirement: Staff detail access scope

The system SHALL allow MANAGER and ADMIN roles to read `GET /api/v1/appointments/{id}` for any
appointment inside the role scope. A MANAGER reads appointments of its dealership only. Out-of-scope
reads return the same HTTP 404 Problem Details as customer cross-access.

#### Scenario: Admin reads any dealership booking
- **WHEN** an ADMIN requests `GET /api/v1/appointments/{id}` for any appointment
- **THEN** response status is 200 OK and body carries the appointment and the staff view

#### Scenario: Customer response stays unchanged
- **WHEN** a CUSTOMER requests `GET /api/v1/appointments/{id}` for an owned appointment
- **THEN** response status is 200 OK and body keeps the existing customer detail shape without the
  staff view

### Requirement: Feedback data in staff detail

The system SHALL surface feedback data in the staff detail view from per-job booking notes and from
the estimate cost versus final cost comparison. No separate feedback table exists.

#### Scenario: Detail shows completed job feedback
- **WHEN** a MANAGER reads the detail of an appointment with a DONE job that holds notes and final
  cost
- **THEN** the staff view shows the note text and shows estimate cost and final cost for the job
