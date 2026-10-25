# ADR-003: PostgreSQL EXCLUDE Constraint for Booking Overlap

- **Status:** Accepted, superseded in part by [ADR-012](ADR-012-service-job-owns-the-bay-reservation.md)
- **Date:** 2026-10-23

## Context

Two booking requests can race for the same bay and time window.

## Decision

Add EXCLUDE USING gist with bay_id equal and tstzrange of start_time and end_time overlapping to the APPOINTMENTS table.

> AMENDMENT (2026-10-24): the constraint now sits on SERVICE_JOBS, not APPOINTMENTS. One appointment can hold several jobs, and each job reserves its own bay for its own window. See ADR-012 for the reason. The decision to let the database own the overlap rule is unchanged.

## Reason

The database enforces the overlap rule. The project needs no application lock. The constraint fires at commit time. The error maps to HTTP 409.

## Consequences

### Positive

The database enforces the overlap rule. The project needs no application lock. The constraint fires at commit time. The error maps to HTTP 409.

### Negative

Production requires PostgreSQL. H2 in dev and CI does not support gist exclusion. Concurrent booking tests use Testcontainers.

### Alternatives Considered

- Pessimistic row locks on the bay
- Serializable transaction isolation
- An application-level mutex

