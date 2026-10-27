# workshop-seed-data Specification

## Purpose
Workshop seed data populates database fixtures for metadata, confirmed appointments on upcoming days, and Redis slot availability grids.

## Requirements

### Requirement: Database metadata seed fixtures

The system SHALL seed demo vehicle categories, workshop bays, service types, and pricing matrices via repeatable Flyway migrations.

#### Scenario: Seeded metadata resolves
- **WHEN** the application applies migrations on PostgreSQL
- **THEN** database queries find demo vehicle categories, workshop bays, service types, and pricing rows

### Requirement: Confirmed booking seed fixtures

The system SHALL seed existing appointments with `CONFIRMED` status on next day 1, day 2, and day 3.

#### Scenario: Seeded confirmed bookings resolve
- **WHEN** the application boots with seed migrations on PostgreSQL
- **THEN** database queries find appointments with `CONFIRMED` status on next days 1, 2, and 3

### Requirement: Redis slot cache fixtures

The system SHALL populate demo slot availability grids in Redis reflecting `CONFIRMED` bookings and remaining open slots.

#### Scenario: Seeded slot cache reflects bookings
- **WHEN** client reads available slots for days with seeded bookings
- **THEN** returned slot grid shows booked windows as unavailable and remaining windows as available
