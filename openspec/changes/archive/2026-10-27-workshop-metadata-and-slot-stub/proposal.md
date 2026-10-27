# Proposal: Workshop Metadata and Slot Grid Stub

## Why

Customers need metadata to book appointments. Customers must select dealerships, vehicle categories, and service types. Customers must also see instant cost estimates and available time slots. Providing metadata endpoints and seed data unblocks the customer booking workflow.

## What Changes

- Add `vehicle_categories` schema with `brand`, `model`, and `type` attributes.
- Ensure vehicle categories omit individual vehicle VIN attributes.
- Add workshop metadata schema: `workshop_bays`, `service_types`, `service_type_technician_requirements`, and `service_type_costs`.
- Add appointment tables: `appointments`, `service_jobs`, and `job_technician_assignments` with PostgreSQL exclusion constraints.
- Provide repeatable seed migrations for demo categories, bays, service types, technician requirements, and pricing matrices.
- Seed existing appointments with `CONFIRMED` status on next day 1, day 2, and day 3.
- Provide `GET /api/v1/dealerships` to list active workshop facilities.
- Provide `GET /api/v1/dealerships/{id}/vehicle-categories` to list supported vehicle categories.
- Provide `GET /api/v1/dealerships/{id}/service-types` to list active service offerings.
- Provide `GET /api/v1/dealerships/{id}/pricing/estimate` to calculate instant cost estimates.
- Provide `GET /api/v1/slots/available` stubbed to read time windows from Redis cache.
- Populate demo slot availability grids in Redis reflecting `CONFIRMED` bookings and remaining slots.

## Capabilities

### New Capabilities
- `workshop-metadata`: Delivers dealership selection, vehicle categories, service catalog, and instant pricing calculations.
- `slot-availability-cache`: Delivers cached slot availability queries reading candidate time slots from Redis.
- `workshop-seed-data`: Delivers repeatable database migrations and Redis fixtures with `CONFIRMED` bookings on days 1, 2, and 3.

### Modified Capabilities
None. This change leaves archived specifications unchanged.

## Verification Ladder Mapping

This change follows the backend-first verification ladder in AGENTS.md Section 5:
- **BE-1 / BE-2**: Write failing JUnit 5 repository and service tests before code implementation.
- **BE-3**: Enforce Spotless code formatting.
- **BE-4**: Verify slot grid cache retrieval and pricing calculations in integration tests.
- **BE-5**: Add Bruno `.bru` contract files for all 5 endpoints.
- **BE-6**: Achieve 100% test coverage on new service classes and 80% overall.
- **BE-7**: Run Alibaba OCR code review delegation.
- **FE-1 to FE-5**: Provide MSW handlers, component integration, and Playwright E2E coverage.

## Delivered Artifacts

### Bruno API Test Files
- `service-scheduler-server/src/test/bruno/api/metadata/dealerships-list.bru`
- `service-scheduler-server/src/test/bruno/api/metadata/vehicle-categories-list.bru`
- `service-scheduler-server/src/test/bruno/api/metadata/service-types-list.bru`
- `service-scheduler-server/src/test/bruno/api/metadata/pricing-estimate.bru`
- `service-scheduler-server/src/test/bruno/api/metadata/slots-available-cached.bru`

### Playwright E2E Specs
- `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts`
- `service-scheduler-ui/tests/e2e/slot-availability-cache.spec.ts`
- `service-scheduler-ui/tests/e2e/workshop-seed-data.spec.ts`

### Archify Diagrams
- `docs/diagrams/system/system-data-model.html` (erd)
- `docs/diagrams/system/metadata-management.html` (architecture)
