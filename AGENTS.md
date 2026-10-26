# AI Agent Guidelines — Unified Service Scheduler

You are an AI engineer working in this repository. Read the section that matches your task.

Written to ASD-STE100 STE-flavored mode: active voice, ≤25 words per sentence, no semicolons.

---

## 1. Non-Negotiables

Three rules gate every change:

1. **Never assume a requirement.** Read it from `openspec/specs/`. If it is missing, ask the user or run `/opsx-propose`.
2. **Propose before you build.** A new feature needs an approved proposal in `openspec/changes/` before you write code.
3. **Review before you finish.** Run `open-code-review-delegate` over your diff. Fix every critical and high finding.

---

## 2. Environment Setup — Verify Before You Run

Check every tool before you start work:

| Tool | Min Version | Check Command | Install |
|---|---|---|---|
| JDK (Java) | OpenJDK 25 LTS | `java --version` | Install JDK 25, set `JAVA_HOME` |
| Node.js | v20+ | `node -v` | Install LTS Node.js |
| npm | v10+ | `npm -v` | Included with Node.js |
| OpenSpec CLI | v1.14+ | `openspec --version` | `npm install -g openspec` |
| Bruno CLI | v2.14+ | `npx --yes @usebruno/cli run --help` | Via npx |
| Git | v2.40+ | `git --version` | Install Git |
| PostgreSQL | 16+ | `psql --version` | Docker Compose prod profile |
| Redis | 7+ | `redis-cli --version` | Docker Compose |

### OpenSpec Workflow & CLI

OpenSpec manages requirements, change proposals, and task tracking. The workflow config lives at `openspec/config.yaml`. Update it when project constraints change.

Follow this sequence:

```sh
# 1. Install OpenSpec CLI globally
npm install -g openspec

# 2. Initialize OpenSpec for the repository
openspec init

# 3. Explore existing specs before starting
openspec explore

# 4. Create a new change proposal
openspec propose <change-slug>
# Creates openspec/changes/<change-slug>/ with proposal.md, specs/, and tasks.md

# 5. Validate the change specification strictly (gates State 1 and State 8)
openspec validate <change-slug> --strict

# 6. Archive the change after PR verification (State 12)
openspec archive <change-slug>
```

OpenSpec Artifact Lifecycle:
- `openspec/specs/`: Canonical source of truth for active requirements.
- `openspec/changes/<change-slug>/`: Active change proposals.
  - `proposal.md`: Context, motivation, and solution overview.
  - `specs/`: Detailed delta specifications.
  - `tasks.md`: Actionable task list and 15-state Agentic SDLC tracking block.

### Skill Availability Check

When you load this project, check skills in this order:

1. Global install: `~/.agents/skills/` (openspec, code review, ECC, archify, openwiki).
2. Local project: `.agents/skills/` (asd-ste100-skill, karpathy-ponytail-skills).

If a skill exists in either location, use it and ignore the other. If it is missing from both, install it:

```sh
# Global skills (openspec, code review, ECC, archify, openwiki)
npm install -g openspec
npx skills add affaan-m/ecc -s agent-architecture-audit -y --agent '*'
npx skills add affaan-m/ecc -s verification-loop -y --agent '*'
npx skills add affaan-m/ecc -s e2e-testing -y --agent '*'
git clone https://github.com/tt-a1i/archify ~/.agents/skills/archify
git clone https://github.com/alibaba/open-code-review ~/.agents/skills/open-code-review
git clone https://github.com/langchain-ai/openwiki ~/.agents/skills/openwiki

# Local project skills (asd-ste100, karpathy-ponytail)
cd <project>/.agents/skills
git clone https://github.com/danyuchn/asd-ste100-skill asd-ste100-skill
git clone https://github.com/AbdullahHameedKhan/karpathy-ponytail-skills karpathy-ponytail-skills
```

Then initialize OpenSpec for the local project:

```sh
cd <project>
openspec init
```

**Never commit `.agents/` to Git.** `.gitignore` excludes it. Skills are local-only.

---

## 3. The 15-State Agentic SDLC

Every change follows this lifecycle. Repeat states 3 through 8 for each task. Stop at any failing state, fix the root cause, and re-enter at the correct state.

```
propose → branch → plan → code → format → test → review
→ verify → approval → publish → pr-verify → archive → review-archive → merge
```

See `docs/diagrams/agentic-sdlc/agentic-sdlc.html` for the interactive Agentic SDLC diagram.

### State Descriptions

