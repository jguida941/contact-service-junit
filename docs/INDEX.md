# CS320 Milestone 1 - Repository Index

Index for easy navigation of the CS320 Milestone 1 codebase.

## Planning & Requirements

| Path | Purpose |
|------|---------|
| [`REQUIREMENTS.md`](REQUIREMENTS.md) | **Master document**: scope, architecture, phases, checklist, code examples |
| [`ROADMAP.md`](ROADMAP.md) | Quick phase overview (points to REQUIREMENTS.md) |
| [`../agents.md`](../agents.md) | AI assistant entry point with constraints and stack decisions |

## Folders

| Path | Purpose |
|------|---------|
| [`../src/`](../src/) | Java source tree. `src/main/java/contactapp` contains application code; `src/test/java/contactapp` contains tests. |
| [`ci-cd/`](ci-cd/) | CI/CD design notes (pipeline plan plus `badges.md` for the badge helper). |
| [`requirements/contact-requirements/`](requirements/contact-requirements/) | Contact assignment requirements (milestone spec). |
| [`requirements/appointment-requirements/`](requirements/appointment-requirements/) | Appointment assignment requirements (object/service specs + checklist). |
| [`requirements/task-requirements/`](requirements/task-requirements/) | Task assignment requirements (task object/service specs + checklist). |
| [`architecture/`](architecture/) | Feature design briefs (e.g., Task entity/service plan with Definition of Done). |
| [`adrs/`](adrs/) | Architecture Decision Records index plus individual ADR files. |
| [`design-notes/`](design-notes/) | Personal design note hub with supporting explanations under `design-notes/notes/`. |
| [`logs/`](logs/) | Changelog and backlog. |

## Key Files

### Spring Boot Infrastructure
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/Application.java`](../src/main/java/contactapp/Application.java) | Spring Boot entrypoint (`@SpringBootApplication`). |
| [`../src/main/resources/application.yml`](../src/main/resources/application.yml) | Profile-based configuration (dev/test/prod) with actuator lockdown. |
| [`../src/test/java/contactapp/ApplicationTest.java`](../src/test/java/contactapp/ApplicationTest.java) | Spring Boot context load smoke test. |
| [`../src/test/java/contactapp/ActuatorEndpointsTest.java`](../src/test/java/contactapp/ActuatorEndpointsTest.java) | Actuator endpoint security verification tests. |
| [`../src/test/java/contactapp/ServiceBeanTest.java`](../src/test/java/contactapp/ServiceBeanTest.java) | Service bean presence and singleton verification tests. |

### Domain Layer (`contactapp.domain`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/domain/Contact.java`](../src/main/java/contactapp/domain/Contact.java) | `Contact` domain object with all field validation rules. |
| [`../src/main/java/contactapp/domain/Task.java`](../src/main/java/contactapp/domain/Task.java) | Task domain object mirroring Contact-style validation (id/name/description). |
| [`../src/main/java/contactapp/domain/Appointment.java`](../src/main/java/contactapp/domain/Appointment.java) | Appointment entity (id/date/description) with date-not-past validation. |
| [`../src/main/java/contactapp/domain/Validation.java`](../src/main/java/contactapp/domain/Validation.java) | Shared helper with not-blank, length, 10-digit, and date-not-past checks. |
| [`../src/test/java/contactapp/domain/ContactTest.java`](../src/test/java/contactapp/domain/ContactTest.java) | JUnit tests covering the `Contact` validation requirements. |
| [`../src/test/java/contactapp/domain/TaskTest.java`](../src/test/java/contactapp/domain/TaskTest.java) | JUnit tests for Task (trimming, invalid inputs, and atomic update behavior). |
| [`../src/test/java/contactapp/domain/AppointmentTest.java`](../src/test/java/contactapp/domain/AppointmentTest.java) | JUnit tests for Appointment entity validation and date rules. |
| [`../src/test/java/contactapp/domain/ValidationTest.java`](../src/test/java/contactapp/domain/ValidationTest.java) | Tests for the shared validation helper (length, numeric, and appointment date guards). |

### Service Layer (`contactapp.service`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/service/ContactService.java`](../src/main/java/contactapp/service/ContactService.java) | @Service bean with static in-memory store for contacts. |
| [`../src/main/java/contactapp/service/TaskService.java`](../src/main/java/contactapp/service/TaskService.java) | Task service with static in-memory store and validation/trim guards on IDs. |
| [`../src/main/java/contactapp/service/AppointmentService.java`](../src/main/java/contactapp/service/AppointmentService.java) | Appointment service with static in-memory store and ID trim/validation guards. |
| [`../src/test/java/contactapp/service/ContactServiceTest.java`](../src/test/java/contactapp/service/ContactServiceTest.java) | JUnit tests covering add, delete, and update behavior. |
| [`../src/test/java/contactapp/service/TaskServiceTest.java`](../src/test/java/contactapp/service/TaskServiceTest.java) | JUnit tests for TaskService singleton behavior and CRUD flows. |
| [`../src/test/java/contactapp/service/AppointmentServiceTest.java`](../src/test/java/contactapp/service/AppointmentServiceTest.java) | JUnit tests for AppointmentService singleton and CRUD flows. |

