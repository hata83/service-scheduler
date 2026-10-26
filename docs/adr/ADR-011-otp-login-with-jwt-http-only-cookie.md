# ADR-011: OTP Login with a JWT in an httpOnly Cookie

**Status:** Accepted  
**Date:** 2026-10-23  
**Amended:** 2026-10-24, 2026-10-26  
**Project:** Unified Service Scheduler  

---

## Context

The first draft authenticated with a password and expected the client to send `Authorization: Bearer <jwt>`.

Two problems appeared:
- A dealership customer does not have a password before their first visit.
- Storing JWTs in browser `localStorage` exposes them to XSS attacks.

The HIL-1 design rounds for the first change also showed that one OTP endpoint cannot serve two flows. A login code and a registration code are different secrets, so one shared slot lets a login code complete a registration.

---

## Decision

Replace password login with a phone-based OTP login that returns a JWT in an httpOnly cookie.

1. The client sends a phone number and a purpose to `POST /api/v1/auth/otp/send`. The purpose is `REGISTER` or `LOGIN`.
2. The service generates a six-digit code, stores it in a purpose-scoped Redis slot (`otp:login:{phone}` or `otp:register:{phone}`) with a 5-minute TTL, and returns `202 Accepted`.
3. In local development/test, the code prints to the console and the response body echoes it. The prod profile omits the echo.
4. The client sends the code to `POST /api/v1/auth/otp/verify`. The service verifies the code against the LOGIN slot, deletes it from Redis, resolves the user by phone number, and sets a JWT in an `httpOnly`, `Secure`, `SameSite=Strict` cookie.
5. Verify never creates a user or a profile row. A phone number with no existing row answers 401 with a detail that names registration.
6. A new customer registers through `POST /api/v1/auth/register`. The call carries the phone, the profile names, and a REGISTER-purpose code. The service verifies the code, inserts the user and the customer profile, and returns `201 Created` with the session cookie. The register response is the customer login.
7. Every OTP slot and every session cookie is single-use. A wrong code never consumes a live code.
8. **SSE Authentication:** The browser opens `EventSource('/api/v1/notifications/stream', { withCredentials: true })`. The browser automatically transmits the `httpOnly` cookie. This eliminates the need for a separate pre-auth ticket endpoint.

The service stays stateless. The JWT carries user id, phone, role, and active dealership id (`null` for global `CUSTOMER` and `ADMIN`, and the local `employees.dealership_id` for `MANAGER` and `TECHNICIAN`). Personal profile attributes (`first_name`, `last_name`, `locale`) are centralized in `profiles` for all four roles.

---

## Reason

The httpOnly cookie prevents XSS attacks from reading the JWT. Direct cookie authentication for SSE eliminates an extra HTTP round-trip and avoids storing ephemeral tickets in Redis. Phone numbers provide natural customer identity for automotive appointments.

---

## Consequences

### Positive

- No password storage, reset, or breach surface.
- The browser holds a token no script can read.
- Zero Redis overhead for SSE tickets.
- SSE streams authenticate directly via existing cookie.

### Negative

- Cross-origin frontends require explicit CORS credentials configuration (`Access-Control-Allow-Credentials: true`).
- Phone numbers become an identity key; phone number changes require an administrative recovery path.

---

## Related

- `docs/SYSTEM_DESIGN.md` §3.1, §3.3, §10.5, §11
- `docs/adr/ADR-005-jwt-authentication-with-redis-session-tokens.md`
- `docs/adr/ADR-007-rate-limiting-with-resilience4j-and-redis-counters.md`
- OTP Login diagram: `docs/diagrams/system/otp-login.html`
