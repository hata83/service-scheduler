# ADR-001: Spring Boot 4.1.1 MVC with Virtual Threads

- **Status:** Accepted
- **Date:** 2026-10-23

## Context

The project needs a Java framework for REST, SSE, JPA, and OpenAPI.

## Decision

Use Spring Boot 4.1.1 with Spring MVC. Set spring.threads.virtual.enabled=true.

## Reason

Virtual threads remove the only throughput advantage WebFlux had for I/O-bound operations. The Spring ecosystem provides JPA, Flyway, SpringDoc, Actuator, and Resilience4j.

## Consequences

### Positive

Virtual threads remove the only throughput advantage WebFlux had for I/O-bound operations. The Spring ecosystem provides JPA, Flyway, SpringDoc, Actuator, and Resilience4j.

### Negative

Memory use is about 200MB. Quarkus uses about 120MB. The difference does not matter for a long-lived container. A synchronized block pins a virtual thread. Use ReentrantLock in hot paths.

### Alternatives Considered

- Spring Boot 4.1.1 WebFlux
- Quarkus 3 with RESTEasy Reactive

