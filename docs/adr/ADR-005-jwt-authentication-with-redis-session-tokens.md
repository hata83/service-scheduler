# ADR-005: JWT Authentication with Redis Session Tokens

- **Status:** Accepted
- **Date:** 2026-10-23

## Context

Three user roles need different access scopes. A session must be invalidatable on logout.

## Decision

Issue a JWT Bearer token with role and dealershipId claims. Store the token in Redis with a 24-hour TTL. A logout deletes the Redis key.

## Reason

The JWT keeps the API call stateless. Redis supports invalidation. A JWT alone cannot be revoked without a blacklist.

## Consequences

### Positive

The JWT keeps the API call stateless. Redis supports invalidation. A JWT alone cannot be revoked without a blacklist.

### Negative

Redis becomes a required infrastructure dependency. The project already needs Redis for the slot cache, rate limiting, and idempotency keys.

### Alternatives Considered

- Server-side sessions only
- An OAuth2 authorization server with an identity provider