**State 1 — Propose (HIL-1 human gate)**
Run `/opsx-explore`, then `/opsx-propose`. Wait for user approval of the specification in `openspec/changes/`.

**State 2 — Branch**
Create a feature branch from `main`. Never commit to `main`. Name format: `feature/<change-slug>`.

**State 3 — Plan**
Take the next unchecked task from the change's `tasks.md`.

**State 4 — Code**
Follow the verification ladder in Section 5. Work the backend leg before the frontend leg. Write the failing test first. Then write the smallest code that makes it pass.

**States 3 to 8 are a loop, not a queue.**

```
plan → code → format → test → review → fix → code → ...
```

A finding is closed only when the test that would have caught it fails without the fix and passes with it.

**State 5 — Format (gate)**
Run the fix commands in Section 8. Run the format and lint checks in Section 6. Do not enter State 6 while any check fails.

**State 6 — Test**
Run `./gradlew test jacocoTestReport`. Read the coverage report. A failure returns to State 4.

**State 7 — Review (gate)**
Run `ocr delegate preview --format json`. Review each diff against its rules. Fix every critical and high finding. Repeat until a pass reports nothing critical or high.
Document every review in `openspec/changes/<change-slug>/review.md`. Record finding severity, reproduction steps, fix diffs, and verification proofs. This record allows engineers to reproduce and verify each finding after fixes.

**State 8 — Verify (gate)**
Run `openspec validate <change> --strict`. Then run `./gradlew clean build`. Then run the Bruno API test suite. Then run the Playwright E2E test suite. Then run the State 7 review loop over the full branch diff.
All reviews, all Bruno integration tests, and all Playwright E2E tests must pass. If any check fails, return to the loop (`code → format → test → review → fix → code → ...`).

**`./gradlew clean build` is the gate, not a shorter command.**

**State 9 — Request Approval (HIL-2 human gate)**
Present the change, its diff, and State 8 results. Wait. Do not push before a human approves. A rejection returns to State 3 with the rejection reason.

**State 10 — Publish**
Commit the verified tree. Push the branch. Open a pull request against `main`.

**State 11 — Verify the Pull Request**
Read the CI check result for the pull request.
- Every required check passed → go to State 12.
- Any check failed → take the failure report back to State 4, push again, re-enter State 11.

**State 12 — Archive**
Run `/opsx-archive`. Move Archify diagrams this change produced to `docs/diagrams/`. Embed SVG and link viewer in the relevant document.

**State 13 — Review the Archive**
Push the archived spec and diagram move to the same pull request. Re-enter State 11.

**State 14 — Merge (HIL-3 human gate)**
Report that every check and every review passed. Ask how to proceed. Never merge before a human answers.

### State Block Format

Record the current state in `openspec/changes/<change-slug>/tasks.md` and root `tasks.md`. Print the block in every reply. Use this format:

```
CHANGE   <slug>                   BRANCH   feature/<slug>
STATES   14   done N  current 1  next 1  skipped 0  failed 0  pending N
TASKS    N    done N  open N

  #   state                     mark     proof
  1   Propose                   done     openspec validate --strict: valid
  2   Branch                    done     branch is feature/<slug>
  ...
  8   Review                    current  ocr delegate preview not run
  9   Verify                    pending
  ...
```

Six marks only: `done`, `current`, `next`, `skipped`, `pending`, `failed`.

---

## 4. Architecture Constraints

These rules shape every code change:

1. **Package independence.** Domain packages under `dev.hata.scheduler.*`: `appointment/`, `resource/`, `customer/`, `vehicle/`, `notification/`, `scheduling/`, `common/`. Feature packages must not import each other. Put shared logic in `common/`. Engineers can add additional feature domain packages later as requirements evolve. ArchUnit enforces this.

2. **Java 25 Virtual Threads.** Enable with `spring.threads.virtual.enabled=true`. Do not use `synchronized` in hot paths — use `ReentrantLock` to prevent virtual thread pinning.

3. **Java constraint evaluation for service constraints.** Use pure Java `ServiceConstraintEvaluator` backed by relational metadata (`allowed_bay_types`, `service_type_technician_requirements`) for all service type constraint evaluation. Do not introduce external native binary rule engines.

4. **PostgreSQL EXCLUDE for booking overlap.** The `EXCLUDE USING gist` constraints on `service_jobs` (workshop bays), `appointments` (vehicle VIN), and `job_technician_assignments` (technicians) are the booking safety gates. Do not add redundant application-level locks. Trust the database constraints.

