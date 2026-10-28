## ADDED Requirements

### Requirement: R-SPRING-ADMIN-1

The scheduler MUST register with the Spring Boot Admin server over the internal network.

#### Scenario: registration at startup

Given the `admin` profile is active and the Admin server URL is configured,
when the application starts,
then the Spring Boot Admin client registers the application with the server.
