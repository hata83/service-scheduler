CHANGE   phase-1-bootstrap-otp-auth             BRANCH   feature/phase-1-bootstrap-otp-auth
STATES   14   done 8  current 9  next 10  skipped 0  failed 0  pending 6
TASKS    39   done 8  open 31  (merged from openspec/changes/phase-1-bootstrap-otp-auth/tasks.md)

# Agentic SDLC state block — reference openspec spec + design + proposal for full 39-task list
# Source of truth for task-level tracking: openspec/changes/phase-1-bootstrap-otp-auth/tasks.md

  #  state                     mark     proof
  1  Propose                   done     openspec validate --strict: valid (8 design rounds, HIL-1 passed)
  2  Branch                    done     feature/phase-1-bootstrap-otp-auth from 55b2f99
  3  Plan                      done     D2 rate limit JSON + D8 idempotency + D7 OTP slots (ADR-008/007/011)
  4  Code                      current  auth/OtpStorage + RequestIdFilter + IdempotencyFilter + migrations V1/V2 written; register + verify endpoints NOT YET
  5  Format                    done     ./gradlew spotlessApply OK; Palantir format 2.71; PMD exclusion (UnitTestContainsTooManyAsserts)
  6  Test                      current  OtpStorageTest 6/6 + SchemaMigrationTest 4/4 pass (now in test source set); Bruno files started
  7  Review                    done     ocr delegate preview: 0 reviewable; all critical/high fixed
  8  Verify                    done     clean build -x test SUCCESS + PMD/main/integration + checkstyle + Spotless + OpenSpec --strict valid
  9  Approval                  current  HIL-2: pending approval to push PR; SchemaMigrationTest best-practice fix done (6f0d040)
 10  Publish                   pending  push branch + open PR vs main after HIL-2
 11  PR-Verify                 pending  CI must pass (no H2, correct Redis env names, compose DB)
 12  Archive                  pending  /opsx-archive after PR merged
 13  Review-Archive            pending  diagram SVG links + openwiki generation
 14  Merge                     pending  HIL-3 human gate required

# Running loop — customer-registration (6.1–6.5) then otp-login verify (5.4–5.11)
# Current open sub-tasks from openspec/tasks.md:
#   5.4 otp/send endpoint + Bruno (8 files), 5.5 JwtService + session, 5.7 otp/verify endpoint + Bruno (10 files)
#   6.1 RegisterService TDD + 6.2 register endpoint + Bruno (8 files), 6.5 customer-registration E2E spec.ts
# E2E target: service-scheduler-ui/tests/e2e/customer-registration.spec.ts passes against compose-stacked server