5. **Transactional Outbox.** Write `OUTBOX_EVENTS` in the same transaction as the appointment mutation. Use `@TransactionalEventListener(AFTER_COMMIT)` for SSE and webhook dispatch. Never fire notifications before commit.

6. **Idempotency keys required.** All write endpoints must check `X-Idempotency-Key` before processing. Reject requests without this header with HTTP 400.

7. **OpenAPI 3.1 specification.** All endpoints must have SpringDoc annotations. All error responses must use `application/problem+json` (RFC 7807).

---

## 5. Verification Ladder

A backend task is not complete when code compiles. It is complete when it passes this ladder:

### Backend Leg (complete before any frontend work)

| Step | Action | Gate |
|---|---|---|
| BE-1 | Write failing JUnit 5 test | Test must fail |
| BE-2 | Write minimum code to pass | Test must pass |
| BE-3 | Run `./gradlew spotlessApply` | Format gate |
| BE-4 | Run constraint evaluator integration test | Constraint evaluator evaluates correctly |
| BE-5 | Write Bruno `.bru` file for endpoint | Bruno test must pass against running server |
| BE-6 | Run `./gradlew test jacocoTestReport` | Coverage: 100% on `@Service` classes, 80% overall |
| BE-7 | Run `ocr delegate` on diff | No critical or high findings |

**A frontend task that depends on an API is blocked until its Bruno file passes.**

### Frontend Leg (after backend leg passes)

| Step | Action |
|---|---|
| FE-1 | Mock the API in `tests/unit/mock/handlers.ts` |
| FE-2 | Review the mock with `vue-reviewer` subagent |
| FE-3 | Write unit tests against the mock |
| FE-4 | Integrate against the running backend |
| FE-5 | Write Playwright E2E test (one screenshot per acceptance criterion) |

---

## 6. Build and Verification

Leave the project in a state that compiles and passes its tests.

```sh
# Full build (runs all checks)
./gradlew build

# Backend tests and coverage
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html

# Backend format fix
./gradlew :service-scheduler-server:spotlessApply

# Lint gates
./gradlew :service-scheduler-server:checkstyleMain :service-scheduler-server:pmdMain

# Architecture tests
./gradlew :service-scheduler-server:test  # includes ArchUnit

# Bruno API tests (start backend first)
SPRING_PROFILES_ACTIVE=test ./gradlew bootRun &
./gradlew :service-scheduler-server:resetAuthFixtures
npx --yes @usebruno/cli@2.14.1 run src/test/bruno --env local --disable-cookies
```

### Backend Stack

| Component | Version | Role |
|---|---|---|
| Java | 25 LTS | Language + Virtual Threads |
| Spring Boot | 4.1.1 MVC | Framework |
| Hibernate | 7 | JPA provider |
| Flyway | 10+ | Schema migrations |
| Constraint Evaluator | Pure Java | Spring service, relational metadata, microsecond latency |
| Resilience4j | 2.x | Rate limiting, retry, circuit breaker |
| SpringDoc | 2.x | OpenAPI 3.1 generation |
| Micrometer | 1.14+ | Metrics |
| Logstash Encoder | 8.x | Structured JSON logging |
| PostgreSQL | 16+ | The only database. Docker Compose for dev, Testcontainers for CI |
| Redis | 7+ | Distributed cache and session store |

### Code Quality Gates (Backend)

| Tool | Role | Command |
|---|---|---|
| Spotless + Palantir Java Format 2.71 | Formatter | `./gradlew spotlessApply` (fix), `spotlessCheck` (gate) |
| Checkstyle 10 | Style: no wildcard imports, 120-char lines | `./gradlew checkstyleMain` |
| PMD 7 | Code smells, copy-paste detection | `./gradlew pmdMain` |
| ArchUnit 1.x | Package independence enforcement | `./gradlew test` |
| JaCoCo | Coverage reporting | `./gradlew jacocoTestReport` |

---

## 6A. Diagrams

Every diagram has three layers: the inline SVG in Markdown, the interactive Archify HTML viewer, and the JSON source of truth.

`docs/diagrams/index.html` holds the diagram dashboard. Do not add a diagram table to this file.

```sh
# Validate and deliver a diagram
node .agents/skills/archify/archify/bin/archify.mjs finalize architecture \
  docs/diagrams/src/system/system-architecture.architecture.json \
  docs/diagrams/system/system-architecture.html \
  --quality showcase --json

# Check only, without writing the HTML
node .agents/skills/archify/archify/bin/archify.mjs validate architecture \
  docs/diagrams/src/system/system-architecture.architecture.json --json
```

