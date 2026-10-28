# Change Proposal: fix-slot-compute-correctness

## Why

Qodo/Kilo inline reviews flagged remaining correctnesses risks in the existing
`SlotGridComputeService` and V3 schema. These are pre-existing (not introduced
by slot-availability-compute) but affect the feature's correctness.

## What Changes

- R-SLOT-NPLUS1: collapse per-slot/per-bay-type/per-role queries into a single
  load of bays, day's non-cancelled service_jobs, qualified technicians per
  requirement, and assignments; check overlaps in memory.
- R-SLOT-DST: resolve candidates in the dealership timezone and reject
  nonexistent starts / durations that differ from the service duration across a
  DST transition.
- R-TECHNICIAN-EXCLUDE: restore a transactionally enforced active-assignment
  overlap constraint on `job_technician_assignments` before the compute path
  relies on it.

## Out of Scope

- The SlotGridComputeServiceTest NPE (finding #4) only affects an untested
  midnight-wrap path; tracked for the N+1 refactor that rewrites candidate
  generation.
