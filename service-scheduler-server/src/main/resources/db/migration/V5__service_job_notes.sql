-- V5: job inspection notes for staff feedback capture (change manager-schedule-realtime).
-- Technicians append inspection notes via POST /api/v1/jobs/{id}/notes.
-- Notes surface as feedback data in the staff booking detail view.

ALTER TABLE service_jobs ADD COLUMN notes TEXT;
