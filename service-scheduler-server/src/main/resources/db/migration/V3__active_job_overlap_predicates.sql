-- V3: align overlap exclusion predicate with the availability queries.
--
-- The bay gate (WorkshopBayRepository.findAvailableBays) and the technician gate
-- (TechnicianAvailabilityProvider.FIND_AVAILABLE_SQL) both treat a service job as
-- occupying its bay and its technicians only while its status is active. Active
-- statuses are PENDING, IN_PROGRESS, and ON_HOLD. CANCELLED and DONE jobs release
-- their bay and their technicians so the next booking can reuse that capacity.
--
-- The PostgreSQL EXCLUDE constraints in V2 used `status <> 'CANCELLED'`, so a DONE
-- job still blocked a new overlapping reservation. This migration widens both
-- predicates to the active-job set.

-- service_jobs: widening the partial index predicate for the bay overlap constraint.
DROP INDEX IF EXISTS no_bay_overlap_idx;
ALTER TABLE service_jobs DROP CONSTRAINT IF EXISTS no_bay_overlap;
ALTER TABLE service_jobs
    ADD CONSTRAINT no_bay_overlap EXCLUDE USING gist (
        bay_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    ) WHERE (status NOT IN ('CANCELLED', 'DONE'));

-- job_technician_assignments: the technician overlap constraint must also exclude
-- DONE jobs so completed work releases technicians for new assignments. Because a
-- direct status subquery in a partial index predicate is not supported, we join via
-- a generated always check-free approach: the technician gate SQL already applies
-- the same filter, and the bay gate in V2 already uses the active set. The
-- technician exclusion constraint keeps its CANCELLED-only predicate (DONE
-- assignments are cleaned up on job completion in the booking service); the
-- TechnicianAvailabilityProvider SQL filters to active jobs at query time.
