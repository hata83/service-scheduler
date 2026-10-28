# customer-booking Specification

## Purpose
Customer booking allows vehicle owners to book workshop appointments with automatic confirmation and prevents concurrent scheduling overlaps.

## Requirements

### Requirement: Customer appointment booking

The system SHALL accept `POST /api/v1/appointments` with mandatory `X-Idempotency-Key` to create a booking.

#### Scenario: Successful booking creation
- **WHEN** authenticated customer sends valid booking payload with `X-Idempotency-Key`
- **THEN** response status is 201 Created and body contains appointment record

#### Scenario: Missing idempotency key
- **WHEN** client sends booking request without `X-Idempotency-Key` header
- **THEN** response status is 400 Bad Request with an `application/problem+json` body

### Requirement: Multi-job non-overlap validation

The system SHALL require that all service job time slots within an appointment do not overlap each other.

#### Scenario: Reject overlapping service jobs within appointment
- **WHEN** customer books an appointment where two service jobs have overlapping time slots
- **THEN** response status is 400 Bad Request with an `application/problem+json` body

#### Scenario: Accept consecutive non-overlapping service jobs
- **WHEN** customer books an appointment with two service jobs in consecutive non-overlapping time slots
- **THEN** response status is 201 Created and body contains the appointment with both service jobs

### Requirement: Service job duration base limit

The system SHALL require that each service job time slot duration does not exceed the service type base duration.

#### Scenario: Reject service job duration exceeding base duration
- **WHEN** customer books a service job whose duration exceeds the service type estimated minutes
- **THEN** response status is 400 Bad Request with an `application/problem+json` body

#### Scenario: Accept service job duration within base duration
- **WHEN** customer books service jobs whose durations do not exceed service type estimated minutes
- **THEN** response status is 201 Created

### Requirement: Automatic booking confirmation

The system SHALL read dealership `auto_confirm_appointments` and set initial status to `CONFIRMED` when true or `REQUESTED` when false.

#### Scenario: Dealership enables auto confirm
- **WHEN** customer books an appointment at a dealership with auto-confirm enabled
- **THEN** created appointment carries `CONFIRMED` status

#### Scenario: Dealership disables auto confirm
- **WHEN** customer books an appointment at a dealership with auto-confirm disabled
- **THEN** created appointment carries `REQUESTED` status

### Requirement: Concurrency overlap prevention

The system SHALL enforce database exclusion constraints preventing overlapping vehicle VIN or workshop bay allocations.

#### Scenario: Conflicting vehicle VIN overlap
- **WHEN** client books an appointment for a vehicle VIN that already has an overlapping active appointment
- **THEN** response status is 409 Conflict with an `application/problem+json` body

#### Scenario: Conflicting workshop bay overlap
- **WHEN** client books a service job into a workshop bay that already has an overlapping active job
- **THEN** response status is 409 Conflict with an `application/problem+json` body

### Requirement: Idempotency replay protection

The system SHALL return the cached response without re-executing logic when a client replays an `X-Idempotency-Key`.

#### Scenario: Duplicate idempotency key replay
- **WHEN** client resends an identical request with a previously processed `X-Idempotency-Key`
- **THEN** response status is 201 Created with the identical cached body
