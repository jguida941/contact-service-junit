# ADR-0021: REST API Implementation

## Status
Accepted

## Context
Phase 2 of the roadmap calls for implementing REST API endpoints for the contact service. Following the API contract defined in ADR-0016, we need to:
- Create REST controllers for Contact, Task, and Appointment resources
- Implement request/response DTOs with Bean Validation
- Add global exception handling for consistent error responses
- Expose OpenAPI/Swagger UI documentation

The challenge is to integrate the HTTP layer while preserving domain validation as the source of truth.

## Decision

### Controllers
Three `@RestController` classes expose CRUD endpoints at `/api/v1/{resource}`:
- `ContactController` at `/api/v1/contacts`
- `TaskController` at `/api/v1/tasks`
- `AppointmentController` at `/api/v1/appointments`

Each controller injects the corresponding service via constructor injection and delegates to domain objects.

### DTOs with Bean Validation
Request DTOs use Jakarta Bean Validation annotations:
- `@NotBlank` for required strings
- `@Size(min, max)` for length constraints
- `@Pattern` for format validation (phone numbers)
- `@FutureOrPresent` for date validation (appointments)

**Key design decision**: DTO constraints use static imports from `Validation.MAX_*` constants to stay in sync with domain rules. This ensures that Bean Validation and domain validation enforce identical limits.

### Two-Layer Validation Strategy
```
HTTP Request → Bean Validation (DTO) → Domain Constructor → Service Layer
```
- Bean Validation catches invalid input early with user-friendly messages
- Domain validation acts as a backup layer (same rules, same constants)
- Controllers construct domain objects directly, not duplicate validation logic

### Global Exception Handler
`GlobalExceptionHandler` with `@RestControllerAdvice` maps exceptions to HTTP responses:
- `MethodArgumentNotValidException` → 400 Bad Request (Bean Validation failures)
- `IllegalArgumentException` → 400 Bad Request (domain validation failures)
- `ResourceNotFoundException` → 404 Not Found
- `DuplicateResourceException` → 409 Conflict
- `HttpMessageNotReadableException` → 400 Bad Request (malformed JSON)

All errors return consistent JSON: `{ "message": "..." }`

### OpenAPI/Swagger UI
springdoc-openapi dependency auto-generates API documentation:
- Swagger UI at `/swagger-ui.html`
- OpenAPI spec at `/v3/api-docs`

### Testing Strategy
MockMvc integration tests cover:
- Happy path CRUD operations (201, 200, 204 responses)
- Bean Validation boundary tests (exact max length, one-over-max)
- 404 Not Found scenarios
- 409 Conflict (duplicate ID)
- Date validation (past date rejection for appointments)
- Malformed JSON handling

Test isolation uses reflection to access package-private `clearAll*()` methods.

## Consequences

### Positive
- API contract matches ADR-0016 specification
- Bean Validation provides user-friendly error messages at HTTP boundary
- Domain validation remains source of truth (no rule duplication)
- Swagger UI enables API exploration and testing
- 71 controller tests (30 Contact + 21 Task + 20 Appointment) provide comprehensive coverage

### Negative
- Controller tests require reflection hack for test isolation
- Two validation layers mean some checks run twice (acceptable trade-off for defense-in-depth)

### Neutral
- Total test count: 261 (including 4 GlobalExceptionHandlerTest, 13 service lookup method tests)
- Build time unchanged (tests run quickly)
- Controllers use service-level lookup methods (`getAllXxx()`, `getXxxById()`) instead of `getDatabase()` for better encapsulation

## Related ADRs
- ADR-0016: API Style and Contract (defines the API contract this implements)
- ADR-0020: Spring Boot Scaffold (provides the foundation)
