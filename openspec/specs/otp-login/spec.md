# otp-login Specification

## Purpose
OTP login issues a revocable JWT session in an httpOnly cookie for all four roles and covers profile view, profile edit, and logout after login. Every OTP flow is purpose-typed, idempotency-keyed, and rate-limited.

## Requirements

### Requirement: OTP issuance

The system SHALL issue a single-use six-digit OTP when `POST /api/v1/auth/otp/send` carries a valid E.164 phone number, a purpose type (`REGISTER` or `LOGIN`), and an `X-Idempotency-Key` header. The system SHALL dispatch the code through the Zalo channel stub, which prints the code to the server console in this phase. The system SHALL store the OTP in a purpose-scoped Redis slot with a configurable TTL of 5 minutes by default and expose the code in the response body only in non-production profiles. The system SHALL reject a request without the idempotency key with 400. The system SHALL rate-limit issuance at 5 requests per phone-purpose pair per 10 minutes and 30 per IP per minute, and SHALL respond with 429 beyond the limits.

#### Scenario: Seeded customer phone requests a login OTP
- **WHEN** the seeded customer phone requests a LOGIN-purpose OTP in a non-production profile
- **THEN** the response is 202, the console print happens, and the body echoes the code

#### Scenario: Register purpose requested
- **WHEN** an unregistered phone requests a REGISTER-purpose OTP before the register call
- **THEN** the response is 202 and the code goes out through the Zalo stub

#### Scenario: Malformed phone or unknown type
- **WHEN** the request carries a phone that is not valid E.164, or a type outside REGISTER/LOGIN
- **THEN** the response is 400 with an `application/problem+json` body

#### Scenario: Rate limited
- **WHEN** the phone-purpose pair exceeds 5 requests inside 10 minutes, or the IP exceeds 30 requests per minute
- **THEN** the response is 429 with an `application/problem+json` body

### Requirement: OTP issuance replay

The system SHALL cache the send response for 24 hours under the record `idempotency:otp-send:{phone}:{header-value}`, so one header value never collides across endpoints or phones. The recommended key pattern is `otp-send-{TYPE}-{phone}` and Phase 1 clients use it verbatim; a client sending a new value starts a genuinely fresh send. A replayed send returns the cached 202 body, and no second code is generated or dispatched for the replayed key.

#### Scenario: Missing idempotency key on send
- **WHEN** send omits the `X-Idempotency-Key` header
- **THEN** the response is 400 with an `application/problem+json` body and no code is generated

#### Scenario: Replayed send key
- **WHEN** the same send request retries with the same idempotency key, for example `otp-send-LOGIN-84900000004`
- **THEN** the response is the cached 202 body with the same echoed code, and the console print does not happen again

### Requirement: Login by verified OTP

The system SHALL resolve the phone to an existing user, consume the LOGIN-purpose OTP, and set the JWT session in an `httpOnly` cookie named `jwt` when `POST /api/v1/auth/otp/verify` carries the correct code. The response body SHALL carry the resolved user id, role, phone, and active `dealershipId`. When `users.type` is `CUSTOMER`, `role` SHALL be `CUSTOMER` and `dealershipId` SHALL be `null`. When `users.type` is `EMPLOYEE`, `role` (`ADMIN`, `MANAGER`, `TECHNICIAN`) and `dealershipId` (`null` for global `ADMIN`, local `dealership_id` for `MANAGER` and `TECHNICIAN`) SHALL resolve from `employees`. Verify SHALL never create a user or profile row. The system SHALL reject a verify request without the idempotency key with 400, and SHALL limit verify to 10 requests per IP per minute with 429 beyond it. The system SHALL respond with 401 for a wrong code, an expired code, an unregistered phone, or an OTP issued for the wrong purpose, and the problem detail SHALL name registration for an unregistered phone.

#### Scenario: Each seeded role logs in
- **WHEN** each seeded account (customer, manager, technician, admin) verifies its LOGIN-purpose OTP
- **THEN** the response is 200, sets an `httpOnly` cookie, and the body carries the correct role for that role's phone

#### Scenario: Wrong code
- **WHEN** verify receives a well-formed but incorrect code
- **THEN** the response is 401 with an `application/problem+json` body

#### Scenario: Expired code
- **WHEN** verify happens after the OTP TTL has elapsed
- **THEN** the response is 401 with an `application/problem+json` body

#### Scenario: Register-purpose code at login
- **WHEN** verify receives a code that was issued for REGISTER
- **THEN** the response is 401 with an `application/problem+json` body and creates nothing

#### Scenario: Unregistered phone
- **WHEN** verify receives a correct LOGIN code for a phone with no user
- **THEN** the response is 401 and the problem detail names registration as the next step

### Requirement: Verify idempotency

The system SHALL cache the verify response for 24 hours under the record `idempotency:otp-verify:{phone}:{header-value}`, so one header value never collides across endpoints or phones. The recommended key pattern is `otp-verify-{phone}`; a client sending a new value starts a genuinely fresh verification. A replayed verify returns the cached 200 response, including the original session cookie headers, and consumes no second OTP.

#### Scenario: Missing idempotency key on verify
- **WHEN** verify omits the `X-Idempotency-Key` header
- **THEN** the response is 400 with an `application/problem+json` body and consumes nothing

#### Scenario: Replayed verify key
- **WHEN** the same verify request retries with the same idempotency key, for example `otp-verify-84900000004`
- **THEN** the response is the cached 200 body with the same session cookie and no second OTP consumption

### Requirement: Session introspection

The system SHALL return the caller's identity from `GET /api/v1/auth/me` for a valid session cookie, and SHALL respond with 401 without one. The identity carries the user id, role, phone, dealershipId, email, and the centralized `profiles` first and last names for every role (`CUSTOMER`, `ADMIN`, `MANAGER`, `TECHNICIAN`).

#### Scenario: Identity after login
- **WHEN** the client calls me with the cookie from a fresh login
- **THEN** the response is 200 and carries that account's identity and centralized profile fields

#### Scenario: No cookie
- **WHEN** the client calls me with no session cookie
- **THEN** the response is 401 with an `application/problem+json` body

### Requirement: Profile edit after login

The system SHALL apply `PATCH /api/v1/auth/me` with an `X-Idempotency-Key` to the caller's editable details: email on `users` and centralized `profiles` first/last names for every role (`CUSTOMER`, `ADMIN`, `MANAGER`, `TECHNICIAN`). The phone is the login identity and SHALL never be editable; a patch body that carries a phone field SHALL fail with 400.

#### Scenario: Customer edits profile names
- **WHEN** a logged-in customer patches new first and last names with an idempotency key
- **THEN** the response is 200 and a following me call returns the same names

#### Scenario: Phone edit rejected
- **WHEN** the patch body includes a phone field
- **THEN** the response is 400 with an `application/problem+json` body and no field changes

#### Scenario: Staff role edits centralized profile names
- **WHEN** a logged-in ADMIN, MANAGER, or TECHNICIAN patches first and last names
- **THEN** the response is 200 and updates the centralized profile row for that employee

### Requirement: Logout revocation

The system SHALL delete the session's Redis record and clear the cookie on `POST /api/v1/auth/logout`, and SHALL respond 401 to that cookie afterwards.

#### Scenario: Revoked session is rejected
- **WHEN** the client calls me with a cookie revoked by logout
- **THEN** the response is 401 with an `application/problem+json` body
