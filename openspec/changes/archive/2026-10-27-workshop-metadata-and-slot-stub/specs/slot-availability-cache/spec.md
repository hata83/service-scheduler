# Spec Delta: slot-availability-cache

## Purpose

Slot availability cache provides fast lookups of workshop time windows from Redis cache key grids.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/slots/available` | GET | `service-scheduler-server/src/test/bruno/api/metadata/slots-available-cached.bru` | Query available time slots from cache |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Cached slot availability query | `service-scheduler-ui/tests/e2e/slot-availability-cache.spec.ts` | `docs/diagrams/system/metadata-management.html` |
| Slot cache miss handling | `service-scheduler-ui/tests/e2e/slot-availability-cache.spec.ts` | `docs/diagrams/system/metadata-management.html` |

## ADDED Requirements

### Requirement: Cached slot availability query

The system SHALL read candidate available time slots from Redis cache key `slot:grid:{dealershipId}:{serviceTypeId}:{date}` via `GET /api/v1/slots/available`.

#### Scenario: Cache hit for slot availability
- **WHEN** client requests available slots for an existing cached grid
- **THEN** response status is 200 and body contains available time slots

### Requirement: Slot cache miss handling

The system SHALL return an empty slot list when the requested slot grid key is missing from Redis cache.

#### Scenario: Cache miss for slot availability
- **WHEN** no cached slot grid exists for the requested parameters
- **THEN** response status is 200 and body contains an empty slot list

## Endpoint Contracts

### 1. `GET /api/v1/slots/available?dealershipId={dealershipId}&serviceTypeId={serviceTypeId}&date={YYYY-MM-DD}`

#### Request Headers
```http
Accept: application/json
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
    "startTime": "2026-10-28T08:00:00+07:00",
    "endTime": "2026-10-28T09:00:00+07:00",
    "available": false
  },
  {
    "startTime": "2026-10-28T09:00:00+07:00",
    "endTime": "2026-10-28T10:00:00+07:00",
    "available": true
  }
]
```
