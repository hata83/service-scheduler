# customer-registration Specification

## Purpose
Customer registration by phone submits profile info plus a REGISTER-purpose OTP in one idempotency-keyed, rate-limited call: the backend verifies the OTP, inserts the user and profile, and creates the JWT session, so the register response is the customer's login.

## Requirements

### Requirement: One-shot registration

The system SHALL create a CUSTOMER user and a customer profile, and issue a login session, when `POST /api/v1/auth/register` carries a phone number, profile name fields, a REGISTER-purpose OTP code, and an `X-Idempotency-Key` header. The system SHALL verify the OTP inside the same action: a wrong, expired, or LOGIN-purpose-code submission SHALL fail with 401 and create nothing. A successful response SHALL be 201 with `Set-Cookie: jwt`, so the created customer is logged in at once. The system SHALL limit register to 10 requests per IP per minute and SHALL respond with 429 beyond it.

#### Scenario: New customer registers and is logged in
- **WHEN** an unregistered phone submits profile names, a REGISTER-purpose OTP code, and an idempotency key
- **THEN** the response is 201 with the new user id and `Set-Cookie: jwt`, and a following me call returns the CUSTOMER identity

#### Scenario: Wrong OTP in registration
- **WHEN** registration carries an incorrect or expired OTP code
- **THEN** the response is 401 with an `application/problem+json` body and creates nothing

#### Scenario: Login-purpose code at registration
- **WHEN** registration submits a code that was issued for LOGIN
- **THEN** the response is 401 with an `application/problem+json` body and creates nothing

#### Scenario: Registration rate limited
- **WHEN** an IP exceeds 10 register submissions inside one minute
- **THEN** the response is 429 with an `application/problem+json` body and creates nothing

### Requirement: Idempotent registration

The system SHALL respond 400 when a registration request carries no `X-Idempotency-Key`. The system SHALL cache the first 201 response for 24 hours under the record `idempotency:register:{phone}:{header-value}`, so one header value never collides across endpoints or phones. The recommended key pattern is `register-{phone}`; a client sending a new value starts a genuinely fresh registration. The system SHALL return the cached response on replay without creating a second user and without consuming a second OTP.

#### Scenario: Missing header
- **WHEN** registration omits the `X-Idempotency-Key` header
- **THEN** the response is 400 with an `application/problem+json` body and creates nothing

#### Scenario: Replayed key
- **WHEN** the same registration body retries with the same idempotency key, for example `register-84900000999`
- **THEN** the response is the cached 201 body with the same session, the user count does not change, and no second OTP consumption happens

### Requirement: Role pinning

The system SHALL create every self-registration with the CUSTOMER role. The system SHALL reject a registration request that carries a role field with 403.

#### Scenario: No self-elevation
- **WHEN** a registration request includes a role field with the value ADMIN
- **THEN** the response is 403 with an `application/problem+json` body and creates nothing

### Requirement: Duplicate rejection

The system SHALL reject registration of a phone that already belongs to a user with 409. The seeded admin, manager, technician, and customer are existing users, so they cannot register again; an already-registered phone uses the login flow instead.

#### Scenario: Existing phone registers
- **WHEN** registration uses the seeded manager phone
- **THEN** the response is 409 with an `application/problem+json` body and creates nothing
