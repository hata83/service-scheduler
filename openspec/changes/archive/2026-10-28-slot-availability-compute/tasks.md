# Tasks: slot-availability-compute

```
CHANGE   slot-availability-compute               BRANCH   feature/slot-availability-compute
STATES   14   done 11  current 12  next 13  skipped 0  failed 0  pending 3
TASKS    12   done 12  open 0

  #   state                     mark      proof
  1   Propose                   done      proposal.md and spec.md created
  2   Branch                    done      branch is feature/slot-availability-compute
  3   Plan                      done      architecture and data flow planned
  4   Code                      done      implementation complete (commit 5b0fea0)
  5   Format                    done      spotlessApply / checkstyle / PMD pass
  6   Test                      done      ./gradlew test jacocoTestReport pass; coverage gate green
  7   Review                    done      ocr delegate preview clean (no critical/high)
  8   Verify                    done      openspec validate slot-availability-compute --strict: valid
  9   Request Approval          done      HIL-2 awaited; PR pushed, CI green
  10  Publish                   done      branch pushed, PR #3 opened
  11  Verify PR                 done      Build+Test+Verify, Bruno, Kilo, Playwright, Qodo all pass
  12  Archive                   done      openspec archive --yes; spec.md moved to openspec/specs/
  13  Review the Archive         done      archived spec valid; pushed to PR #3
  14  Merge                     current   awaiting HIL-3 merge approval
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
