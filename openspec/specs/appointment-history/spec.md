# appointment-history Specification

## Purpose
Appointment history allows authenticated customers to view their historical and upcoming appointments with real-time status and itemized job details.

## Requirements

### Requirement: Customer appointments list

The system SHALL return all appointments owned by the authenticated customer via `GET /api/v1/appointments` sorted by start time descending.

#### Scenario: List customer appointments
- **WHEN** authenticated customer requests `GET /api/v1/appointments`
- **THEN** response status is 200 OK and body contains an array of customer appointments

### Requirement: Appointment details query

The system SHALL return full appointment and service job details via `GET /api/v1/appointments/{id}` for an owned appointment.

#### Scenario: View appointment details
- **WHEN** customer requests `GET /api/v1/appointments/{id}` for an appointment they own
- **THEN** response status is 200 OK and body contains appointment status, vehicle, and service jobs

### Requirement: Unique item keys for booking list

The system SHALL provide a unique identifier `id` for each appointment in the booking list, and a unique identifier `id` for each itemized service job, enabling client schedule views to key list elements and update item status reactively by key.

#### Scenario: Unique keys across booking list items
- **WHEN** client fetches customer appointments via `GET /api/v1/appointments`
- **THEN** every appointment item has a unique primary key `id` with no duplicates across the list

### Requirement: Cross-customer access restriction

The system SHALL reject access with HTTP 404 Problem Details when a customer requests an appointment owned by another user.

#### Scenario: Unauthorized appointment access
- **WHEN** customer requests `GET /api/v1/appointments/{id}` for an appointment belonging to another customer
- **THEN** response status is 404 Not Found with an `application/problem+json` body

### Requirement: Staff detail access scope

The system SHALL allow MANAGER and ADMIN roles to read `GET /api/v1/appointments/{id}` for any
appointment inside the role scope. A MANAGER reads appointments of its dealership only. Out-of-scope
reads return the same HTTP 404 Problem Details as customer cross-access.

#### Scenario: Admin reads any dealership booking
- **WHEN** an ADMIN requests `GET /api/v1/appointments/{id}` for any appointment
- **THEN** response status is 200 OK and body carries the appointment and the staff view

#### Scenario: Customer response stays unchanged
- **WHEN** a CUSTOMER requests `GET /api/v1/appointments/{id}` for an owned appointment
- **THEN** response status is 200 OK and body keeps the existing customer detail shape without the
  staff view

### Requirement: Feedback data in staff detail

The system SHALL surface feedback data in the staff detail view from per-job inspection notes and
from the estimate cost versus final cost comparison. Notes live in the `notes` column of
`service_jobs`.

#### Scenario: Detail shows completed job feedback
- **WHEN** a MANAGER reads the detail of an appointment with a DONE job that holds notes and final
  cost
- **THEN** the staff view shows the note text and shows estimate cost and final cost for the job
