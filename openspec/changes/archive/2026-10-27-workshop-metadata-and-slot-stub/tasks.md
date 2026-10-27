# Tasks

```
CHANGE   workshop-metadata-and-slot-stub                   BRANCH   feature/workshop-metadata-and-slot-stub
STATES   14   done 12 current 1  next 1  skipped 0  failed 0  pending 0
TASKS    25   done 25 open 0

  #   state                     mark     proof
  1   Propose                   done     openspec validate --strict: valid + HIL-1 approved
  2   Branch                    done     branch is feature/workshop-metadata-and-slot-stub
  3   Plan                      done     all 25 tasks defined and sequenced
  4   Code                      done     backend-first TDD ladder BE-1 to BE-6 complete
  5   Format                    done     spotlessApply + checkstyleMain + checkstyleTest passed
  6   Test                      done     142 unit/integration tests + JaCoCo 92.6% (100% on services)
  7   Review                    done     ocr delegate preview + review.md + verifyCodeReviewDelegate passed
  8   Verify                    done     ./gradlew clean build: SUCCESSFUL + Playwright 34/34 + Bruno 56/56
  9   Approval                  done     HIL-2 approved by user to publish and create PR
 10   Publish                   done     PR #2 created: https://github.com/hata83/service-scheduler/pull/2
 11   Verify PR                 done     CI run 40486716509 passed, Qodo and Kilo Code findings resolved
 12   Archive                   done     openspec archive executed to 2026-10-27-workshop-metadata-and-slot-stub
 13   Review archive            current  committing and pushing archive to PR #2
 14   Merge                     next     awaiting user decision (HIL-3)
```

## 1. Branch Setup (State 2)

- [x] 1.1 Create feature branch `feature/workshop-metadata-and-slot-stub` from `master`. Verify `git branch --show-current` outputs `feature/workshop-metadata-and-slot-stub`.

## 2. Database Schema and Seed Migrations (`workshop-seed-data`)

- [x] 2.1 Add `V2__workshop_metadata_and_appointments_schema.sql` creating `vehicle_categories`, `workshop_bays`, `service_types`, `service_type_technician_requirements`, `service_type_costs`, `appointments`, `service_jobs`, and `job_technician_assignments`. Verify `./gradlew test` executes migration without errors.
- [x] 2.2 Add `R__seed_metadata_fixtures.sql` seeding demo vehicle categories, workshop bays, service types, technician requirements, and pricing matrix. Verify integration test finds all seeded entities.
- [x] 2.3 Add `R__seed_schedule_and_appointments.sql` seeding existing appointments with `CONFIRMED` status on next day 1, day 2, and day 3. Verify integration test finds bookings with `CONFIRMED` status on days 1, 2, and 3.
- [x] 2.4 Add Redis test fixture populator for demo slot grid cache keys `slot:grid:{dealershipId}:{serviceTypeId}:{date}` reflecting booked and available windows. Verify Redis contains demo slot keys during test runs.

## 3. Dealership and Vehicle Categories Backend Leg (`workshop-metadata`)

- [x] 3.1 TDD BE-1/BE-2: Write failing unit test for `DealershipService.findAllActive()` and implement repository and service methods. Verify test passes.
- [x] 3.2 TDD BE-1/BE-2: Write failing unit test for `VehicleCategoryService.findByDealershipId()` and implement domain model, repository, and service. Verify test passes.
- [x] 3.3 Implement `GET /api/v1/dealerships` endpoint in `DealershipController` with SpringDoc annotations. Add Bruno contract file `service-scheduler-server/src/test/bruno/api/metadata/dealerships-list.bru`. Verify Bruno test passes.
- [x] 3.4 Implement `GET /api/v1/dealerships/{id}/vehicle-categories` endpoint in `VehicleCategoryController` with SpringDoc annotations. Add Bruno contract file `service-scheduler-server/src/test/bruno/api/metadata/vehicle-categories-list.bru`. Verify Bruno test passes.

## 4. Service Types and Cost Estimation Backend Leg (`workshop-metadata`)

