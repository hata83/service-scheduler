## ADDED Requirements

### Requirement: R-SLOT-NPLUS1

Slot grid computation MUST load dealership bays, the day's non-cancelled
service jobs, qualified technicians per requirement, and assignments exactly
once per grid, then check overlaps in memory.

#### Scenario: single load per grid

Given a grid with multiple candidate slots,
when the grid is computed,
then `bayRepository` and `technicianAvailability` are each queried once.

### Requirement: R-SLOT-DST

Candidate generation MUST resolve candidate times in the dealership timezone
and reject slots whose resolved elapsed duration differs from the service
duration.

#### Scenario: spring-forward gap

Given operating hours that span a DST spring-forward transition,
when a candidate slot crosses the missing hour,
then no zero-length slot is emitted.

### Requirement: R-TECHNICIAN-EXCLUDE

The `job_technician_assignments` table MUST enforce an atomic overlap
exclusion on active assignments.

#### Scenario: only one overlapping active assignment commits

Given two concurrent transactions inserting overlapping active technician
assignments for the same technician,
when both commit,
then the database rejects the second insert.
