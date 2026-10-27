# slot-availability-compute Specification

## Purpose

Compute available workshop time slots from live database state and cache the result.
The system must evaluate bay capacity and technician availability for each candidate slot.

## Requirements

### Requirement: Slot grid computation on cache miss

The system SHALL compute the available slot grid when no cached grid exists for `slot:grid:{dealershipId}:{serviceTypeId}:{date}`.

#### Scenario: Compute and cache on miss
- **GIVEN** no cached slot grid for the requested parameters
- **WHEN** client requests available slots
- **THEN** the system computes the grid from the 5-step algorithm
- **AND** stores the result in Redis with TTL from `dealership.slotCacheTtlSeconds`
- **AND** returns the computed slots with HTTP 200

### Requirement: Bay gate passes when a free compatible bay exists

The system SHALL include a candidate slot when at least one active bay supporting a required bay type has no overlapping active service job.

#### Scenario: Bay gate passes
- **GIVEN** a bay of a compatible type has no booking in the candidate window
- **WHEN** the slot grid is computed
- **THEN** the slot passes the bay gate

#### Scenario: Bay gate fails when all compatible bays are occupied
- **GIVEN** all bays of compatible types have overlapping active service jobs
- **WHEN** the slot grid is computed
- **THEN** the slot is excluded from the result

### Requirement: Technician gate passes when all role slots can be filled

The system SHALL include a slot when every required technician role slot in `service_type_technician_requirements` can be filled by a distinct available technician.

#### Scenario: Technician gate passes
- **GIVEN** qualified unassigned technicians exist for every required role slot
- **WHEN** scarcest-slot greedy matching runs
- **THEN** the slot passes the technician gate

#### Scenario: Technician gate fails when a role slot cannot be filled
- **GIVEN** no available technician meets the required skill and level for one role slot
- **WHEN** scarcest-slot greedy matching runs
- **THEN** the slot is excluded from the result

### Requirement: Slots that do not cover the full service duration are excluded

The system SHALL exclude any candidate slot where `slot_start + estimatedMinutes > close_time`.

#### Scenario: Slot too short for service duration
- **GIVEN** a 120-minute service type
- **AND** a candidate slot starts at 16:30 with close time 17:30
- **WHEN** the grid is computed
- **THEN** that slot is excluded because 16:30 + 120 min exceeds 17:30

### Requirement: Out-of-range date returns HTTP 400

The system SHALL return HTTP 400 Problem Details when `date > current_date + dealership.maxAdvanceBookingDays`.

#### Scenario: Date beyond max advance booking window
- **GIVEN** `maxAdvanceBookingDays` is 7
- **WHEN** client requests slots for a date 8 or more days in the future
- **THEN** response status is 400 with `application/problem+json`

### Requirement: Past dates return an empty slot list

The system SHALL return an empty list for `date < current_date`.

#### Scenario: Past date query
- **GIVEN** client requests slots for yesterday
- **WHEN** the grid is computed
- **THEN** response status is 200 and body contains an empty list

### Requirement: Cache hit returns stored grid without recomputation

The system SHALL return the cached grid directly when `slot:grid:{dealershipId}:{serviceTypeId}:{date}` exists.

#### Scenario: Cache hit returns stored result
- **GIVEN** a cached grid exists for the requested parameters
- **WHEN** client requests available slots
- **THEN** response status is 200 and the body contains the cached slots
