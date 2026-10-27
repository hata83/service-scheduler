# workshop-metadata Specification

## Purpose
Workshop metadata exposes dealership directories, supported vehicle categories, service catalogs, and instant cost calculations for workshop services.

## Requirements

### Requirement: Dealership directory

The system SHALL return all active dealerships via `GET /api/v1/dealerships`.

#### Scenario: Active dealerships query
- **WHEN** client sends a request to `GET /api/v1/dealerships`
- **THEN** response status is 200 and body contains active dealership profiles

### Requirement: Vehicle categories query

The system SHALL return supported vehicle categories for a dealership via `GET /api/v1/dealerships/{id}/vehicle-categories`. Each item SHALL provide `brand`, `model`, and `type`. The table SHALL NOT store vehicle VIN values.

#### Scenario: Valid dealership categories query
- **WHEN** client sends a request to `GET /api/v1/dealerships/{id}/vehicle-categories`
- **THEN** response status is 200 and body contains list of supported vehicle categories

#### Scenario: Unknown dealership
- **WHEN** client requests categories for an unknown dealership ID
- **THEN** response status is 404 with an `application/problem+json` body

### Requirement: Service types catalog

The system SHALL return active service types for a dealership via `GET /api/v1/dealerships/{id}/service-types`.

#### Scenario: Valid service types query
- **WHEN** client requests service types for a valid dealership ID
- **THEN** response status is 200 and body lists active service types with estimated duration

### Requirement: Instant cost estimation

The system SHALL calculate the total estimated cost via `GET /api/v1/dealerships/{id}/pricing/estimate` for a vehicle category and selected service types.

#### Scenario: Valid cost estimation
- **WHEN** client supplies valid dealershipId, categoryId, and serviceTypeIds
- **THEN** response status is 200 and body carries itemized costs and total estimate

#### Scenario: Missing pricing mapping
- **WHEN** a service type has no configured cost for the requested vehicle category
- **THEN** response status is 400 with an `application/problem+json` body
