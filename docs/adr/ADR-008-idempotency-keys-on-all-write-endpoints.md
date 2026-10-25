# ADR-008: Idempotency Keys on All Write Endpoints

- **Status:** Accepted
- **Date:** 2026-10-23
- **Amended:** 2026-10-24

## Context

A network retry creates a duplicate booking when the API has no idempotency protection.

## Decision

Require the `X-Idempotency-Key` header with a client-generated UUID v4 on every write endpoint.

1. **Dual Storage:**
   - **Fast Path:** Cache the JSON response in Redis (`idempotency:{key}`) with a 24-hour TTL.
   - **Durable Path:** Persist the key in the database `appointments.idempotency_key` column with a `UNIQUE NOT NULL` constraint.
2. **Business Overlap Guard:**
   - Alongside network idempotency, protect the vehicle against concurrent bookings using PostgreSQL `EXCLUDE USING gist`:
   - No single vehicle (`vehicle_vin`) may have two active appointments in overlapping time windows.

## Reason

Client retries must be safe. Caching in Redis provides sub-millisecond replay. Persisting the key with a database unique constraint guarantees safety even if Redis evicts keys.

## Consequences

### Positive

- Retries produce identical responses without side effects.
- The database enforces uniqueness even during cache failure.
- Vehicles cannot be double-booked across concurrent requests.

### Negative

- The client must generate and maintain a UUID v4 for each write request.

### Alternatives Considered

- A deduplication check on customer and time window only (brittle under concurrent edits)
- Redis cache only without database persistence (vulnerable to cache eviction)
