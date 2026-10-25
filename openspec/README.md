# OpenSpec Configuration

This directory contains the requirements and specifications for Unified Service Scheduler using the **OpenSpec Framework**.

## Directory Structure
- `specs/`: The living specification for the project. This acts as the single source of truth for all business logic, API contracts, and UX flows.
- `changes/`: The staging area for new features, bug fixes, or enhancements. Each new body of work gets a subdirectory here (e.g., `changes/phase-1-booking/`) containing a proposal, design, tasks, and delta specs.
- `config.yaml`: The project context and per-artifact rules. Update it when project constraints change.

## How to use with AI Agents
Do not write specs manually. Use your AI assistant's `/opsx:` commands:
1. `/opsx:explore` - Assistant explores the codebase and existing specs to prepare for a change.
2. `/opsx:propose` - Assistant writes a proposal and technical design for the next phase.
3. `/opsx:apply` - Assistant executes the tasks defined in the approved proposal.
4. `/opsx:archive` - Assistant merges the finished delta specs into the main `specs/` directory.

## Validation
- `openspec validate <change-slug> --strict` gates State 1 (propose) and State 8 (verify) in `AGENTS.md`.
- `openspec list --specs` shows the active spec capabilities.

## Next Steps
Use `/opsx:propose` to create the first change proposal for appointment booking with constraint evaluation (CR-1, CR-2). That change creates `service-scheduler-server` and books an appointment end to end.
