# ADR-006: RFC 7807 Problem Details for All Error Responses

- **Status:** Accepted
- **Date:** 2026-10-23

## Context

An API consumer needs structured and actionable error information.

## Decision

Return Content-Type application/problem+json for every 4xx and 5xx response.

## Reason

RFC 7807 is the industry standard. SpringDoc documents the format. The frontend and webhook consumers handle errors in one way.

## Consequences

### Positive

RFC 7807 is the industry standard. SpringDoc documents the format. The frontend and webhook consumers handle errors in one way.

### Negative

The response body is longer than a short error object. The API quality gain justifies the length.

### Alternatives Considered

- A custom error envelope
- Plain text messages
- An errors array

