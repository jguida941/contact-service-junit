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
