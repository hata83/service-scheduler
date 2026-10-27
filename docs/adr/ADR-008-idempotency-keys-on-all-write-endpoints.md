# ADR-008: Idempotency Keys on All Write Endpoints

- **Status:** Accepted
- **Date:** 2026-10-23
- **Amended:** 2026-10-24, 2026-10-26

## Context

A network retry creates a duplicate booking when the API has no idempotency protection.

The HIL-1 design rounds for the first change also showed that the auth endpoints need the same protection. A retried register call must not create two customers, and a retried OTP send must not dispatch two codes.

## Decision

Require the `X-Idempotency-Key` header with a client-generated key on every write endpoint. There are no exemptions.

1. **Namespaced Record:** The cache record is `idempotency:{endpoint}:{phone}:{header-value}`. The endpoint and the phone keep one header value from colliding across endpoints or phones. The recommended value is a phone-derived pattern: `otp-send-{TYPE}-{phone}`, `otp-verify-{phone}`, `register-{phone}`. A client that sends a new value starts a genuinely fresh operation.
2. **Dual Storage:**
   - **Fast Path:** Cache the JSON response in Redis with a 24-hour TTL.
   - **Durable Path:** Persist the key in the database `appointments.idempotency_key` column with a `UNIQUE NOT NULL` constraint.
3. **Replay Semantics:**
   - `POST /api/v1/auth/otp/send` returns the cached 202 body and dispatches no second code.
   - `POST /api/v1/auth/otp/verify` returns the cached 200 body with the original session cookie and consumes no second OTP.
   - `POST /api/v1/auth/register` returns the cached 201 body and creates no second user.
   - `PATCH /api/v1/auth/me` returns the cached patch body.
4. **Missing Header:** A write without the header answers 400 with an `application/problem+json` body.
5. **Business Overlap Guard:**
   - Alongside network idempotency, protect the vehicle against concurrent bookings using PostgreSQL `EXCLUDE USING gist`:
   - No single vehicle (`vehicle_vin`) may have two active appointments in overlapping time windows.

## Reason

Client retries must be safe. Caching in Redis provides sub-millisecond replay. Persisting the key with a database unique constraint guarantees safety even if Redis evicts keys. The namespaced record keeps one client key from replaying the wrong endpoint.

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
