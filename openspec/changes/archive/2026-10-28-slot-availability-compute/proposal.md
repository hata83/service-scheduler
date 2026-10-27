# Change Proposal: slot-availability-compute

## Why

Customers and service advisors cannot see real available time slots. The slot service is a stub and returns an empty list.

## Context

The `SlotAvailabilityService#getCachedAvailableSlots` method is a stub.
It reads from Redis and returns an empty list on cache miss.
It does not compute the availability grid from database state.

## Motivation

Customers and service advisors cannot see real available time slots.
The system must compute availability from actual workshop capacity.
The computed grid must respect bay types, technician skills, and service duration.

## Solution Overview

Implement the 5-step availability algorithm from SYSTEM_DESIGN Section 5.4:

1. Generate candidate time slots from dealership operating hours.
2. Filter bays by `allowed_bay_types` with no overlapping active `service_jobs`.
3. Find technicians with required skill and level from `service_type_technician_requirements` with no overlapping `job_technician_assignments`.
4. Apply greedy scarcest-slot matching.
5. Exclude slots where any required technician role slot cannot be filled.

Cache the result at `slot:grid:{dealershipId}:{serviceTypeId}:{date}` with `dealership.slotCacheTtlSeconds` TTL.

Return HTTP 400 when `date > current_date + maxAdvanceBookingDays`.

## New Entities

- `WorkshopBay` entity in `scheduling/model` (maps `workshop_bays` table).
- `ServiceTypeTechnicianRequirement` entity in `scheduling/model`.
- `WorkshopBayRepository` in `scheduling/repository`.
- `ServiceTypeTechnicianRequirementRepository` in `scheduling/repository`.

## New Port

- `TechnicianAvailabilityPort` interface in `common/provider`.
  Decouples `scheduling` from `resource` per ArchUnit package independence rule.
- `TechnicianAvailabilityProvider` implementation in `resource/service`.

## Impact

- `SlotAvailabilityService`: full algorithm replaces empty-list fallback.
- `SlotAvailabilityController`: add HTTP 400 for out-of-range dates.
- No schema changes required. All tables already exist in V2 migration.
