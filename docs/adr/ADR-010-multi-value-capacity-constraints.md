# ADR-010: Multi-Value Capacity Constraints for Bays and Technicians

**Status:** Accepted  
**Date:** 2026-10-23  
**Amended:** 2026-10-24  
**Project:** Unified Service Scheduler  

---

## Context

The first draft of the schema modelled capacity with single-value columns:

- `workshop_bays.bay_type` — one bay, one bay type
- `technicians.skill_level` — one technician, one level
- `technicians.specializations` — one JSON array, no level per skill

That model answers three real workshop questions incorrectly:

1. A gearbox overhaul needs a `PIT` bay or a `HEAVY_LINE` bay. One bay cannot express both.
2. A multi-purpose bay serves a quick-lube job in the morning and a brake job after lunch. One bay type forbids that.
3. One technician is a `MASTER` on transmission work and a `JUNIOR` on bodywork. One level forbids that.

---

## Decision

Replace single-value columns with join tables and clean relational metadata.

1. **One bay carries many bay types.** `workshop_bays` loses `bay_type`. A `bay_capabilities` table holds one row per bay and bay type.
2. **One technician holds many skills, each with its own level.** `technicians` loses `skill_level` and `specializations`. A `technician_skills` table holds one row per technician and skill, with a level column.
3. **The level ladder is `TRAINEE`, `JUNIOR`, `TECHNICIAN`, `SENIOR`, `MASTER`.** The earlier names `APPRENTICE` and `JOURNEYMAN` were replaced because automotive workshops use standard qualification tiers.
4. **Service constraints use Java and relational metadata.** Instead of an external rule engine (GoRules ZEN), a Spring bean (`ServiceConstraintEvaluator`) evaluates service types, allowed bay types, and technician role slots.
5. **A job requires role slots, not a flat skill list.** One slot names its skill, its minimum level, and the count of technicians needed.
6. **Multi-skill matching inside a slot is an AND.** A technician holding three of four required skills does not qualify for that slot.
7. **Allocation order.** Allocation walks the bay type preference order, takes the least recently used free bay, and fills the most constrained technician slot first.

---

## Reason

A join table expresses "many" correctly without growing columns. Evaluating constraints in Java with relational metadata avoids native Rust UniFFI dependencies and JDM JSON complexity while keeping tests simple and fast.

---

## Consequences

### Positive

- A bay is multi-purpose without schema changes.
- A technician is not capped at one skill.
- The constraint evaluator is fully type-safe, deterministic, and testable with JUnit.

### Negative

- The technician allocation query requires `GROUP BY` and hierarchical level ranking.
- Slot filling requires greedy matching (scarcest slot first).

---

## Related

- `docs/SYSTEM_DESIGN.md` §4.2, §4.4, §6.3
- Data Model diagram: `docs/diagrams/system/system-data-model.html`
