# slot-availability-cache Specification

## Purpose
Slot availability cache provides fast lookups of workshop time windows from Redis cache key grids.

## Requirements

### Requirement: Cached slot availability query

The system SHALL read candidate available time slots from Redis cache key `slot:grid:{dealershipId}:{date}:{serviceTypeId}` via `GET /api/v1/slots/available`.

#### Scenario: Cache hit for slot availability
- **WHEN** client requests available slots for an existing cached grid
- **THEN** response status is 200 and body contains available time slots

### Requirement: Slot cache miss handling

The system SHALL return an empty slot list when the requested slot grid key is missing from Redis cache.

#### Scenario: Cache miss for slot availability
- **WHEN** no cached slot grid exists for the requested parameters
- **THEN** response status is 200 and body contains an empty slot list
