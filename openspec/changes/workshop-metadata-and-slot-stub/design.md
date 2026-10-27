# Technical Design: Workshop Metadata and Slot Grid Stub

## Context

Customers need workshop metadata to prepare for service booking. This includes dealership details, vehicle categories, service offerings, instant pricing estimates, and available time slots. See proposal.md for motivation and problem background.

## Goals / Non-Goals

### Goals
- Deliver Flyway schema for `vehicle_categories`, `workshop_bays`, `service_types`, `service_type_technician_requirements`, and `service_type_costs`.
- Deliver Flyway schema for `appointments`, `service_jobs`, and `job_technician_assignments` with PostgreSQL exclusion constraints.
- Provide vehicle categories with `brand`, `model`, and `type` attributes.
- Ensure vehicle categories omit vehicle VIN attributes.
- Provide REST endpoints for dealerships, vehicle categories, service types, and pricing estimation.
- Provide a stubbed slot availability endpoint reading directly from Redis cache key `slot:grid:{dealershipId}:{serviceTypeId}:{date}`.
- Seed demo metadata, test slot grids, and sample `CONFIRMED` appointments for next days 1, 2, and 3.

### Non-Goals
- Implement dynamic dual resource gating constraint engine in this phase.
- Implement appointment booking API (`POST /api/v1/appointments`) in this phase.
- Implement customer notifications or outbox processors in this phase.

## Decisions

### Decision 1: Vehicle Categories with Brand, Model, and Type
The system replaces `vehicles` with `vehicle_categories`.
- **Rationale**: Dealerships configure supported vehicle classifications by brand, model, and category type. Individual vehicle VINs only appear during appointment booking.
- **Alternatives Considered**: Keeping a customer-specific vehicles table with VIN. Rejected because customers supply vehicle VIN during booking.

### Decision 2: Stubbed Slot Availability via Redis Cache
The slot query checks Redis key `slot:grid:{dealershipId}:{serviceTypeId}:{date}` directly.
- **Rationale**: Isolates metadata and catalog delivery from the complex constraint matching engine. Allows frontend and API contracts to stabilize first.
- **Alternatives Considered**: In-memory hardcoded slot generator. Rejected because Redis mirrors the production caching architecture.

### Decision 3: Seed Existing Bookings with `CONFIRMED` Status
The seed data provides appointments with `CONFIRMED` status on next days 1, 2, and 3.
- **Rationale**: Proves schedule visibility and allows the cached slot grid to demonstrate booked versus free slots realistically.
- **Alternatives Considered**: Seeding an entirely empty workshop schedule. Rejected because empty schedules fail to exercise busy slot handling.

### Decision 4: Pricing Estimation Service
The pricing service calculates total cost using relational mappings in `service_type_costs`.
- **Rationale**: Ensures deterministic pricing based on vehicle category and selected service types.
- **Alternatives Considered**: Client-side calculation. Rejected because server must control authoritative pricing rules.

## Risks / Trade-offs

- **[Risk]** Cache miss on slot availability returns an empty list.
  - **Mitigation**: Pre-populate demo slot grid keys during test setup and seed routines.
- **[Risk]** Missing pricing matrix entries for selected category.
  - **Mitigation**: Return RFC 7807 problem details with HTTP 400 when cost mappings are missing.

## Architecture Constraints (AGENTS.md Section 4)

1. **Model the domain**: Entities in `dealership/`, `vehicle/`, and `scheduling/` encapsulate state and behavior.
2. **Keep packages independent**: Feature packages (`dealership`, `vehicle`, `resource`) do not import each other. Cross-feature communication uses DTOs and common ports.
3. **OpenAPI 3.1 specification**: All endpoints declare SpringDoc annotations and return RFC 7807 problem details on errors.
4. **Idempotency keys**: Read endpoints do not require idempotency keys. Write endpoints enforce `X-Idempotency-Key`.

## Traceability

| Capability | API Endpoint | Bruno Contract File | Playwright Spec | Archify Diagram |
|---|---|---|---|---|
| `workshop-metadata` | `GET /api/v1/dealerships` | `service-scheduler-server/src/test/bruno/api/metadata/dealerships-list.bru` | `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` | [Metadata Management](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/metadata-management.html) |
| `workshop-metadata` | `GET /api/v1/dealerships/{id}/vehicle-categories` | `service-scheduler-server/src/test/bruno/api/metadata/vehicle-categories-list.bru` | `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` | [Metadata Management](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/metadata-management.html) |
| `workshop-metadata` | `GET /api/v1/dealerships/{id}/service-types` | `service-scheduler-server/src/test/bruno/api/metadata/service-types-list.bru` | `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` | [Metadata Management](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/metadata-management.html) |
| `workshop-metadata` | `GET /api/v1/dealerships/{id}/pricing/estimate` | `service-scheduler-server/src/test/bruno/api/metadata/pricing-estimate.bru` | `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` | [Metadata Management](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/metadata-management.html) |
| `slot-availability-cache` | `GET /api/v1/slots/available` | `service-scheduler-server/src/test/bruno/api/metadata/slots-available-cached.bru` | `service-scheduler-ui/tests/e2e/slot-availability-cache.spec.ts` | [Metadata Management](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/metadata-management.html) |
| `workshop-seed-data` | `/actuator/health` | `service-scheduler-server/src/test/bruno/api/platform/health-db-redis.bru` | `service-scheduler-ui/tests/e2e/workshop-seed-data.spec.ts` | [System Data Model](file:///C:/Users/Hata/workspace/service-scheduler/docs/diagrams/system/system-data-model.html) |
