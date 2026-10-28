# Spec Delta: appointment-history

## Purpose

Appointment history allows authenticated customers to view their historical and upcoming appointments with real-time status and itemized job details.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/appointments` | GET | `service-scheduler-server/src/test/bruno/api/appointment/appointments-list-my.bru` | List customer appointments |
| `/api/v1/appointments/{id}` | GET | `service-scheduler-server/src/test/bruno/api/appointment/appointment-get-by-id.bru` | Get appointment details |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Customer appointments list | `service-scheduler-ui/tests/e2e/appointment-history.spec.ts` | `docs/diagrams/system/booking-sequence.html` |
| Appointment details query | `service-scheduler-ui/tests/e2e/appointment-history.spec.ts` | `docs/diagrams/system/booking-sequence.html` |
| Cross-customer access restriction | `service-scheduler-ui/tests/e2e/appointment-history.spec.ts` | `docs/diagrams/system/booking-sequence.html` |

## ADDED Requirements

### Requirement: Customer appointments list

The system SHALL return all appointments owned by the authenticated customer via `GET /api/v1/appointments` sorted by start time descending.

#### Scenario: List customer appointments
- **WHEN** authenticated customer requests `GET /api/v1/appointments`
- **THEN** response status is 200 OK and body contains an array of customer appointments

### Requirement: Appointment details query

The system SHALL return full appointment and service job details via `GET /api/v1/appointments/{id}` for an owned appointment.

#### Scenario: View appointment details
- **WHEN** customer requests `GET /api/v1/appointments/{id}` for an appointment they own
- **THEN** response status is 200 OK and body contains appointment status, vehicle, and service jobs

### Requirement: Cross-customer access restriction

The system SHALL reject access with HTTP 404 Problem Details when a customer requests an appointment owned by another user.

#### Scenario: Unauthorized appointment access
- **WHEN** customer requests `GET /api/v1/appointments/{id}` for an appointment belonging to another customer
- **THEN** response status is 404 Not Found with an `application/problem+json` body

## Endpoint Contracts

### 1. `GET /api/v1/appointments`

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
[
  {
    "id": "11111111-2222-3333-4444-555555555555",
    "dealershipId": "00000000-0000-0000-0000-000000000001",
    "vehicleVin": "1HGBH41JXMN109186",
    "startTime": "2026-10-28T08:00:00+07:00",
    "endTime": "2026-10-28T09:00:00+07:00",
    "status": "CONFIRMED",
    "estimateCost": 50.00,
    "totalCost": null,
    "createdAt": "2026-10-27T19:30:00+07:00"
  }
]
```

### 2. `GET /api/v1/appointments/{id}`

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
  "dealershipId": "00000000-0000-0000-0000-000000000001",
  "customerId": "00000000-0000-0000-0000-000000000002",
  "vehicleCategoryId": "00000000-0000-0000-0000-000000000101",
  "vehicleVin": "1HGBH41JXMN109186",
  "startTime": "2026-10-28T08:00:00+07:00",
  "endTime": "2026-10-28T09:00:00+07:00",
  "status": "CONFIRMED",
  "estimateCost": 50.00,
  "totalCost": null,
  "serviceJobs": [
    {
      "id": "22222222-3333-4444-5555-666666666666",
      "serviceTypeId": "00000000-0000-0000-0000-000000000301",
      "bayId": "00000000-0000-0000-0000-000000000201",
      "startTime": "2026-10-28T08:00:00+07:00",
      "endTime": "2026-10-28T09:00:00+07:00",
      "status": "PENDING",
      "estimateCost": 50.00
    }
  ],
  "createdAt": "2026-10-27T19:30:00+07:00"
}
```