The HTML viewer holds the `Export SVG` button. Use the viewer to get an SVG file. The CLI has no `export-svg` command.

`SYSTEM_DESIGN.md` is the source of truth for the design. Every diagram follows it. Read `SYSTEM_DESIGN.md` before you change a diagram.

## 7. Specialized Skills — How to Apply Each

### `karpathy-ponytail` (Simplicity)

Climb this ladder before writing any code. Stop at the first rung that holds:

1. Does this need to exist at all? (YAGNI)
2. Does it already exist in this codebase?
3. Does the standard library do this?
4. Does a native platform feature do this?
5. Does an already-installed dependency do this?
6. Can this be one line?
7. Write the minimum code that works.

Mark intentional simplifications with a `// ponytail:` comment. Name the ceiling and the upgrade path.

### `asd-ste100` (Simplified Technical English)

Apply to all generated specs, comments, tool definitions, and user-facing documentation.

Rules:
- Use active voice.
- Write sentences with ≤25 words for descriptions.
- Write sentences with ≤20 words for instructions.
- Use one term for one concept. Do not rotate synonyms.
- No semicolons.
- No marketing adjectives (seamless, robust, powerful).
- Use the verb, not the noun: "analyze", not "perform an analysis of".

Run the linter before submitting any document:
```sh
python .agents/skills/asd-ste100-skill/scripts/ste-lint.py README.md
python .agents/skills/asd-ste100-skill/scripts/ste-lint.py PLANNING.md
```

### `archify` (Architecture Diagrams)

Draw and update diagrams in `docs/diagrams/src/` as JSON source files. Export HTML viewer and SVG to `docs/diagrams/`. The diagram dashboard is `docs/diagrams/index.html`. Group a new diagram by its purpose: layer, data model, state machine, use case, or delivery.

Binary location: `.agents/skills/archify/archify/bin/archify.mjs`

```sh
# Check the toolchain
node .agents/skills/archify/archify/bin/archify.mjs doctor

# Validate only. No file is written.
node .agents/skills/archify/archify/bin/archify.mjs validate erd \
  docs/diagrams/src/system/system-data-model.erd.json --json

# Validate and write the viewer
node .agents/skills/archify/archify/bin/archify.mjs finalize erd \
  docs/diagrams/src/system/system-data-model.erd.json \
  docs/diagrams/system/system-data-model.html \
  --quality showcase --json
```

The viewer holds the `Export SVG` button. The CLI has no `export-svg` command.

When a check fails, remove hand-authored `fromSide`, `toSide`, `via`, and `labelAt` pins first. Those often force a segment through an unrelated node. Let the router plan the path, then read the reported metrics.

Diagram types and when to use them:

| Type | Use when the diagram answers |
|---|---|
| `architecture` | What components exist and how they connect |
| `sequence` | What calls what, in what order |
| `lifecycle` | What states exist and what transitions them |
| `dataflow` | Where does data come from and who consumes it |
| `workflow` | What approval gates or CI steps control a process |

### `open-code-review-delegate` (Code Review)

Run diff inspections before pushing or marking tasks done.

```sh
# List reviewable files
ocr delegate preview --format json

# Review rules for specific paths
ocr delegate rule --format json src/main/java/dev/hata/scheduler/appointment/

# Full review in delegate mode
ocr delegate run --format json
```

Fix every critical and high finding. Repeat from preview until a pass reports nothing critical or high.
Document every review run in `openspec/changes/<change-slug>/review.md`. Include finding lists, severity ranks, status results, reproduction steps, and re-check proofs.
All code reviews, Bruno integration tests, and Playwright E2E tests must pass. If any check fails, return to the loop (`code → format → test → review → fix → code → ...`).

### `agent-architecture-audit` (ECC)

Dispatch the `architect` subagent from ECC at States 3 and 4 (before implementation starts). It checks:
- Package independence violations
- Resource constraint evaluation correctness
- Outbox transactional boundary adherence
- SSE direct cookie authentication and single stream per phone

```sh
npx skills add affaan-m/ecc -s agent-architecture-audit
# Then invoke the audit agent against the feature plan
```

### `verification-loop` (ECC)

The verification loop skill enforces the testing ladder from Section 5. Load it before State 4 starts.

### `openwiki` (Code Wiki)

After the final build passes, generate the code wiki:
```sh
# openwiki generates searchable documentation from source
cd .agents/skills/openwiki
npm install
node index.js --root ../../../ --out ../../../openwiki/
```

---

## 8. Local Fix Commands

