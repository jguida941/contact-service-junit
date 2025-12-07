# CS320 Original Requirements

> **Historical Reference**: These are the original SNHU CS320 assignment requirements that the project was built to satisfy.

## Contents

| Directory | Entity | Description |
|-----------|--------|-------------|
| [contact-requirements/](contact-requirements/) | Contact | Original Contact class specification |
| [task-requirements/](task-requirements/) | Task | Original Task class specification |
| [appointment-requirements/](appointment-requirements/) | Appointment | Original Appointment class specification |

## Relationship to Current Project

The project has evolved **far beyond** these original requirements:

| Original Requirement | Current Implementation |
|---------------------|------------------------|
| In-memory Contact/Task/Appointment | Full Spring Boot REST API with JPA persistence |
| Basic validation | Two-layer validation (Bean Validation + Domain) |
| Simple CRUD | Multi-tenant CRUD with JWT authentication |
| No UI | Full React 19 SPA with Tailwind CSS |
| No persistence | PostgreSQL with Flyway migrations |
| No security | JWT + CSRF + Rate Limiting + Security Headers |

## Current Documentation

For current project requirements and implementation details, see:
- [`../REQUIREMENTS.md`](../REQUIREMENTS.md) - Master requirements document
- [`../roadmaps/ROADMAP.md`](../roadmaps/ROADMAP.md) - Implementation phases
- [`../adrs/README.md`](../adrs/README.md) - All 54 Architecture Decision Records
