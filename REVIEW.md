# Code Review Instructions

## Alibaba Open Code Review (OCR) Delegate

All pull request reviews must use the Alibaba Open Code Review (OCR) delegate skill.

### Installation

Install the OCR CLI before running any review:

```sh
npm install -g @alibaba-group/open-code-review
```

Verify the installation:

```sh
ocr --version
```

### Review Process

Run the following steps for every pull request review:

1. Preview reviewable files:
   ```sh
   ocr delegate preview --format json
   ```

2. Fetch review rules grouped by file pattern:
   ```sh
   ocr delegate rule --format json <path>
   ```

3. Inspect diffs for every entry in `reviewable_files`:
   ```sh
   git --no-pager diff --no-ext-diff --no-textconv --no-color HEAD -- "<path>"
   ```

4. Fix every critical and high finding. Repeat from step 1 until a pass reports nothing critical or high.

5. Document every review run in `openspec/changes/<change-slug>/review.md`. Include:
   - `total_files`, `reviewed_files`, `skipped_files`, `coverage_rate`
   - Finding lists with severity ranks
   - Reproduction steps
   - Re-check proofs

### Review Scope

Review all Java, Kotlin, TypeScript, YAML, SQL, and Dockerfile changes. Exclude generated files, binary assets, and dependency lock files.

### Severity Gates

- **Critical:** Must fix before merge. Blocks the PR.
- **High:** Must fix before merge. Blocks the PR.
- **Medium:** Fix or document a justified exception.
- **Low:** Informational. Fix if trivial.

### Architecture Constraints to Enforce

1. Package Independence: Domain packages under `dev.hata.scheduler.*` must never import each other.
2. Java 25 Virtual Threads: Never use `synchronized` in hot paths. Use `ReentrantLock`.
3. Pure Java Constraint Evaluator: Never introduce external native binary rule engines.
4. PostgreSQL GiST Booking Safety: Trust `EXCLUDE USING gist` constraints. No redundant locks.
5. Transactional Outbox: Write `outbox_events` in the same transaction. Dispatch `AFTER_COMMIT`.
6. Mandatory Idempotency Keys: All mutating endpoints require `X-Idempotency-Key` header.
7. OpenAPI 3.1 and RFC 7807: All endpoints need SpringDoc annotations and `application/problem+json` errors.

### Code Quality Standards

- Spotless + Palantir Java Format 2.71 for Java formatting
- Checkstyle 10: no wildcard imports, 120-char lines
- PMD 7: code smells, copy-paste detection
- ArchUnit 1.x: package independence enforcement
- JaCoCo: 100% on `@Service` classes, 80% overall

### Simplified Technical English (ASD-STE100)

All review comments must follow ASD-STE100 rules:
- Active voice
- Sentences with 25 words or fewer
- No semicolons
- No marketing adjectives
- Use the verb, not the noun
