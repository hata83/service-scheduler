# Technical Design: Customer Booking and SSE Notifications

## Context

The Unified Service Scheduler completed platform infrastructure and workshop metadata with demo slot availability caches. Customers now require an end-to-end booking flow: booking an appointment, receiving real-time confirmation via SSE, viewing their booking history, and cancelling prior to `check-in`. PostgreSQL GIST exclusion constraints serve as the definitive concurrency boundary for vehicle and workshop bay bookings.

See [proposal.md](proposal.md) for background and motivation.

## Goals / Non-Goals

### Goals
- Implement customer booking endpoint `POST /api/v1/appointments` requiring `X-Idempotency-Key`.
- Support multi-job appointments for a vehicle VIN across multiple service types.
- Require that service jobs within an appointment do not overlap in time.
- Require that each service job time slot duration does not exceed the service type base duration.
- Support dealership `auto_confirm_appointments` configuration to move bookings to `CONFIRMED` automatically.
- Enforce PostgreSQL GIST exclusion constraints (`no_vehicle_overlap` and `no_bay_overlap`) and map violations to HTTP 409 Conflict.
- Support idempotency replay by returning cached 201 responses from Redis.
- Expose customer appointment history via `GET /api/v1/appointments` and details via `GET /api/v1/appointments/{id}`.
- Enforce state machine transitions allowing cancellation (`DELETE /api/v1/appointments/{id}`) before `check-in` only (`REQUESTED` and `CONFIRMED`).
- Reject post-`check-in` cancellations with HTTP 422 Problem Details.
- Implement real-time SSE stream `GET /api/v1/notifications/stream` with direct `httpOnly` cookie auth and one active stream per phone.
- Stage domain events in `outbox_events` and emit SSE alerts via `@TransactionalEventListener(AFTER_COMMIT)`.
- Record delivery in `communication_logs` and expose history via `GET /api/v1/notifications/my`.

### Non-Goals
- Dynamic constraint calculation engine: `SlotAvailabilityService` remains a stub demo cache reader for this phase.
- Manager walk-in appointment registration: deferred to manager schedule phase.
- Technician job status progression (`IN_PROGRESS -> DONE`): deferred to technician phase.
- Final invoice generation and settlement: deferred to billing phase.

## Technical Decisions

### Decision 1: Feature Package Independence and Decoupling Ports
- **Choice**: Place appointment logic in `appointment/` and notification logic in `notification/`. Cross-domain lookups (dealership auto-confirm, customer profile validation) use `@FunctionalInterface` provider ports in `common.provider`.
- **Rationale**: Complies with ADR-009 and ArchUnit package independence rules. Controllers only access local feature services.
- **Alternatives Considered**: Direct cross-package service injection (violates ArchUnit).

### Decision 2: PostgreSQL GIST Exclusion Constraints for Concurrency Safety
- **Choice**: Rely on PostgreSQL `EXCLUDE USING gist` constraints (`no_vehicle_overlap` and `no_bay_overlap`) rather than application-level locks.
- **Rationale**: Complies with ADR-003 and ADR-012. The database acts as the single source of truth for concurrency. When an exclusion collision occurs, the exception handler returns an RFC 7807 response with HTTP 409 Conflict. It also evicts the date slot grid in Redis.
- **Alternatives Considered**: Distributed Redis locks (risks split-brain, deadlock, and clock drift).

### Decision 3: Transactional Outbox Pattern for Real-Time SSE Alerts
- **Choice**: Insert `outbox_events` records inside the primary database transaction alongside appointments. An `@TransactionalEventListener(AFTER_COMMIT)` listener dispatches the event to the connected customer SSE emitter and logs to `communication_logs`.
- **Rationale**: Complies with ADR-004. Guarantees events publish only when database transactions commit successfully.
- **Alternatives Considered**: Synchronous dispatch inside transaction (risks sending notifications for rolled-back bookings).

### Decision 4: Single SSE Stream per Phone with Heartbeats
- **Choice**: The server tracks active `SseEmitter` instances by phone number. A new connection automatically completes and evicts any existing connection for that phone number. A scheduled executor sends `: ping` heartbeats every 25 seconds.
- **Rationale**: Prevents resource leaks on mobile network transitions and cleans up zombie client connections.
- **Alternatives Considered**: Unlimited streams per user (exhausts server file descriptors and memory).

### Decision 5: Pre-`check-in` State Machine Gate
- **Choice**: Cancellation (`DELETE /api/v1/appointments/{id}`) requires that current status is in `[REQUESTED, CONFIRMED]`. If current status is `CHECKED_IN` or later, the service throws `ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ...)`.
- **Rationale**: Section 9.1 and Section 14.1 prohibit cancellation once a vehicle arrives for `check-in` at the dealership. The manager can only declare `NO_SHOW` if the deal collapses.
- **Alternatives Considered**: Allow cancellation at any time (violates workshop shop-floor execution model).

