# ADR-0015: Database Choice and Environment Profiles

**Status**: Accepted | **Date**: 2025-11-29 | **Owners**: Justin Guida  

**Related**: ADR-0014, ADR-0019

## Context
- The app currently stores data in memory; production requires durable storage.
- We need a database that fits CRUD workloads, course constraints, and local dev ergonomics.
- Tests must remain reliable across platforms (CI + local).

## Decision
- Use Postgres as the primary database for dev/prod.
- Use Postgres Testcontainers for integration tests; allow H2 only for fast unit-level slices.
- Manage schema via Flyway migrations; keep profiles for `dev`, `test`, and `prod`.
- Convention: Flyway versioned scripts `V{timestamp}__description.sql`; credentials and JDBC URLs provided via environment variables.

## Consequences
- Postgres provides strong consistency, rich SQL, and wide tool support.
- Dual test strategy (SpringBootTests/MockMvc on Postgres via Testcontainers; H2 reserved for unit/slice tests) balances speed with fidelity.
- Requires containerized local setup (docker-compose/Testcontainers) and CI services.
- Adds migration discipline; breaking changes must ship with Flyway scripts.
