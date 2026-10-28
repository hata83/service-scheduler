# sse-notifications Specification

## Purpose
SSE notifications stream real-time booking confirmation and schedule events to connected customer and manager sessions with audit logging.

## Requirements

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
- **THEN** system dispatches `appointment.confirmed` event over the customer's open SSE stream with payload containing `id`, `appointmentId`, and `status`

### Requirement: Communication audit log

The system SHALL record dispatched notifications in `communication_logs` and expose history via `GET /api/v1/notifications/my`.

#### Scenario: Query notification history
- **WHEN** authenticated customer requests `GET /api/v1/notifications/my`
- **THEN** response status is 200 OK and body carries dispatched notification records
