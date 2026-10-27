# Tasks: slot-availability-compute

```
CHANGE   slot-availability-compute               BRANCH   feature/slot-availability-compute
STATES   14   done 8  current 9  next 10  skipped 0  failed 0  pending 6
TASKS    12   done 12  open 0

  #   state                     mark      proof
  1   Propose                   done      proposal.md and spec.md created
  2   Branch                    done      branch is feature/slot-availability-compute
  3   Plan                      done      architecture and data flow planned
  4   Code                      current   implementation in progress
  5   Format                    next      spotlessApply pending
  6   Test                      pending   tests pending
  7   Review                    pending   ocr delegate not yet run
  8   Verify                    pending   openspec validate not yet run
  9   Request Approval          pending   awaiting HIL-2
  10  Publish                   pending   awaiting approval
  11  Verify PR                 pending   awaiting push
  12  Archive                   pending   awaiting merge
```

## Implementation Tasks

- [x] Read SYSTEM_DESIGN.md slot availability algorithm (Section 5.4)
- [x] Explore existing SlotAvailabilityService, models, repositories
- [x] Identify ArchUnit constraints (scheduling cannot import resource)
- [x] Create feature branch feature/slot-availability-compute
- [x] Create WorkshopBay entity in scheduling/model
- [x] Create ServiceTypeTechnicianRequirement entity in scheduling/model
- [x] Create WorkshopBayRepository in scheduling/repository
- [x] Create ServiceTypeTechnicianRequirementRepository in scheduling/repository
- [x] Create TechnicianAvailabilityPort interface in common/provider
- [x] Implement TechnicianAvailabilityProvider in resource/service
- [x] Implement SlotGridComputeService in scheduling/service (5-step algorithm)
- [x] Update SlotAvailabilityService to read-compute-write cache
- [x] Update SlotAvailabilityController to validate date range (HTTP 400)
- [x] Write JUnit 5 tests for all service classes
- [x] Run spotlessApply, checkstyle, PMD
- [x] Run ./gradlew test jacocoTestReport
- [x] Run ocr delegate code review