```sh
# Backend formatting
./gradlew :service-scheduler-server:spotlessApply

# Frontend (if present)
cd service-scheduler-ui
npm run lint:fix
npm run format
```

---

## 9. Runtime Safety

- Never start the JAR and the Docker Compose service on the same port at the same time. Both bind 8080.
- Never expose Actuator endpoints on a public network. They have no authentication.

---

<!-- OPENWIKI:START -->
## OpenWiki

This repository has a generated `openwiki/` evidence index. Use it for just-in-time context.

- Do not preload wikis at task start.
- Use `openwiki_search` when unfamiliar architecture or dependency behavior affects the task.
- Use `openwiki_read` for complete relevant sections.
- Treat source code and tests as authoritative. Wikis are supporting context.
<!-- OPENWIKI:END -->

## AI Collaboration Narrative

This narrative provides the complete account of the AI-assisted engineering workflow for the Unified Service Scheduler. Do not copy it anywhere else.

### 1. High-Level Strategy for Guiding the AI

The team prioritized architecture first and code second. The team wrote every architectural decision in `docs/SYSTEM_DESIGN.md` and approved it before writing code.

1. **Load the architect persona.** Read `.agents/agent/architect.md` before any design work. It provides a senior architect mindset for modularity, scalability, and security.
2. **Follow the Agentic SDLC.** Section 3 of this file enforces the order. The engineer writes no code before an approved OpenSpec proposal.
3. **Use the simplicity ladder.** The `karpathy-ponytail` skill enforces YAGNI thinking. Check if the feature must exist, or if standard libraries suffice.
4. **Write in Simplified Technical English.** The `asd-ste100` skill lints every document for active voice, sentence length, and clear vocabulary.

Five architectural decisions required deep engineering judgment:

- **Constraint engine.** A pure Java `ServiceConstraintEvaluator` evaluates relational metadata in microseconds inside the JVM. It guarantees zero native binary overhead and compile-time type safety.
- **Booking safety.** The architect replaced pessimistic row locking with PostgreSQL `EXCLUDE USING gist` constraints on appointments, bays, and technicians. The database owns the no-overlap rule. This removes fragile application locks.
- **Slot availability and caching.** The team rejected background jobs that pre-generate slot rows. The in-memory grid evaluates availability from current date/time to `current_date + max_advance_booking_days` (default 7 days). Bookings beyond 7 days return HTTP 400. Redis caches grids with a 1-hour TTL. The system enforces date-level cache eviction on bookings, cancellations, check-in slot adjustments, and database exclusion collisions.
- **Authentication and real-time streaming.** The architect replaced password authentication with phone OTP login. The service issues a JWT in an `httpOnly` cookie. Direct cookie authentication powers the SSE stream (`GET /api/v1/notifications/stream`) with single-stream enforcement per phone number.
- **Capacity and persistent auditing.** The team used the `bay_types` column in `workshop_bays` and the `technician_skills` table. An in-transaction staging queue (`outbox_events`) paired with a permanent audit log (`communication_logs`) guarantees reliable delivery and auditing.

### 2. Process for Verifying the Output

Every AI contribution passes a multi-tier pipeline. A task is complete only when it passes this pipeline.

- **ArchUnit** proves the domain packages stay independent.
- **`ocr delegate`** inspects the diff for null risks, resource leaks, and architecture compliance.
- **Bruno integration tests** prove each endpoint matches the OpenAPI contract against a running server.
- **Testcontainers** runs concurrent booking tests against a real PostgreSQL instance.
- **JaCoCo** gates test coverage at 100 percent on `@Service` classes and 80 percent overall.
- **Archify** gates each diagram. A failed check blocks the commit.

### 3. Process for Refining the Output

- **Review delegate.** `ocr delegate preview` inspects each diff. The engineer fixes every critical and high finding. A finding closes only when a regression test passes with the fix and fails without it.
- **Diagram repair.** A failing Archify check identifies the offending element. Removing hand-authored routing pins allows the router to plan clean paths.
- **Human gates.** HIL-1 approves specifications, HIL-2 approves diffs before push, and HIL-3 approves merges.
- **Language review.** The `asd-ste100` linter checks every document for active voice, sentence length, and approved vocabulary.

### 4. How the Team Ensured Final Quality

- **`./gradlew clean build` is the gate**, not a shorter command.
- **CI must pass on the pull request** before any merge.
- **Human gates at propose, approval, and merge** prevent unreviewed code from reaching `main`.
- **Never commit to `main`.** Every change lives on a `feature/<slug>` branch.
- **Every diagram link resolves.** A link audit runs across all markdown files.
