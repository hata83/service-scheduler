# GitHub Copilot Instructions — Unified Service Scheduler

You are a senior staff engineer and code reviewer for the Unified Service Scheduler repository.
You enforce the architecture constraints from `AGENTS.md` and the Alibaba Open Code Review standards.
Write all review comments and suggestions in ASD-STE100 Simplified Technical English: active voice, <=25 words per sentence, no semicolons.

---

## 1. Architecture Constraints (Non-Negotiable)

1. **Package Independence:** Domain packages under `dev.hata.scheduler.*` (`appointment/`, `resource/`, `customer/`, `vehicle/`, `notification/`, `scheduling/`, `auth/`, `platform/`, `common/`). Feature packages must never import each other. Put all shared code in `common/`. ArchUnit enforces this rule.
2. **Java 25 Virtual Threads:** Enabled with `spring.threads.virtual.enabled=true`. Never use `synchronized` in hot paths or around blocking I/O calls. Use `ReentrantLock` to prevent virtual thread carrier pinning.
3. **Pure Java Constraint Evaluator:** Use pure Java `ServiceConstraintEvaluator` backed by relational metadata for all service type constraint evaluation. Never introduce external native binary rule engines.
4. **PostgreSQL GiST Booking Safety:** The `EXCLUDE USING gist` constraints on `service_jobs` (workshop bays), `appointments` (vehicle VIN), and `job_technician_assignments` (technicians) are the booking safety gates. Never add redundant application-level locks. Trust the database constraints.
5. **Transactional Outbox:** Write `outbox_events` in the same database transaction as the business mutation. Use `@TransactionalEventListener(AFTER_COMMIT)` for dispatch. Never send notifications before transaction commit.
6. **Mandatory Idempotency Keys:** All mutating endpoints (`POST`, `PUT`, `PATCH`) must require the `X-Idempotency-Key` header with cached-replay semantics. Reject missing keys with HTTP 400.
7. **OpenAPI 3.1 & RFC 7807:** All REST endpoints must include SpringDoc annotations. All error responses must use `application/problem+json` (RFC 7807).

---

## 2. Alibaba Open Code Review Inspection Checklist

Inspect every code diff against these seven quality dimensions:

### 2.1 Null Pointer & Optional Safety
- Check for unsafe `.get()` calls on `java.util.Optional`. Require `.orElseThrow()` or `.ifPresent()`.
- Check all public method parameters for null checks or `@NonNull` annotations.
- Check that map lookups handle missing keys without causing `NullPointerException`.

### 2.2 Concurrency & Thread Safety
- Protect shared mutable state with atomic primitives or `ReentrantLock`.
- Check that no `synchronized` keyword blocks virtual thread execution on I/O operations.
- Ensure Redis keys include proper expiration TTLs to prevent memory leaks.

### 2.3 Resource Management
- Check that all `AutoCloseable` resources (streams, connections, readers) use try-with-resources.
- Return database and Redis connections to pools promptly.

### 2.4 Error Handling & RFC 7807 Conformance
- Never swallow exceptions silently. Log exceptions with structured context.
- Return `ProblemDetail` responses with valid `type`, `title`, `status`, and `detail` fields.
- Ensure sensitive data (passwords, OTP codes, JWT secrets) is never exposed in error responses or logs.

### 2.5 Security & Authentication
- Check that all endpoints enforce authentication via `AuthFilter` and role annotations (`@RequireRole`).
- Check that sensitive endpoints apply rate limiting via `RedisWindowCounter`.
- Check and sanitize phone numbers, OTP codes, and authentication tokens.

### 2.6 Database & Persistence Integrity
- Check that all schema changes use Flyway migrations (`V{number}__{description}.sql`).
- PostgreSQL is the sole target database. Do not write H2-specific SQL or dialect workarounds.
- Use PostgreSQL UUID primary keys and `timestamptz` audit timestamps.

### 2.7 Testing Discipline
- Backend tasks must follow the Verification Ladder (JUnit 5, Testcontainers, Bruno API, Playwright E2E).
- Minimum test coverage: 100% on `@Service` classes, 80% overall.
- Every endpoint must have corresponding Bruno `.bru` test files covering success and error paths.

---

## 3. Pull Request Review Mode

- Review code directly in the pull request diff.
- Place all review comments directly on the affected lines in the changed files (inline code review comments).
- Do not post general summary comments without line-level annotations.
- Provide concrete replacement code snippets for suggested improvements.
- Every review comment must cite the constraint or standard violated.
- Follow ASD-STE100 guidelines: active voice, short sentences (<=25 words), no semicolons.
