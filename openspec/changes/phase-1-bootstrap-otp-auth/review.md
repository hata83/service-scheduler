# Phase 1 Review — Layered Package Refactor

- **Date:** 2026-10-26
- **Branch:** feature/phase-1-bootstrap-otp-auth
- **Commit:** baf1f32 (refactor) upon 6127f04 (bootstrap)
- **Reviewer:** AI (agent-architecture-audit + open-code-review-delegate)

## Findings (before fix)

- Critical: `common/` was a big ball of mud (exceptions + filters + Redis middleware).
- Critical: `auth/` held entities, services, controllers, and adapters in one folder.
- High: The use case (`AuthService`) depended on `RedisWindowCounter` and `IdempotencyStorage` through `common/`.
- High: `PackageIndependenceTest` only enforced feature-to-feature independence, not the layer direction.
- Medium: DTO records nested in the controller made reuse impossible.

## Fixes applied

- Created `auth/domain/`, `auth/application/`, `auth/dto/`, `auth/adapter/`; `common/{config,exception,port,web}/`; `infra/{redis,logging,monitoring}/`; `customer/domain/`, `customer/application/`; `dealership/domain/`, `dealership/application/`.
- Extracted ports (`OtpStorage`, `SessionStore`, `IdempotencyStore`, `WindowCounter`) to the feature `application/` or `common/port/`; created `Redis*` adapters in `infra/redis/`.
- Split DTOs out of `AuthController` into `auth/dto/`.
- Updated `PackageIndependenceTest` with four direction rules (feature↔feature, common↔feature, feature↔infra, infra→port).
- Updated `AGENTS.md`, `README.md`, `SYSTEM_DESIGN.md` §8, `openspec/config.yaml`, `docs/adr/ADR-009`, new `ADR-014`, and the package-layer diagram.
- Added `BuildIdFilter` (lost in parallel session reset) back to `infra/monitoring/`.

## Verification

- `./gradlew clean build`: PASS (Spotless, Checkstyle, PMD, JaCoCo 92.3% line / 100% AuthService).
- `./gradlew test`: 134 passed, 3 failed (idempotency mock — fixed by stubbing `slot()` in `AuthServiceTest`).
- Bruno: 49/49 PASS.
- Playwright: 20/20 PASS.
- ArchUnit: PASS (all 4 rules).
- Archify diagram (`package-layers.html`): PASS (validate + deliver + check + browser-check, 0 crossings, 0 detours beyond budget).
- `X-Build-Id` header: confirmed working (`8a5f...`).

## Open items (none blocking)

- `docs/diagrams/index.html` dashboard entry added. The diagram SVG exported.
- `opencode` version wasn't needed for this work — all tooling came from the repo scripts (`tools/`) and the `archify` binary inside `.agents/skills/archify/`.