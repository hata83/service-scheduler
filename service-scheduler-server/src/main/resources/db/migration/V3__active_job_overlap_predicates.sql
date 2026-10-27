-- V3: align overlap exclusion predicate with the availability queries.
--
-- The bay gate (WorkshopBayRepository.findAvailableBays) and the technician gate
-- (TechnicianAvailabilityProvider.FIND_AVAILABLE_SQL) both treat a service job as
-- occupying its bay and its technicians only while its status is active. Active
-- statuses are PENDING, IN_PROGRESS, and ON_HOLD. CANCELLED and DONE jobs release
-- their bay and their technicians so the next booking can reuse that capacity.
--
-- The PostgreSQL EXCLUDE constraints in V2 used `status <> 'CANCELLED'` for bays
-- and an unconditional predicate for technicians, so DONE jobs still blocked new
-- reservations. This migration widens the bay predicate to the active set and
-- removes the technician overlap EXCLUDE constraint (which cannot reference the
-- job status via a cross-table subquery in a partial index predicate).
--
-- Technician availability is enforced at query time by
-- TechnicianAvailabilityProvider.FIND_AVAILABLE_SQL, which joins assignments to
-- their service_jobs and filters to active statuses (SYSTEM_DESIGN §5.3).

-- service_jobs: widening the partial index predicate for the bay overlap constraint.
ALTER TABLE service_jobs DROP CONSTRAINT IF EXISTS no_bay_overlap;
ALTER TABLE service_jobs
    ADD CONSTRAINT no_bay_overlap EXCLUDE USING gist (
        bay_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    ) WHERE (status NOT IN ('CANCELLED', 'DONE'));

-- job_technician_assignments: drop the unconditional tech-overlap constraint.
-- It cannot express a cross-table job-status filter. The TechnicianAvailabilityProvider
-- SQL enforces the active-job rule in application logic instead.
ALTER TABLE job_technician_assignments DROP CONSTRAINT IF EXISTS no_technician_overlap;
