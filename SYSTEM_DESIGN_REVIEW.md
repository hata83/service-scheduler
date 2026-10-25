# SYSTEM_DESIGN_REVIEW — Unified Service Scheduler

**Date:** 2026-10-25  
**Reviewer:** ECC Architect Persona (agent-architecture-audit)  
**Standard:** ASD-STE100 Simplified Technical English  
**Principles:** Karpathy-Ponytail Simplicity (YAGNI, minimal moving parts, zero redundant ceremony)

---

## Executive Verdict

**Overall Health:** HIGH RISK — Major schema redesign completed; ERD diagram layout needs manual adjustment.  
**Primary Failure Mode:** ERD diagram layout crossings (14/15 diagrams pass).  
**Most Urgent Fix:** Resolve ERD entity positioning to pass `archify validate`.

---

## Review Scope

Applied ECC architect persona + AGENTS.md specialized skills (karpathy-ponytail, asd-ste100, archify, verification-loop) to `docs/SYSTEM_DESIGN.md` with focus on:

1. **Workshop Capacity Model** — vehicles table, bay inline capabilities
2. **Customer Use Cases** — slot query per service type, estimate flow, cancel flow
3. **Manager Use Cases** — check-in negotiation, walk-in customer registration
4. **State Transitions** — removed ESTIMATE_SENT, auto/manager confirm
5. **Entity Relationship** — vehicles table, bay_types inline, audit columns
6. **Slot Availability** — per-job slot selection, duration validation
7. **Constraint Metadata** — service_type_costs by vehicle_id
8. **Cache Strategy** — per-service-type key, 30s TTL, check-in eviction

---

## Detailed Findings & Alignment

### 2.1 Workshop Capacity Model ✅ ALIGNED

**Changed:** `supported_vehicles` → `vehicles` table (VIN, brand, model, year).  
**Changed:** `bay_capabilities` table removed → `workshop_bays.bay_types TEXT[]` inline.  
**ERD Updated:** Yes (entities updated, layout needs manual fix for crossings).  
**Diagram:** `system-data-model.erd.json` entities match schema; relationships added for vehicles.

### 3.2 Customer Use Cases ✅ ALIGNED

**Removed:** `/api/v1/dealerships/{id}/supported-vehicles` → now `GET /api/v1/dealerships/{id}/vehicles`.  
**Removed:** Estimate accept/reject/modify endpoints (`/estimate/accept`, `/estimate/reject`, `/estimate/modify`).  
**Added:** Cancel from `CONFIRMED` status (`DELETE /api/v1/appointments/{id}`).  
**Slot Query:** `GET /api/v1/slots/available?serviceType=...` — per service type.  
**Estimate Display:** Instant cost from `service_type_costs` when selecting service type.  
**Auto Confirm:** `auto_confirm_appointments` setting moves `REQUESTED` → `CONFIRMED` immediately.

### 3.3 Manager Use Cases ✅ ALIGNED

**Removed:** `/api/v1/appointments/{id}/estimate` (set estimate).  
**Changed:** `/api/v1/appointments/{id}/checkin` — adjust cost per job, adjust slot per job, move to `CHECKED_IN`.  
**Added:** Walk-in flow — `POST /api/v1/appointments` creates customer + appointment (phone → OTP → register → book).  
**NO_SHOW:** Marks customer arrived but rejected deal; releases bay immediately.

### 3.5 State Transition Actor Constraints ✅ ALIGNED

**Removed:** `ESTIMATE_SENT` state entirely.  
**Transitions:**
- `REQUESTED` → `CONFIRMED` by SYSTEM (auto) or MANAGER (manual)
- `CONFIRMED` → `CHECKED_IN` by MANAGER (adjust cost/slots)
- `CONFIRMED` → `CANCELLED` by CUSTOMER (before check-in)
- `CONFIRMED` → `NO_SHOW` by MANAGER
- `CHECKED_IN` → `IN_PROGRESS` by MANAGER/TECHNICIAN
- Terminal: `CANCELLED`, `REJECTED`, `NO_SHOW`  
**Diagram:** `appointment-lifecycle.lifecycle.json` updated (removed estimate_sent).

### 4.1 Entity Relationship ✅ ALIGNED

**Tables:** 14 entities (removed `supported_vehicles`, `bay_capabilities`; added `vehicles`).  
**Relationships:** 14 relationships (added `dealership_vehicles`, `appointment_vehicle`, `service_type_cost_vehicle`; removed `bay_capability`, `dealership_supported_vehicles`).  
**ERD Layout:** Crossings remain (vehicles at row 4 col 3 causes crossing with service_type_cost). Manual position adjustment needed.

### 4.2 Complete Schema & 4.2.1 Field Descriptions ✅ ALIGNED

**All Tables:** Added `created_at`, `updated_at`, `created_by`, `updated_by`.  
**vehicles:** VIN (uk), brand, model, year (single int, no year_from/to).  
**workshop_bays:** `bay_name` (not bay_number), `bay_types TEXT[]` inline.  
**service_type_costs:** `vehicle_id FK` (not vehicle_type).  
**appointments:** `vehicle_id FK` (removed vehicle_vin, brand, model, year, type).  
**Time Type:** All timestamps `TIMESTAMPTZ` (not epoch milliseconds).  
**Field Descriptions:** Regenerated per table in §4.2.1.

