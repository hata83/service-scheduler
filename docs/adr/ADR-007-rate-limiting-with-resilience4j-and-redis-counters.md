# ADR-007: Rate Limiting with Resilience4j and Redis Counters

- **Status:** Accepted
- **Date:** 2026-10-23

## Context

The unauthenticated booking endpoints are open to abuse.

## Decision

Apply the rate limit per IP address and per authenticated user. Count with INCR and set the window with EXPIRE in Redis.

## Reason

The Redis counters are shared across pods. Resilience4j integrates with Spring Boot.

## Consequences

### Positive

The Redis counters are shared across pods. Resilience4j integrates with Spring Boot.

### Negative

Redis adds about 1 millisecond to each rate-limited request. The cost is acceptable.

### Alternatives Considered

- A rate limit at the API gateway only
- A local in-memory limiter per pod