### API Layer (`contactapp.api`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/api/ContactController.java`](../src/main/java/contactapp/api/ContactController.java) | REST controller for Contact CRUD at `/api/v1/contacts`. |
| [`../src/main/java/contactapp/api/TaskController.java`](../src/main/java/contactapp/api/TaskController.java) | REST controller for Task CRUD at `/api/v1/tasks`. |
| [`../src/main/java/contactapp/api/AppointmentController.java`](../src/main/java/contactapp/api/AppointmentController.java) | REST controller for Appointment CRUD at `/api/v1/appointments`. |
| [`../src/main/java/contactapp/api/GlobalExceptionHandler.java`](../src/main/java/contactapp/api/GlobalExceptionHandler.java) | @RestControllerAdvice mapping exceptions to HTTP responses. |
| [`../src/main/java/contactapp/api/dto/ContactRequest.java`](../src/main/java/contactapp/api/dto/ContactRequest.java) | Contact request DTO with Bean Validation. |
| [`../src/main/java/contactapp/api/dto/ContactResponse.java`](../src/main/java/contactapp/api/dto/ContactResponse.java) | Contact response DTO. |
| [`../src/main/java/contactapp/api/dto/TaskRequest.java`](../src/main/java/contactapp/api/dto/TaskRequest.java) | Task request DTO with Bean Validation. |
| [`../src/main/java/contactapp/api/dto/TaskResponse.java`](../src/main/java/contactapp/api/dto/TaskResponse.java) | Task response DTO. |
| [`../src/main/java/contactapp/api/dto/AppointmentRequest.java`](../src/main/java/contactapp/api/dto/AppointmentRequest.java) | Appointment request DTO with Bean Validation and @FutureOrPresent. |
| [`../src/main/java/contactapp/api/dto/AppointmentResponse.java`](../src/main/java/contactapp/api/dto/AppointmentResponse.java) | Appointment response DTO. |
| [`../src/main/java/contactapp/api/dto/ErrorResponse.java`](../src/main/java/contactapp/api/dto/ErrorResponse.java) | Standard error response DTO. |
| [`../src/main/java/contactapp/api/exception/ResourceNotFoundException.java`](../src/main/java/contactapp/api/exception/ResourceNotFoundException.java) | Exception for 404 Not Found responses. |
| [`../src/main/java/contactapp/api/exception/DuplicateResourceException.java`](../src/main/java/contactapp/api/exception/DuplicateResourceException.java) | Exception for 409 Conflict responses. |
| [`../src/test/java/contactapp/ContactControllerTest.java`](../src/test/java/contactapp/ContactControllerTest.java) | MockMvc integration tests for Contact API (30 tests). |
| [`../src/test/java/contactapp/TaskControllerTest.java`](../src/test/java/contactapp/TaskControllerTest.java) | MockMvc integration tests for Task API (21 tests). |
| [`../src/test/java/contactapp/AppointmentControllerTest.java`](../src/test/java/contactapp/AppointmentControllerTest.java) | MockMvc integration tests for Appointment API (20 tests). |
| [`../src/test/java/contactapp/GlobalExceptionHandlerTest.java`](../src/test/java/contactapp/GlobalExceptionHandlerTest.java) | Unit tests for GlobalExceptionHandler methods (4 tests). |

### Build & Configuration
| Path | Description |
|------|-------------|
| [`../pom.xml`](../pom.xml) | Maven project file with Spring Boot 3.4.12 parent and dependencies. |
| [`../config/checkstyle/checkstyle.xml`](../config/checkstyle/checkstyle.xml) | Custom Checkstyle rules enforced in CI. |
| [`../config/owasp-suppressions.xml`](../config/owasp-suppressions.xml) | Placeholder suppression list for OWASP Dependency-Check. |
| [`../scripts/ci_metrics_summary.py`](../scripts/ci_metrics_summary.py) | Prints the QA metrics table (tests/coverage/mutations/dependencies) in GitHub Actions. |
| [`../scripts/serve_quality_dashboard.py`](../scripts/serve_quality_dashboard.py) | Launches a local server for `target/site/qa-dashboard` when reading downloaded artifacts. |
| [`architecture/2025-11-19-task-entity-and-service.md`](architecture/2025-11-19-task-entity-and-service.md) | Task entity/service plan with Definition of Done and phase breakdown. |
| [`architecture/2025-11-24-appointment-entity-and-service.md`](architecture/2025-11-24-appointment-entity-and-service.md) | Appointment entity/service implementation record. |
| [`adrs/README.md`](adrs/README.md) | ADR index summarizing ADR-0001 through ADR-0020. |
| [`design-notes/README.md`](design-notes/README.md) | Landing page for informal design notes (individual topics in `design-notes/notes/`). |
| [`logs/backlog.md`](logs/backlog.md) | Backlog for reporting and domain enhancements. |
| [`logs/CHANGELOG.md`](logs/CHANGELOG.md) | Project changelog. |
| [`../.github/workflows`](../.github/workflows) | GitHub Actions pipelines (CI, release packaging, CodeQL). |

## Milestone Requirements (Original Assignments)

| Path | Description |
|------|-------------|
| [`requirements/contact-requirements/requirements.md`](requirements/contact-requirements/requirements.md) | Contact assignment requirements. |
| [`requirements/contact-requirements/requirements_checklist.md`](requirements/contact-requirements/requirements_checklist.md) | Contact requirements checklist. |
| [`requirements/appointment-requirements/requirements.md`](requirements/appointment-requirements/requirements.md) | Appointment assignment requirements. |
| [`requirements/appointment-requirements/requirements_checklist.md`](requirements/appointment-requirements/requirements_checklist.md) | Appointment requirements checklist. |
| [`requirements/task-requirements/requirements.md`](requirements/task-requirements/requirements.md) | Task assignment requirements (task object/service). |
| [`requirements/task-requirements/requirements_checklist.md`](requirements/task-requirements/requirements_checklist.md) | Task requirements checklist. |
