-- V3b: restore technician active-assignment overlap protection.
--
-- V3 removed the unconditional `no_technician_overlap` constraint because a
-- partial index cannot reference service_jobs.status across a join. This
-- migration adds a denormalized `job_status` column on
-- job_technician_assignments that mirrors service_jobs.status, keeps it
-- in sync via a trigger, and re-adds the EXCLUDE constraint filtered to
-- active statuses only (SYSTEM_DESIGN §5.3).
--
-- The bay gate and technician gate both treat CANCELLED and DONE jobs as
-- released capacity, so the active set for technicians is
-- PENDING, IN_PROGRESS, ON_HOLD — matching V3's bay predicate.

-- 1. Copy the job status onto each assignment (backfill). Existing assignments
--    inherit their job's current status.
ALTER TABLE job_technician_assignments ADD COLUMN IF NOT EXISTS job_status VARCHAR(20);

UPDATE job_technician_assignments a
SET job_status = j.status
FROM service_jobs j
WHERE j.id = a.job_id;

-- Any remaining NULLs (orphans, unlikely) are treated as non-active.
UPDATE job_technician_assignments
SET job_status = 'CANCELLED'
WHERE job_status IS NULL;

ALTER TABLE job_technician_assignments ALTER COLUMN job_status SET NOT NULL;

-- 2. Keep the denormalized column in sync when the underlying job status changes
--    or when assignments are inserted/updated.
CREATE OR REPLACE FUNCTION sync_assignment_job_status()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.job_status := (
            SELECT status FROM service_jobs WHERE id = NEW.job_id
        );
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        -- An assignment update may reassign job_id; re-resolve.
        IF NEW.job_id IS DISTINCT FROM OLD.job_id OR NEW.job_status IS NULL THEN
            NEW.job_status := (
                SELECT status FROM service_jobs WHERE id = NEW.job_id
            );
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL; -- unreachable for this trigger (no DELETE action)
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_assignment_job_status
    ON job_technician_assignments;
CREATE TRIGGER trg_sync_assignment_job_status
    BEFORE INSERT OR UPDATE ON job_technician_assignments
    FOR EACH ROW EXECUTE FUNCTION sync_assignment_job_status();

-- Mirror service_jobs.status changes onto assignments so the partial EXCLUDE
-- predicate stays accurate without application code touching it.
CREATE OR REPLACE FUNCTION sync_assignment_status_on_job_change()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        UPDATE job_technician_assignments
        SET job_status = NEW.status
        WHERE job_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_assignment_status_on_job_change
    ON service_jobs;
CREATE TRIGGER trg_sync_assignment_status_on_job_change
    AFTER UPDATE OF status ON service_jobs
    FOR EACH ROW EXECUTE FUNCTION sync_assignment_status_on_job_change();

-- 3. Re-add the overlap EXCLUDE constraint filtered to active assignments only.
--    DONE and CANCELLED jobs release their technicians, consistent with the
--    availability SQL and the bay gate.
ALTER TABLE job_technician_assignments
    DROP CONSTRAINT IF EXISTS no_technician_overlap;
ALTER TABLE job_technician_assignments
    ADD CONSTRAINT no_technician_overlap EXCLUDE USING gist (
        technician_id WITH =,
        tstzrange(assigned_start_time, assigned_end_time) WITH &&
    ) WHERE (job_status NOT IN ('CANCELLED', 'DONE'));
