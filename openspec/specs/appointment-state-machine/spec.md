# appointment-state-machine Specification

## Purpose
Appointment state machine enforces lifecycle status transitions and restricts cancellation strictly to `pre-check-in` stages.

## Requirements

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
