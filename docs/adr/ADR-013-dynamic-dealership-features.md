# ADR-013: Dynamic Dealership Features and Rate Limits in JSONB

- **Status:** Accepted
- **Date:** 2026-10-26
- **Project:** Unified Service Scheduler

---

## Context

Static configuration in YAML files applied the same rate limits to all dealerships. Dealerships operate with different staffing levels, traffic volumes, and abuse profiles. Modifying static properties required application restarts or redeployments.

The system needed a way to store per-dealership feature configurations and rate limits without schema churn.

---

## Decision

Add a `features` JSONB column to the `dealerships` table.

1. The `dealerships.features` column defaults to `{}`.
2. The seed migration sets default rate limits inside the demo dealership row.
3. The `DealershipService` parses the JSON features document with Jackson.
4. If a dealership record lacks a features document or specific keys, the system falls back to Java default values.
5. The `DealershipFeaturesProvider` interface in `common` exposes feature lookups to other domains without creating direct dependencies.

---

## Reason

JSONB columns store flexible operational settings inside PostgreSQL. Dealership managers can update settings dynamically through database records or admin endpoints without code redeployments. Relational metadata stays queryable and auditable.

---

## Consequences

### Positive

- Dealerships can customize rate limits and feature flags independently.
- Operational updates take effect immediately without service restarts.
- New feature flags do not require database schema migrations.
- The `DealershipFeaturesProvider` keeps domain packages decoupled.

### Negative

- Invalid JSON payloads require graceful application fallbacks.
- Querying deeply nested JSON keys requires PostgreSQL JSON operators.

---

## Alternatives Considered

- Dedicated relational tables for each feature setting.
- Environment variables per tenant.
- External configuration service.
