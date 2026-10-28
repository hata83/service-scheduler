# Spec Delta: customer-booking

## Purpose

Customer booking allows vehicle owners to book workshop appointments with automatic confirmation and prevents concurrent scheduling overlaps.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/appointments` | POST | `service-scheduler-server/src/test/bruno/api/appointment/appointment-create.bru` | Book single-job appointment |
| `/api/v1/appointments` | POST | `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-multi-job.bru` | Book appointment with two service types |
| `/api/v1/appointments` | POST | `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-duration-exceeded.bru` | Reject job duration exceeding base duration |
| `/api/v1/appointments` | POST | `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-intra-job-overlap.bru` | Reject intra-appointment overlapping jobs |
| `/api/v1/appointments` | POST | `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-overlap-conflict.bru` | Reject overlapping vehicle appointment via DB constraint |
| `/api/v1/appointments` | POST | `service-scheduler-server/src/test/bruno/api/appointment/appointment-create-duplicate-key.bru` | Idempotency replay |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Customer appointment booking | `service-scheduler-ui/tests/e2e/customer-booking.spec.ts` | `docs/diagrams/system/booking-sequence.html` |
| Multi-job non-overlap validation | `service-scheduler-ui/tests/e2e/customer-booking.spec.ts` | `docs/diagrams/system/booking-sequence.html` |
| Service job duration base limit | `service-scheduler-ui/tests/e2e/customer-booking.spec.ts` | `docs/diagrams/system/booking-sequence.html` |
| Automatic booking confirmation | `service-scheduler-ui/tests/e2e/customer-booking.spec.ts` | `docs/diagrams/system/booking-sequence.html` |
| Concurrency overlap prevention | `service-scheduler-ui/tests/e2e/customer-booking.spec.ts` | `docs/diagrams/system/booking-sequence.html` |
| Idempotency replay protection | `service-scheduler-ui/tests/e2e/customer-booking.spec.ts` | `docs/diagrams/system/booking-sequence.html` |

## ADDED Requirements

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

## Endpoint Contracts

### 1. `POST /api/v1/appointments`

#### Request Headers
```http
Content-Type: application/json
Accept: application/json
Cookie: jwt=eyJhbGciOiJIUzI1NiJ9...
X-Idempotency-Key: aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
```

#### Request Body
```json
{
  "dealershipId": "00000000-0000-0000-0000-000000000001",
  "vehicleCategoryId": "11111111-0000-0000-0000-000000000001",
  "vehicleVin": "1HGBH41JXMN109186",
  "serviceJobs": [
    {
      "serviceTypeId": "22222222-0000-0000-0000-000000000001",
      "bayId": "33333333-0000-0000-0000-000000000001",
      "startTime": "2026-10-28T08:00:00+07:00",
      "endTime": "2026-10-28T08:30:00+07:00"
    },
    {
      "serviceTypeId": "22222222-0000-0000-0000-000000000002",
      "bayId": "33333333-0000-0000-0000-000000000002",
      "startTime": "2026-10-28T08:30:00+07:00",
      "endTime": "2026-10-28T09:30:00+07:00"
    }
  ]
}
```

#### Response Headers
```http
HTTP/1.1 201 Created
Content-Type: application/json
```

#### Response Body
```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "dealershipId": "00000000-0000-0000-0000-000000000001",
  "customerId": "00000000-0000-0000-0000-000000000002",
  "vehicleCategoryId": "11111111-0000-0000-0000-000000000001",
  "vehicleVin": "1HGBH41JXMN109186",
  "startTime": "2026-10-28T08:00:00+07:00",
  "endTime": "2026-10-28T09:30:00+07:00",
  "status": "CONFIRMED",
  "estimateCost": 210.00,
  "totalCost": null,
  "serviceJobs": [
    {
      "id": "22222222-3333-4444-5555-666666666666",
      "serviceTypeId": "22222222-0000-0000-0000-000000000001",
      "bayId": "33333333-0000-0000-0000-000000000001",
      "startTime": "2026-10-28T08:00:00+07:00",
      "endTime": "2026-10-28T08:30:00+07:00",
      "status": "PENDING",
      "baseMinutes": 30,
      "estimateCost": 60.00
    },
    {
      "id": "33333333-4444-5555-6666-777777777777",
      "serviceTypeId": "22222222-0000-0000-0000-000000000002",
      "bayId": "33333333-0000-0000-0000-000000000002",
      "startTime": "2026-10-28T08:30:00+07:00",
      "endTime": "2026-10-28T09:30:00+07:00",
      "status": "PENDING",
      "baseMinutes": 60,
      "estimateCost": 150.00
    }
  ],
  "createdAt": "2026-10-27T19:30:00+07:00"
}
```
