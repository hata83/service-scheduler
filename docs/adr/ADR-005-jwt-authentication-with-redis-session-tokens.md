# ADR-005: JWT Authentication with Redis Session Tokens

- **Status:** Accepted
- **Date:** 2026-10-23

## Context

Three user roles need different access scopes. A session must be invalidatable on logout.

## Decision

Issue a stateless HS256 JWT in an `httpOnly` cookie with `sub`, `role`, `ph`, `did` (active dealership id), and `jti` claims. Dealership scope resolves from `employees`: `null` for global `CUSTOMER` (non-employee) and global `ADMIN` (`employees.dealership_id IS NULL`), and the local `employees.dealership_id` for `MANAGER` and `TECHNICIAN`. Logout clears the client cookie (`Max-Age=0`).

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

