# ADR-012: Service Job Bay Reservation and Multi-Tier Overlap Exclusion

**Status:** Accepted  
**Date:** 2026-10-24  
**Project:** Unified Service Scheduler  
**Supersedes:** the constraint placement in [ADR-003](ADR-003-postgresql-exclude-constraint-for-booking-overlap.md)  

---

## Context

ADR-003 originally placed the `EXCLUDE USING gist` constraint on `APPOINTMENTS`. One appointment can hold several service jobs, each running in a different bay or time window.

Additionally, two other race conditions can occur:
1. The same vehicle (`vehicle_vin`) being scheduled in two overlapping appointments.
2. The same technician being assigned to two overlapping service jobs across different bays.

---

## Decision

Move the bay reservation to `SERVICE_JOBS` and enforce multi-tier overlap constraints in PostgreSQL.

1. **Service Job Bay Reservation:**
   - `SERVICE_JOBS` holds `bay_id`, `start_time`, and `end_time`.
   - `CONSTRAINT no_bay_overlap EXCLUDE USING gist (bay_id WITH =, tstzrange(start_time, end_time) WITH &&) WHERE (status <> 'CANCELLED')`.
2. **Vehicle VIN Overlap Guard:**
   - `APPOINTMENTS` holds `vehicle_vin`, `start_time`, and `end_time`.
   - `CONSTRAINT no_vehicle_overlap EXCLUDE USING gist (dealership_id WITH =, vehicle_vin WITH =, tstzrange(start_time, end_time) WITH &&) WHERE (status NOT IN ('CANCELLED','REJECTED','NO_SHOW'))`.
3. **Technician Non-Overlap Protection:**
   - `job_technician_assignments` records `assigned_start_time` and `assigned_end_time` bounded by the job window.
   - `CONSTRAINT no_technician_overlap EXCLUDE USING gist (technician_id WITH =, tstzrange(assigned_start_time, assigned_end_time) WITH &&)`.
4. **Appointment Summary Window:**
   - `APPOINTMENTS.start_time` is the earliest job start time; `end_time` is the latest job end time.

---

## Reason

The service job is the actual unit of physical bay reservation. Placing exclusion constraints on the exact resource (bay, vehicle, and technician) delegates concurrency safety entirely to PostgreSQL without application-level distributed locks.

---

## Consequences

### Positive

- Bays cannot be double-booked.
- Vehicles cannot be double-booked.
- Technicians cannot be double-booked across concurrent jobs.
- Safe under multi-pod deployment with no Redis distributed lock overhead.

### Negative

- Requires PostgreSQL with `btree_gist` extension (H2 does not support `gist` exclusion; integration tests use Testcontainers).

---

## Related

- `docs/SYSTEM_DESIGN.md` §4.2, §4.3, §5
- `docs/adr/ADR-003-postgresql-exclude-constraint-for-booking-overlap.md`
- Data Model diagram: `docs/diagrams/system/system-data-model.html`
