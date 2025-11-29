# ADR-0020: Spring Boot Scaffold

## Status
Accepted

## Context
Phase 1 of the roadmap calls for converting the in-memory contact service library into a Spring Boot application. The existing codebase has:
- Domain classes (`Contact`, `Task`, `Appointment`) with validation rules
- Singleton services with `ConcurrentHashMap` storage
- Comprehensive test suite with 100% mutation coverage

The challenge is to introduce Spring Boot infrastructure without breaking existing tests or validation guarantees.

## Decision

### Spring Boot Version
Use Spring Boot 3.4.12 (latest 3.4.x patch) via parent POM to inherit dependency management, plugin configuration, and sensible defaults.

### Package Structure
Reorganize into layered architecture:
- `contactapp.domain` - Domain entities (`Contact`, `Task`, `Appointment`, `Validation`)
- `contactapp.service` - Business logic (`ContactService`, `TaskService`, `AppointmentService`)
- `contactapp.api` - REST controllers and DTOs (Phase 2 complete)
- `contactapp.persistence` - Repository interfaces (Phase 3, currently empty)

### Service Bean Wiring
- Add `@Service` annotation to existing services for Spring DI
- Keep `getInstance()` singleton accessor for backward compatibility with existing tests
- Make the backing `ConcurrentHashMap` static so all access paths share the same data store regardless of how many instances are created
- Public no-arg constructor registers `this` as the static singleton only if no instance exists (thread-safe via `synchronized` block)
- Package-private `clearAll*()` methods remain accessible to tests in the same package

### Configuration
- `application.yml` with profile-based settings (dev/test/prod)
- Actuator endpoints locked down to health/info only per OWASP guidelines
- Health probes enabled for Kubernetes readiness/liveness

### Smoke Tests
- `ApplicationTest` - Verifies context loads without errors
- `ActuatorEndpointsTest` - Confirms health/info exposed, other endpoints blocked
- `ServiceBeanTest` - Proves beans are injectable and singletons

### Visibility Changes
- Made `copy()` methods public so services can call them across packages
- Domain classes remain in separate package from services (proper layering)

## Consequences

### Positive
- Spring Boot provides auto-configuration, embedded Tomcat, and actuator out of the box
- Profile-based configuration simplifies environment-specific settings
- REST controllers and DTOs implemented (Phase 2 complete); foundation in place for JPA persistence (Phase 3)
- Existing tests continue to pass without modification
- New smoke tests catch Spring wiring issues early

### Negative
- Added 10 Spring Boot dependencies to the classpath
- Slightly longer test startup due to context loading
- `copy()` methods are now public API (acceptable trade-off for proper layering)

### Neutral
- Test count increased (Spring Boot smoke tests + ValidationTest additions)
- Build time increased marginally (~2 seconds for context loading)

## Alternatives Considered

### Micronaut or Quarkus
Rejected because Spring Boot is the most widely adopted framework with the richest ecosystem, best documentation, and largest talent pool. ADR-0014 already selected Spring Boot.

### Keep Flat Package Structure
Rejected because layered packages enforce architectural boundaries and make it clear which classes belong to which layer. The slight inconvenience of updating imports is outweighed by long-term maintainability.

### Remove Singleton Pattern
Rejected because existing tests rely on `getInstance()`. Keeping both patterns (Spring DI + singleton accessor) ensures backward compatibility. Can be deprecated in future phases once all code uses DI.

## Related ADRs
- ADR-0014: Backend Framework and Persistence Stack (selected Spring Boot)
- ADR-0015: Database Choice and Environment Profiles (informs profile configuration)
