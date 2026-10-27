# Spring Boot Admin

## Context

The service exposes Actuator endpoints. A Spring Boot Admin client is wired to
register the application with the centralized Admin server for health and
runtime monitoring.

## Motivation

Operators need a read-only observability surface for the scheduler. Spring Boot
Admin provides health, metrics, and environment views without exposing
Actuator on a public network (design R9).

## Solution

Add the `spring-boot-admin` dependency and client configuration. The client
registers with the Admin server over the internal network. Actuator endpoints
remain behind a non-public profile.

## Why

Operators need a read-only observability surface for the scheduler. Actuator endpoints must stay behind a non-public profile to avoid unauthenticated exposure (environment, threads, heap).
