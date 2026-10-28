# Spec Delta: appointment-state-machine

## Purpose

Appointment state machine enforces lifecycle status transitions and restricts cancellation strictly to `pre-check-in` stages.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/appointments/{id}` | DELETE | `service-scheduler-server/src/test/bruno/api/appointment/appointment-cancel.bru` | Cancel appointment |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Pre-`check-in` appointment cancellation | `service-scheduler-ui/tests/e2e/appointment-state-machine.spec.ts` | `docs/diagrams/system/appointment-lifecycle.html` |
| Post-`check-in` cancellation rejection | `service-scheduler-ui/tests/e2e/appointment-state-machine.spec.ts` | `docs/diagrams/system/appointment-lifecycle.html` |

## ADDED Requirements

### Requirement: Pre-`check-in` appointment cancellation

The system SHALL allow authenticated customers to cancel appointments in `REQUESTED` or `CONFIRMED` status via `DELETE /api/v1/appointments/{id}`.

#### Scenario: Cancel confirmed appointment
- **WHEN** customer cancels an appointment with `CONFIRMED` status
- **THEN** response status is 200 OK and appointment status updates to `CANCELLED`

#### Scenario: Cancel requested appointment
- **WHEN** customer cancels an appointment with `REQUESTED` status
- **THEN** response status is 200 OK and appointment status updates to `CANCELLED`

### Requirement: Post-`check-in` cancellation rejection

The system SHALL reject cancellation requests with HTTP 422 Problem Details when an appointment enters `CHECKED_IN` or later states.

#### Scenario: Reject cancellation for `checked-in` appointment
- **WHEN** customer attempts to cancel an appointment with `CHECKED_IN` status
- **THEN** response status is 422 Unprocessable Entity with an `application/problem+json` body

#### Scenario: Reject cancellation for in-progress appointment
- **WHEN** customer attempts to cancel an appointment with `IN_PROGRESS` status
- **THEN** response status is 422 Unprocessable Entity with an `application/problem+json` body

## Endpoint Contracts

### 1. `DELETE /api/v1/appointments/{id}`

#### Request Headers
```http
Accept: application/json
Cookie: jwt=eyJhbGciOiJIUzI1NiJ9...
```

#### Request Body
```
(empty)
```

#### Response Headers
```http
HTTP/1.1 200 OK
Content-Type: application/json
```

#### Response Body
```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "status": "CANCELLED",
  "cancelledAt": "2026-10-27T19:35:00+07:00"
}
```
