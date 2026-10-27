# Change Proposal: fix-scheduling-correctness-bugs

## Why

The technician availability query in `TechnicianAvailabilityProvider` uses
`status NOT IN ('CANCELLED','REJECTED')`, omitting `DONE`. DONE jobs still hold
technician capacity, which is inconsistent with the V3 bay gate and oversells
technician availability.

## What Changes

- Update `FIND_AVAILABLE_SQL` to `NOT IN ('CANCELLED','REJECTED','DONE')` so
  technicians released by completed jobs are counted as available.
- Add a regression test proving a DONE-job assignment releases the technician.

## Out of Scope

- N+1 query reduction in `SlotGridComputeService` (larger refactor).
- DST-aware candidate generation (larger refactor).
- Technician overlap EXCLUDE constraint replacement (atomic, separate).
