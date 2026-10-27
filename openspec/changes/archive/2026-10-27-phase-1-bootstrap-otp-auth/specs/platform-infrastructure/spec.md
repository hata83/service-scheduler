# Spec Delta

## Purpose

Platform infrastructure proves the runtime: Docker Compose topology with PostgreSQL and Redis, PostgreSQL-only persistence with no H2, a bootable Spring Boot skeleton with tracing, JSON logging, and monitoring, healthy DB and Redis connectivity, and the five seed fixtures.

## ADDED Requirements

### Requirement: Platform composition and health

The system SHALL run with Docker Compose services for PostgreSQL 16 and Redis 7 for dev and local use, and the application SHALL report `UP` health at `GET /actuator/health` with the `db` and `redis` components `UP`.

#### Scenario: Health proves connectivity
- **WHEN** the application boots against the compose stack
- **THEN** `GET /actuator/health` returns 200 with status `UP` and components `db` and `redis` present as `UP`

#### Scenario: Compose stack is ready
- **WHEN** the stack starts with the compose file
- **THEN** both service healthchecks pass before the application is accepted as up

### Requirement: PostgreSQL-only persistence

The system SHALL treat PostgreSQL as the only SQL engine: no H2 dependency, no H2 configuration, and no in-memory SQL provider anywhere in the build or profiles. Integration tests SHALL run against PostgreSQL via Testcontainers, and E2E suites SHALL run with the PostgreSQL from Docker Compose.

#### Scenario: Dev and local run on compose
- **WHEN** the application starts in the default profile
- **THEN** it connects to the PostgreSQL and Redis services from Docker Compose and Flyway applies the schema

#### Scenario: Integration tests use Testcontainers
- **WHEN** the `integrationTest` suite runs
- **THEN** it provisions a PostgreSQL 16 container, applies the migrations, and reports no H2 involvement

#### Scenario: No H2 in the build
- **WHEN** a dependency inspection lists the server module's runtime classed dependencies
- **THEN** no H2 artifact appears

### Requirement: Observability

The system SHALL emit JSON logs carrying `requestId` and trace correlation fields, expose Prometheus metrics at `GET /actuator/prometheus` including JVM meter families, and expose only the `health` and `prometheus` actuator endpoints. Trace export SHALL be disabled by default while trace ids still enrich logs.

#### Scenario: Structured log line
- **WHEN** any request is served
- **THEN** its log record is a single JSON object containing `requestId` and trace fields

#### Scenario: Metrics scrape
- **WHEN** a client scrapes `GET /actuator/prometheus`
- **THEN** the response is 200 `text/plain` and includes JVM meter families

#### Scenario: Actuator lockdown
- **WHEN** a client requests any actuator endpoint other than health or prometheus
- **THEN** the response is 404 with an `application/problem+json` body

### Requirement: Seed fixtures

The system SHALL seed 1 demo dealership, 4 global `users` rows (`type` in `CUSTOMER` or `EMPLOYEE`) with fixed phones, 4 centralized `profiles` rows (one per user), 3 `employees` rows (`role` in `ADMIN`, `MANAGER`, `TECHNICIAN`; `ADMIN` global with `dealership_id IS NULL`, `MANAGER` and `TECHNICIAN` local with `dealership_id` referencing the demo dealership), 1 `technicians` row with career `grade`, and `technician_skills` rows with per-skill `level`. Seed migrations SHALL not duplicate rows when reapplied on a preserved database.

#### Scenario: Seed accounts resolve
- **WHEN** the integration test runs the migrations on PostgreSQL
- **THEN** queries find 1 dealership row, 4 global user rows (`type` in `CUSTOMER` or `EMPLOYEE`), 4 centralized profile rows, 3 employee rows (`ADMIN` global, `MANAGER` and `TECHNICIAN` referencing the demo dealership), 1 technician row with career `grade`, and 2 technician skill rows with skill `level`

#### Scenario: Migration re-run stays single
- **WHEN** the migration set applies again against an already-seeded database
- **THEN** the user count does not change

## Mapping

### API Mapping

| Endpoint | Request | Response | Bruno files |
|---|---|---|---|
| `GET /actuator/health` | none | 200 `{status:"UP", components:{db:{status:"UP"}, redis:{status:"UP"}}}`; 503 when a component is down | `api/platform/health-db-redis.bru` |
| `GET /actuator/prometheus` | none | 200 `text/plain` with metric families | `api/platform/prometheus-metrics.bru` |

### Capacity Mapping

| Requirement | Playwright spec | Archify diagram |
|---|---|---|
| Platform composition and health | `platform-infrastructure.spec.ts` | `docs/diagrams/system/system-architecture.html` |
| PostgreSQL-only persistence | `platform-infrastructure.spec.ts` | `docs/diagrams/system/system-data-model.html` |
| Observability | `platform-infrastructure.spec.ts` | `docs/diagrams/system/system-architecture.html` |
| Seed fixtures | `platform-infrastructure.spec.ts` | `docs/diagrams/system/system-data-model.html` |