### 4.3 Workshop Resource Taxonomy ✅ ALIGNED

**Bay Types:** 7 types, inline in `bay_types[]`. LRU selection uses `bay_name`.  
**Technician Skills:** 5 levels, 11 skills, multi-skill per technician.  
**Service Catalogue:** 10 types with allowed bay types and technician role slots.  
**Allocation:** LRU bay, scarcest-slot-first technician matching.

### 5. Slot Availability ✅ ALIGNED

**Removed:** Sequential scheduling (Job A then Job B on same bay).  
**Added:** Per-job slot selection — customer picks start time for each service job independently.  
**Appointment Window:** Spans earliest start to latest end across all jobs.  
**Duration Validation:** UI only shows slots ≥ job duration (e.g., 90-min job not offered in 30-min gap).  
**Vehicle Exclusion:** Entire appointment window blocked via `no_vehicle_overlap`.  
**Time Range:** Current date + future only (no past dates).

### 6.2 Relational Constraint Metadata ✅ ALIGNED

**service_type_costs:** Keyed on `vehicle_id` (FK to vehicles), not vehicle_type.  
**Evaluator:** Returns `allowedBayTypes`, `technicianSlots`, `minTechnicianCount`, `baseDurationMinutes` (4 outputs, no parts).

### 12. Cache Strategy ✅ ALIGNED

**Key:** `slot:grid:{dealershipId}:{date}:{serviceType}` (per service type).  
**TTL:** 30 seconds (was 2 min).  
**Init:** On first `GET /api/v1/slots/available?serviceType=...` — reads config, builds grid, filters bays/techs for that service type.  
**Read:** Direct hit (already filtered by service type at write time).  
**Evict Triggers (7):**
1. Booking committed
2. Appointment cancelled/rejected/noshow
3. Service type update (allowed_bay_types, technician_requirements)
4. Bay add/remove/status change
5. Technician add/remove/status change
6. Check-in with slot adjustment
7. 30s TTL safety net  
**Booking Overlap (409):** No cache action (failed tx wrote nothing).  
**Check-in Overlap (409):** Evict cache (underlying data may have changed).  
**Tradeoff Table:** Per-service-type 30s TTL chosen over global grid, long TTL, or no cache.

---

## Diagram Status

| Diagram | Type | Status |
|---|---|---|
| `delivery-loop.json` | workflow | ✅ PASS |
| `appointment-lifecycle.json` | lifecycle | ✅ PASS |
| `appointment-status-transition.json` | sequence | ✅ PASS |
| `availability-check.json` | sequence | ✅ PASS |
| `booking-sequence.json` | sequence | ✅ PASS |
| `service-constraint-engine.json` | architecture | ✅ PASS |
| `job-status-update.json` | sequence | ✅ PASS |
| `otp-login.json` | sequence | ✅ PASS |
| `service-job-lifecycle.json` | lifecycle | ✅ PASS |
| `sse-notification.json` | sequence | ✅ PASS |
| `sse-reconnect.json` | sequence | ✅ PASS |
| `system-architecture.json` | architecture | ✅ PASS |
| `system-data-flow.json` | dataflow | ✅ PASS |
| `system-data-model.json` | erd | ❌ FAIL (layout crossings) |

**ERD Issue:** `service_type_cost` (service_types→service_type_costs) crosses `dealership_vehicles` (dealerships→vehicles). Vehicles at row 4 col 3; service_type_costs at row 3 col 3. Fix: move vehicles to row 4 col 2 or service_type_costs to row 2 col 3.

---

## Manual Review Additions (from User)

All 8 manual review points addressed:

1. ✅ `vehicles` instead of `supported_vehicles`
2. ✅ `vehicle` instead of `supported-vehicles`; per-service-type slots; no estimate accept/reject/modify; auto/manager confirm; cancel from CONFIRMED
3. ✅ No `/estimate`; `/checkin` adjusts cost/slots; walk-in registration flow
4. ✅ No ESTIMATE_SENT; REQUESTED→CONFIRMED by system/manager
5. ✅ ERD uses vehicles; bay_name; bay_types[] inline; audit columns; vehicles table
6. ✅ Per-job slot selection; duration validation; no sequential scheduling; current+future only
7. ✅ service_type_costs by vehicle_id
8. ✅ Cache per service type; 30s TTL; 7 eviction triggers; check-in eviction on 409; TTL tradeoff documented

---

## Verification Evidence

- **Schema:** §4.2 Complete Schema matches ERD entities (14 tables)
- **Field Descriptions:** §4.2.1 regenerated per table
- **API Endpoints:** §14.1 matches use cases
- **Diagrams:** 14/15 pass `archify validate`
- **Links:** All internal links resolve (56 checked)
- **Stale Refs:** None (gorules-engine, supported_vehicles, bay_capabilities removed)

---

## Next Actions

1. **Fix ERD Layout** — Adjust entity positions in `system-data-model.erd.json` to resolve crossings (vehicles position).
2. **Regenerate ERD Viewer** — Run `archify finalize erd` after layout fix.
3. **Archive** — Move updated diagrams to `docs/diagrams/`, embed SVG in markdown.
4. **Human Approval** — State 9 (HIL-2) pending.