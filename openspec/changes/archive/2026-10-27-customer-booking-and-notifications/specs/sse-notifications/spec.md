# Spec Delta: sse-notifications

## Purpose

SSE notifications stream real-time booking confirmation and schedule events to connected customer and manager sessions with audit logging.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/notifications/stream` | GET | `service-scheduler-server/src/test/bruno/api/notification/notifications-stream.bru` | Open real-time SSE stream |
| `/api/v1/notifications/my` | GET | `service-scheduler-server/src/test/bruno/api/notification/notifications-my.bru` | Get notification history |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Real-time SSE stream | `service-scheduler-ui/tests/e2e/sse-notifications.spec.ts` | `docs/diagrams/system/sse-notification.html` |
| Transactional outbox event broadcast | `service-scheduler-ui/tests/e2e/sse-notifications.spec.ts` | `docs/diagrams/system/sse-notification.html` |
| Communication audit log | `service-scheduler-ui/tests/e2e/sse-notifications.spec.ts` | `docs/diagrams/system/sse-notification.html` |

## ADDED Requirements

### Requirement: Real-time SSE stream

The system SHALL provide `GET /api/v1/notifications/stream` authenticated via `httpOnly` JWT session cookie. Exactly one active stream SHALL exist per user phone number.

#### Scenario: Open valid SSE connection
- **WHEN** client opens `GET /api/v1/notifications/stream` with valid credentials
- **THEN** response status is 200 OK and Content-Type is `text/event-stream`

#### Scenario: Reconnect evicts prior stream
- **WHEN** an authenticated user opens a second SSE connection
- **THEN** system terminates the previous connection and registers the new stream

### Requirement: Transactional outbox event broadcast

The system SHALL persist domain events in `outbox_events` and emit `appointment.confirmed` to the customer SSE stream after commit.

#### Scenario: Broadcast auto-confirmed appointment event
- **WHEN** a customer creates an auto-confirmed appointment
- **THEN** system dispatches `appointment.confirmed` event over the customer's open SSE stream

### Requirement: Communication audit log

The system SHALL record dispatched notifications in `communication_logs` and expose history via `GET /api/v1/notifications/my`.

#### Scenario: Query notification history
- **WHEN** authenticated customer requests `GET /api/v1/notifications/my`
- **THEN** response status is 200 OK and body carries dispatched notification records

## Endpoint Contracts

### 1. `GET /api/v1/notifications/stream`

#### Request Headers
```http
Accept: text/event-stream
Cookie: jwt=eyJhbGciOiJIUzI1NiJ9...
```

#### Request Body
```
(empty)
```

#### Response Headers
```http
HTTP/1.1 200 OK
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache
Connection: keep-alive
```

#### Response Body
```
event: appointment.confirmed
data: {"appointmentId":"11111111-2222-3333-4444-555555555555","status":"CONFIRMED","dealershipId":"00000000-0000-0000-0000-000000000001"}

: ping
```

### 2. `GET /api/v1/notifications/my`

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
    "id": "77777777-8888-9999-0000-111111111111",
    "channel": "SSE",
    "eventType": "appointment.confirmed",
    "deliveryStatus": "DELIVERED",
    "dispatchedAt": "2026-10-27T19:30:00+07:00"
  }
]
```
