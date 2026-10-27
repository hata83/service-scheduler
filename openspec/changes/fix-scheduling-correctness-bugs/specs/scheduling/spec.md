## ADDED Requirements

### Requirement: R-TECHNICIAN-STATUS-FILTER

The technician availability query MUST free technicians held by DONE jobs.

#### Scenario: DONE job releases technician

Given a technician assigned to a DONE service job for an overlapping window,
when `TechnicianAvailabilityPort.findAvailableTechnicianIds` is called,
then that technician is returned as available.
