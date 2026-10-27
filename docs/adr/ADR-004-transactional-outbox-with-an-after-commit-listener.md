# ADR-004: Transactional Outbox with an AFTER_COMMIT Listener

- **Status:** Accepted
- **Date:** 2026-10-23

## Context

Webhook and notification dispatch must survive a JVM crash after the database commit.

## Decision

Write the OUTBOX_EVENTS row in the same transaction as the appointment mutation. A background poller reads the rows with FOR UPDATE SKIP LOCKED every 5 seconds.

## Reason

The delivery is at-least-once. A rollback creates no phantom notification. SSE push bypasses the poller with a transactional event listener for AFTER_COMMIT, which gives instant updates.

## Consequences

### Positive

The delivery is at-least-once. A rollback creates no phantom notification. SSE push bypasses the poller with a transactional event listener for AFTER_COMMIT, which gives instant updates.

### Negative

Email and SMS delivery waits up to 5 seconds. SSE delivery is instant.

### Alternatives Considered

- Publish to a message broker in the same transaction
- Send the webhook inside the request thread

