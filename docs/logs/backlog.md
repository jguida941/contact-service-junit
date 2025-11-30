# Backlog

Tracking potential improvements beyond the CS320 requirements.
We move items here once they no longer fit inside the README so the project
landing page stays focused.

## Deferred Decisions
- UI component library selection for the new UI (e.g., utility CSS + custom components vs. lightweight kit).
- OAuth2/SSO upgrade path beyond initial JWT/basic auth.
- WAF/API gateway selection for production deployment.
- Secrets management tooling for prod (vault/secret manager implementation details).

## Validation Layer Improvements (Phase 2+)
Consider these extensions when adding API/persistence/UI while keeping the current fail-fast behavior for domain invariants:

- **Time abstraction**: Keep using the `Clock` overload for date checks; consider a simple `TimeProvider` wrapper so all time-based validations are deterministic and testable.
- **Domain-specific wrappers**: Add tiny helpers like `validatePhone(String phone)` that call `validateDigits(..., 10)` to avoid passing lengths around and make intent clearer.
- **Pattern/range helpers**: If adding new fields, introduce generic `validatePattern(String value, String label, Pattern p)` and `validateRange(int value, String label, int min, int max)` so future rules don't sprout ad-hoc checks elsewhere.
- **Error coding**: When exposing REST, consider a custom `ValidationException` that carries a code/key to map cleanly to JSON error payloads; keep `IllegalArgumentException` in the domain if preferred.
- **Bean Validation bridge**: For DTOs in Spring Boot, use Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, etc.) and map to domain objects that still use the `Validation` helpers, keeping API-level and domain-level validation consistent.

## Domain Enhancements (Phase 2+)
- **Phone internationalization**: Current `validateDigits(..., 10)` enforces US phone numbers (exactly 10 digits). Consider supporting international formats via E.164 or libphonenumber when the app needs global reach.

## Reporting & Observability
- Future observability enhancements (distributed tracing backends, log shipping) to be considered post-MVP.

## Legacy Singleton Decommission (Future)
- Once every caller uses Spring DI, delete `getInstance()` and the in-memory store implementations entirely.
- Remove the legacy singleton tests and compatibility guards after the DI-only migration is complete.
- Update documentation/ADRs at that time to reflect the fully managed Spring context (no static singletons).

## Controller/Service Encapsulation ✅ IMPLEMENTED
Service-level lookup methods have been implemented:
- **Service-level lookup methods**: ✅ Added `getAllContacts()`/`getContactById(String)` to `ContactService`, `getAllTasks()`/`getTaskById(String)` to `TaskService`, `getAllAppointments()`/`getAppointmentById(String)` to `AppointmentService`. Controllers now use these instead of `getDatabase()`.
- **Consistent ID normalization**: ✅ All lookup methods validate/trim IDs at the service layer.
- **Path vs body ID validation**: Deferred - not a CS320 requirement. The path ID is canonical; body ID is currently ignored on PUT endpoints.

## Date/Time API Migration (Future)
- **java.time migration**: Domain layer uses `java.util.Date` per existing design. Future phases could migrate to `java.time.LocalDateTime`/`ZonedDateTime` for immutability and better timezone handling. Would require domain, service, DTO, and test changes.

## Exception Handling Refinement (Future)
- **Custom validation exception**: `GlobalExceptionHandler` catches all `IllegalArgumentException`. Consider introducing `DomainValidationException` for finer-grained handling, separating domain validation errors from unexpected framework exceptions.

## Dependency Risks (to track)
- ~~**commons-lang3 3.17.0** flagged by Dependency-Check (CVE-2025-48924).~~ **FIXED**: Upgraded to 3.20.0.
- ~~**swagger-ui DOMPurify 3.1.6** (transitive via swagger-ui 5.18.2) flagged with CVE-2025-26791.~~ **FIXED**: Upgraded springdoc-openapi to 2.8.7.
