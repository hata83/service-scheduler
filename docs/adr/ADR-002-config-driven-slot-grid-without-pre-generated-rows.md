# ADR-002: Config-Driven Slot Grid Without Pre-Generated Rows

- **Status:** Accepted
- **Date:** 2026-10-23
- **Amended:** 2026-10-24

## Context

Slot availability must stay flexible and must not rely on nightly background batch jobs.

Workshop bays support multiple bay types (`bay_capabilities`). Storing slot configuration per bay type creates conflicting timelines when a single bay supports multiple types.

## Decision

1. **Global Dealership Slot Grid:** Store operating hours, base slot interval (e.g., 30 minutes, 08:00 to 17:30, lunch break 12:00 to 13:00), and max advance booking days (default 7 days) globally per dealership in `dealerships`. Requests beyond this window are rejected.
2. **On-Demand Generation:** Generate the time grid in memory for each availability search from current date/time to `current_date + max_advance_booking_days`. Multi-interval jobs occupy contiguous intervals based on `base_minutes`.
3. **Redis Caching:** Cache availability results per `(dealershipId, date, serviceType)` in Redis with a default 1-hour TTL (configured in dealership settings). Enforce date-level cache eviction on bookings, cancellations, check-in slot adjustments, and database exclusion collisions.

## Reason

A single operational timeline for the dealership eliminates conflicting interval grids across multi-purpose bays. No background generator job means zero missed-run operational risks.

## Consequences

### Positive

- All workshop bays operate on a consistent, unified schedule.
- Changing operating hours or slot intervals touches one row with zero data migrations.
- Ephemeral in-memory generation with Redis caching keeps queries fast and fresh.

### Negative

- Availability queries check both bay reservations and technician qualifications. Redis caching mitigates repeated queries.

### Alternatives Considered

- Pre-generate slot rows per bay at midnight (brittle, misses dynamic changes)
- Slot configuration per bay type (conflicts on multi-purpose bays)
