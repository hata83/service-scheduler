# slot-availability-compute Specification

## Purpose
Compute available workshop time slots from live database state and cache the result.
The system must evaluate bay capacity and technician availability for each candidate slot.

## Requirements

### Requirement: Slot grid computation on cache miss

The system SHALL compute the available slot grid when no cached grid exists for `slot:grid:{dealershipId}:{date}:{serviceTypeId}`.

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

### Requirement: Completed or cancelled jobs release bays and technicians

The bay and technician gates SHALL ignore service jobs whose status is `CANCELLED` or `DONE`.

#### Scenario: Done job frees its bay
- **GIVEN** a service job on a bay with status `DONE`
- **WHEN** a candidate slot overlaps that job's time window
- **THEN** the bay passes the bay gate (the past job does not block it)

#### Scenario: Done job frees its technician
- **GIVEN** a technician assignment attached to a `DONE` service job
- **WHEN** a candidate slot overlaps the assignment window
- **THEN** the technician is available for the technician gate

### Requirement: Technician gate passes when all role slots can be filled

The system SHALL include a slot when every required technician role slot in `service_type_technician_requirements` can be filled by a distinct available technician.

#### Scenario: Technician gate passes
- **GIVEN** qualified unassigned technicians exist for every required role slot
- **WHEN** augmenting-path bipartite matching runs
- **THEN** the slot passes the technician gate

#### Scenario: Technician gate fails when a role slot cannot be filled
- **GIVEN** no available technician meets the required skill and level for one role slot
- **WHEN** augmenting-path matching runs
- **THEN** the slot is excluded from the result

### Requirement: Matching finds distinct technicians across overlapping candidate sets

The system SHALL assign a distinct technician to each role slot using augmenting-path matching, so a slot is included even when greedy first-fit assignment would exhaust shared candidates.

#### Scenario: Overlapping candidate sets still match
- **GIVEN** role slots with candidate sets {T1,T2}, {T2,T3}, {T1,T2}
- **WHEN** the technician gate evaluates the slot
- **THEN** the slot is included because T1,T2,T3 can fill the three role slots distinctly

### Requirement: Slots that do not cover the full service duration are excluded

The system SHALL exclude any candidate slot where `slot_start + estimatedMinutes > close_time`.

#### Scenario: Slot too short for service duration
- **GIVEN** a 120-minute service type
- **AND** a candidate slot starts at 16:30 with close time 17:30
- **WHEN** the grid is computed
- **THEN** that slot is excluded because 16:30 + 120 min exceeds 17:30

### Requirement: Slots that wrap past midnight are excluded

The system SHALL not list a candidate slot whose start time plus the service duration crosses midnight.

#### Scenario: Long service starting late is excluded
- **GIVEN** a 120-minute service type and a close time of 23:30
- **WHEN** the cursor reaches 22:00 and the end time would be 00:00 (next day)
- **THEN** generation stops and no wrapping slot is listed

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

The system SHALL return the cached grid directly when `slot:grid:{dealershipId}:{date}:{serviceTypeId}` exists.

#### Scenario: Cache hit returns stored result
- **GIVEN** a cached grid exists for the requested parameters
- **WHEN** client requests available slots
- **THEN** response status is 200 and the body contains the cached slots

#### Scenario: Expired slots are purged on cache hit
- **GIVEN** a cached grid contains a slot whose start time has already elapsed
- **WHEN** client requests available slots and the grid is returned from cache
- **THEN** the elapsed slot is removed from the response

### Requirement: Service type must belong to the requested dealership and be active

The system SHALL not compute or cache slots for a service type that belongs to another dealership or is inactive.

#### Scenario: Foreign service type is rejected
- **GIVEN** a service type owned by a different dealership
- **WHEN** client requests available slots for that service type
- **THEN** response status is 200 and the body is an empty list

#### Scenario: Inactive service type is rejected
- **GIVEN** an inactive service type
- **WHEN** client requests available slots for that service type
- **THEN** response status is 200 and the body is an empty list

### Requirement: Cache write failures do not affect the response

The system SHALL log the exception with a stack trace and return the computed slots even when Redis write fails.

#### Scenario: Redis write failure is non-fatal
- **GIVEN** the compute grid is ready and Redis write throws
- **WHEN** client requests available slots
- **THEN** response status is 200 and the body contains the computed slots
