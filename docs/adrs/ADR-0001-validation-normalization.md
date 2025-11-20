# ADR-0001: Validation Normalization Policy

Status: Accepted
Date: 2025-11-19
Owners: Justin Guida
Related: src/main/java/contactapp/Contact.java, src/main/java/contactapp/Validation.java, docs/logs/CHANGELOG.md

## Context
- The milestone requirements mandate strict constraints for identifiers, names, phone numbers, and addresses.
- Early iterations duplicated validation logic directly inside constructors and setters, which led to inconsistent trimming and exception text.
- Mutation testing exposed gaps whenever the validation flow or error messages diverged between constructor and setter paths.

## Decision
- Centralize all field validation inside `contactapp.Validation`, exposing `validateNotBlank`, `validateLength`, and `validateNumeric10`.
- Trim incoming strings before storage so persisted state matches what validation evaluates (`Contact` now trims IDs, names, and addresses before assigning).
- Throw `IllegalArgumentException` with consistent messages such as `"<field> must not be null or blank"` or `"<field> length must be between X and Y"` so tests can assert exact text and callers get actionable feedback.
- Reuse the same helpers everywhere (constructors, setters, and service entry points) to guarantee one enforcement pipeline.

## Consequences
- Validation rules now live in one class, which simplifies maintenance and lets mutation testing focus on a single code path.
- Trimming before storage keeps persisted IDs/names/addresses normalized, making equality checks and future lookups deterministic.
- Tests can assert precise exception messages because every failure route emits the same strings.
- Any future domain (e.g., Task) inherits the same helpers, but we must ensure new max-length rules still fit the shared API; otherwise, Validation may need additional methods.

## Alternatives considered
- **Inline validation per class** – rejected because duplicating the logic caused message drift and made mutation coverage brittle.
- **Accept raw user input** – rejected because keeping whitespace or partially valid strings would violate requirements and lead to confusing state.
- **Introduce a third-party validation framework** – rejected as overkill for a small project and harder to run inside the constrained CS320 tooling.