### Decision 6: Intra-Appointment Multi-Job Non-Overlap and Duration Bounds
- **Choice**: Enforce that all service jobs for the vehicle VIN have consecutive non-overlapping time ranges. Sort jobs chronologically and reject overlapping intervals with HTTP 400 Bad Request. For each job, require that `Duration.between(startTime, endTime).toMinutes()` does not exceed `ServiceType.estimatedMinutes`. Reject violations with HTTP 400 Bad Request.
- **Rationale**: Follows Section 14.1 multi-job dependency rules. A single vehicle cannot occupy multiple service bays simultaneously. Time slots cannot exceed the manufacturer base service duration.
- **Alternatives Considered**: Permit overlapping jobs on the same vehicle (physically impossible in the workshop).

## Traceability

| Capability | Endpoint / Mechanism | Bruno Test File | Playwright E2E Spec | Archify Diagram |
|---|---|---|---|---|
| `customer-booking` | `POST /api/v1/appointments` | `src/test/bruno/api/appointment/appointment-create.bru` | `customer-booking.spec.ts` | [Booking Sequence](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/booking-sequence.html) |
| `customer-booking` | `POST /api/v1/appointments` (multi-job) | `src/test/bruno/api/appointment/appointment-create-multi-job.bru` | `customer-booking.spec.ts` | [Booking Sequence](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/booking-sequence.html) |
| `customer-booking` | `POST /api/v1/appointments` (duration exceeded) | `src/test/bruno/api/appointment/appointment-create-duration-exceeded.bru` | `customer-booking.spec.ts` | [Booking Sequence](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/booking-sequence.html) |
| `customer-booking` | `POST /api/v1/appointments` (intra-job overlap) | `src/test/bruno/api/appointment/appointment-create-intra-job-overlap.bru` | `customer-booking.spec.ts` | [Booking Sequence](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/booking-sequence.html) |
| `customer-booking` | `POST /api/v1/appointments` (duplicate key) | `src/test/bruno/api/appointment/appointment-create-duplicate-key.bru` | `customer-booking.spec.ts` | [Booking Sequence](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/booking-sequence.html) |
| `customer-booking` | `POST /api/v1/appointments` (overlap conflict) | `src/test/bruno/api/appointment/appointment-create-overlap-conflict.bru` | `customer-booking.spec.ts` | [Booking Sequence](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/booking-sequence.html) |
| `appointment-history` | `GET /api/v1/appointments` | `src/test/bruno/api/appointment/appointments-list-my.bru` | `appointment-history.spec.ts` | [Booking Sequence](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/booking-sequence.html) |
| `appointment-history` | `GET /api/v1/appointments/{id}` | `src/test/bruno/api/appointment/appointment-get-by-id.bru` | `appointment-history.spec.ts` | [Booking Sequence](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/booking-sequence.html) |
| `appointment-state-machine` | `DELETE /api/v1/appointments/{id}` | `src/test/bruno/api/appointment/appointment-cancel.bru` | `appointment-state-machine.spec.ts` | [Appointment Lifecycle](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/appointment-lifecycle.html) |
| `appointment-state-machine` | `DELETE /api/v1/appointments/{id}` (rejected) | `src/test/bruno/api/appointment/appointment-cancel-post-checkin-rejected.bru` | `appointment-state-machine.spec.ts` | [Appointment Lifecycle](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/appointment-lifecycle.html) |
| `sse-notifications` | `GET /api/v1/notifications/stream` | `src/test/bruno/api/notification/notifications-stream.bru` | `sse-notifications.spec.ts` | [SSE Notification](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/sse-notification.html) |
| `sse-notifications` | `GET /api/v1/notifications/my` | `src/test/bruno/api/notification/notifications-my.bru` | `sse-notifications.spec.ts` | [SSE Notification](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/sse-notification.html) |

## Risks / Trade-offs

- **[Risk] High-concurrency booking collisions on the same bay or vehicle**:
  - *Mitigation*: PostgreSQL GIST exclusion constraints reject overlapping reservations. Global exception handler catches `DataIntegrityViolationException`, translates it to HTTP 409 Conflict, and invalidates Redis slot grids for that date.
- **[Risk] Intra-appointment service job overlaps on a single vehicle**:
  - *Mitigation*: The service sorts job ranges chronologically during booking and rejects overlapping slots with HTTP 400 Bad Request.
- **[Risk] Service job duration exceeds estimated base duration**:
  - *Mitigation*: The service compares job duration against `ServiceType.estimatedMinutes` and rejects excessive durations with HTTP 400 Bad Request.
- **[Risk] Client disconnects silently during SSE streaming**:
  - *Mitigation*: The server sends periodic heartbeat comments (`: ping`) every 25 seconds and removes dead emitters on IO error or after 60 seconds of inactivity.
- **[Risk] Replayed booking requests create duplicate appointments**:
  - *Mitigation*: `IdempotencyFilter` inspects `idempotency:POST:/api/v1/appointments:{key}` in Redis. If found, it returns the cached response immediately. The database column `appointments.idempotency_key` has a UNIQUE constraint as fallback.
