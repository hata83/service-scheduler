# ADR-009: ArchUnit for Package Independence

- **Status:** Accepted
- **Date:** 2026-10-23

## Context

Each feature package must not import another feature package.

## Decision

Place the domain packages under dev.hata.scheduler: appointment, resource, customer, vehicle, notification, scheduling, and common. An ArchUnit test rejects a cross-feature import.

## Reason

Package independence allows independent evolution. It supports a later microservice extraction.

## Consequences

### Positive

Package independence allows independent evolution. It supports a later microservice extraction.

### Negative

Cross-domain communication needs a Spring application event. A synchronous cross-domain call needs a shared interface in common.

### Alternatives Considered

- Accept cross-package imports and review them manually
- Use a layered architecture with a shared domain module