- [x] 4.1 TDD BE-1/BE-2: Write failing unit test for `ServiceTypeService.findByDealershipId()` and implement entity, repository, and service. Verify test passes.
- [x] 4.2 TDD BE-1/BE-2: Write failing unit test for `PricingService.calculateEstimate()` calculating itemized and total cost from `service_type_costs`. Verify test passes.
- [x] 4.3 Implement `GET /api/v1/dealerships/{id}/service-types` in `ServiceTypeController`. Add Bruno contract file `service-scheduler-server/src/test/bruno/api/metadata/service-types-list.bru`. Verify Bruno test passes.
- [x] 4.4 Implement `GET /api/v1/dealerships/{id}/pricing/estimate` in `PricingController`. Add Bruno contract file `service-scheduler-server/src/test/bruno/api/metadata/pricing-estimate.bru`. Verify Bruno test passes.

## 5. Cached Slot Availability Backend Leg (`slot-availability-cache`)

- [x] 5.1 TDD BE-1/BE-2: Write failing unit test for `SlotAvailabilityService.getCachedAvailableSlots()` reading from Redis key `slot:grid:{dealershipId}:{serviceTypeId}:{date}` with fallback to empty list. Verify test passes.
- [x] 5.2 Implement `GET /api/v1/slots/available` in `SlotAvailabilityController` with SpringDoc annotations and RFC 7807 problem details. Add Bruno contract file `service-scheduler-server/src/test/bruno/api/metadata/slots-available-cached.bru`. Verify Bruno test passes.

## 6. Architecture Diagrams and Documentation Alignment

- [x] 6.1 Update Archify data model source `docs/diagrams/src/system/system-data-model.erd.json` replacing `vehicles` with `vehicle_categories` and adding workshop metadata tables. Finalize interactive viewer `docs/diagrams/system/system-data-model.html`. Verify `node .agents/skills/archify/archify/bin/archify.mjs validate erd` passes.
- [x] 6.2 Update Archify metadata architecture source `docs/diagrams/src/system/metadata-management.sequence.json` and finalize viewer `docs/diagrams/system/metadata-management.html`. Verify `node .agents/skills/archify/archify/bin/archify.mjs validate sequence` passes.
- [x] 6.3 Update `docs/SYSTEM_DESIGN.md` §4.2.6 reflecting `vehicle_categories` schema (`brand`, `model`, `type`) and removing VIN. Verify document links resolve.

## 7. Frontend Mocking and Playwright E2E

- [x] 7.1 Add MSW mock handlers for metadata endpoints in `service-scheduler-ui/tests/unit/mock/handlers.ts`. Verify MSW handlers compile.
- [x] 7.2 Implement Playwright E2E spec `service-scheduler-ui/tests/e2e/workshop-metadata.spec.ts` covering dealership selection, vehicle category browsing, and instant cost estimation. Verify Playwright test passes.
- [x] 7.3 Implement Playwright E2E spec `service-scheduler-ui/tests/e2e/slot-availability-cache.spec.ts` covering cached slot grid view and booked slot indicators. Verify Playwright test passes.
- [x] 7.4 Implement Playwright E2E spec `service-scheduler-ui/tests/e2e/workshop-seed-data.spec.ts` covering seeded metadata and `CONFIRMED` bookings. Verify Playwright test passes.

## 8. Final Gates and SDLC Verification

- [x] 8.1 Run `./gradlew :service-scheduler-server:spotlessApply`, Checkstyle, PMD, and ArchUnit package independence tests. Verify all quality gates pass.
- [x] 8.2 Run `./gradlew test jacocoTestReport` and verify 100% test coverage on new `@Service` classes and 80% overall.
- [x] 8.3 Run `ocr delegate preview --format json` and resolve all findings in `review.md`. Verify `./gradlew :service-scheduler-server:verifyCodeReviewDelegate` passes.
- [x] 8.4 Run `openspec validate workshop-metadata-and-slot-stub --strict`. Verify change specification is valid.
