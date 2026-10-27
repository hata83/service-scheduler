# Spec Delta: workshop-metadata

## Purpose

Workshop metadata exposes dealership directories, supported vehicle categories, service catalogs, and instant cost calculations for workshop services.

## Mapping

### API Mapping

| Endpoint | Method | Contract / Bruno File | Description |
|---|---|---|---|
| `/api/v1/dealerships` | GET | `service-scheduler-server/src/test/bruno/api/metadata/dealerships-list.bru` | List active dealerships |
| `/api/v1/dealerships/{id}/vehicle-categories` | GET | `service-scheduler-server/src/test/bruno/api/metadata/vehicle-categories-list.bru` | List supported vehicle categories |
| `/api/v1/dealerships/{id}/service-types` | GET | `service-scheduler-server/src/test/bruno/api/metadata/service-types-list.bru` | List active service types |
| `/api/v1/dealerships/{id}/pricing/estimate` | GET | `service-scheduler-server/src/test/bruno/api/metadata/pricing-estimate.bru` | Calculate instant cost estimate |

### Capacity Mapping

| Requirement | Playwright Spec | Archify Diagram |
|---|---|---|
| Dealership directory | `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` | `docs/diagrams/system/metadata-management.html` |
| Vehicle categories query | `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` | `docs/diagrams/system/metadata-management.html` |
| Service types catalog | `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` | `docs/diagrams/system/metadata-management.html` |
| Instant cost estimation | `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` | `docs/diagrams/system/metadata-management.html` |

## ADDED Requirements

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

## Endpoint Contracts

### 1. `GET /api/v1/dealerships`

#### Request Headers
```http
Accept: application/json
```

#### Request Body
```
(empty)
```

#### Response Headers
```http
HTTP/1.1 200 OK
Content-Type: application/json
```

#### Response Body
```json
[
  {
    "id": "00000000-0000-0000-0000-000000000001",
    "name": "Hata Demo Service Center",
    "address": "1 Le Loi",
    "city": "Ho Chi Minh City",
    "country": "Vietnam",
    "timezone": "Asia/Ho_Chi_Minh",
    "slotIntervalMinutes": 30,
    "openTime": "08:00:00",
    "closeTime": "17:30:00"
  }
]
```

### 2. `GET /api/v1/dealerships/{id}/vehicle-categories`

#### Request Headers
```http
Accept: application/json
```

#### Request Body
```
(empty)
```

#### Response Headers
```http
HTTP/1.1 200 OK
Content-Type: application/json
```

#### Response Body
```json
[
  {
    "id": "11111111-0000-0000-0000-000000000001",
    "dealershipId": "00000000-0000-0000-0000-000000000001",
    "brand": "Toyota",
    "model": "Camry",
    "type": "SEDAN"
  },
  {
    "id": "11111111-0000-0000-0000-000000000002",
    "dealershipId": "00000000-0000-0000-0000-000000000001",
    "brand": "Toyota",
    "model": "RAV4",
    "type": "SUV"
  }
]
```

### 3. `GET /api/v1/dealerships/{id}/service-types`

#### Request Headers
```http
Accept: application/json
```

#### Request Body
```
(empty)
```

#### Response Headers
```http
HTTP/1.1 200 OK
Content-Type: application/json
```

#### Response Body
```json
[
  {
    "id": "22222222-0000-0000-0000-000000000001",
    "dealershipId": "00000000-0000-0000-0000-000000000001",
    "code": "STANDARD_SERVICE",
    "name": "Standard Scheduled Service",
    "description": "Routine scheduled service inspection and fluid top-up",
    "estimatedMinutes": 60
  }
]
```

### 4. `GET /api/v1/dealerships/{id}/pricing/estimate?categoryId={categoryId}&serviceTypeIds={id1},{id2}`

#### Request Headers
```http
Accept: application/json
```

#### Request Body
```
(empty)
```

#### Response Headers
```http
HTTP/1.1 200 OK
Content-Type: application/json
```

#### Response Body
```json
{
  "dealershipId": "00000000-0000-0000-0000-000000000001",
  "vehicleCategoryId": "11111111-0000-0000-0000-000000000001",
  "items": [
    {
      "serviceTypeId": "22222222-0000-0000-0000-000000000001",
      "serviceCode": "STANDARD_SERVICE",
      "cost": 150.00
    }
  ],
  "totalCost": 150.00
}
```
