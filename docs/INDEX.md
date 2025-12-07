# CS320 Milestone 1 - Repository Index

Index for easy navigation of the CS320 Milestone 1 codebase.

## Planning & Requirements

| Path | Purpose |
|------|---------|
| [`REQUIREMENTS.md`](REQUIREMENTS.md) | **Master document**: scope, architecture, phases, checklist, code examples |
| [`roadmaps/ROADMAP.md`](roadmaps/ROADMAP.md) | Quick phase overview (points to REQUIREMENTS.md) |
| [`roadmaps/FUTURE_ROADMAP.md`](roadmaps/FUTURE_ROADMAP.md) | Future roadmap: evolving into a Jira-like project management platform |
| [`roadmaps/README.md`](roadmaps/README.md) | Roadmap directory index with archived implementation plans |

## Folders

| Path | Purpose |
|------|---------|
| [`../src/`](../src/) | Java source tree. `src/main/java/contactapp` contains application code; `src/test/java/contactapp` contains tests. |
| [`../ui/contact-app/`](../ui/contact-app/) | React UI (Vite + React 19 + TypeScript + Tailwind CSS v4 + shadcn/ui). |
| [`ci-cd/`](ci-cd/) | CI/CD design notes (pipeline plan plus `badges.md` for the badge helper). |
| [`cs320-requirements/`](cs320-requirements/) | Original CS320 assignment requirements (contact, task, appointment specs). |
| [`showcase/`](showcase/) | Project showcase materials for recruiters and portfolio. |
| [`architecture/`](architecture/) | Feature design briefs (e.g., Task entity/service plan with Definition of Done). |
| [`adrs/`](adrs/) | Architecture Decision Records: CS320 Foundation + Production Auth + Frontend fixes + Security Audit (ADR-0001-0054). |
| [`design-notes/`](design-notes/) | Personal design note hub with supporting explanations under `design-notes/notes/`. |
| [`logs/`](logs/) | Changelog, backlog, and code review reports. |
| [`test-plan-sdlc-audit-v2.1.0.md`](test-plan-sdlc-audit-v2.1.0.md) | Comprehensive test plan and SDLC audit report (1107 tests, 90% coverage analysis). |
| [`operations/`](operations/) | Operations documentation (Docker setup, Actuator endpoints, deployment guides). |
| [`security-reviews/`](security-reviews/) | Security audit reports and vulnerability assessments. |

## CLI Tool

| Path | Purpose |
|------|---------|
| [`../scripts/run`](../scripts/run) | Shell shim entry point |
| [`../scripts/cs_cli.py`](../scripts/cs_cli.py) | Main CLI implementation (typer-based) |
| [`../scripts/runtime_env.py`](../scripts/runtime_env.py) | Environment configuration helpers |
| [`design-notes/notes/clitool.md`](design-notes/notes/clitool.md) | CLI design document |

> See [README CLI Tool section](../README.md#cli-tool) for usage.

## Security

| Path | Purpose |
|------|---------|
| [`adrs/ADR-0052-production-auth-system.md`](adrs/ADR-0052-production-auth-system.md) | Production-grade auth system (ADR) |
| [`agents.md`](agents.md) | AI implementation runbook for ADR-0052 (batch execution guidance) |
| [`security-reviews/`](security-reviews/) | Security audit reports |
| [`../src/main/java/contactapp/security/`](../src/main/java/contactapp/security/) | JWT auth, User entity, SecurityConfig |

## Key Files

### Spring Boot Infrastructure
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/Application.java`](../src/main/java/contactapp/Application.java) | Spring Boot entrypoint (`@SpringBootApplication`). |
| [`../src/main/resources/application.yml`](../src/main/resources/application.yml) | Multi-document profile config (dev/test/integration/prod + Flyway/JPA) with `jwt.expiration=30m`, explicit `app.auth.cookie.secure` overrides, and prod-only Secure-cookie requirements. |
| [`../src/main/resources/db/migration/common/V1__create_contacts_table.sql`](../src/main/resources/db/migration/common/V1__create_contacts_table.sql) | Flyway migration creating contacts table with id, firstName, lastName, phone, address columns (shared by all DBs). |
| [`../src/main/resources/db/migration/common/V2__create_tasks_table.sql`](../src/main/resources/db/migration/common/V2__create_tasks_table.sql) | Flyway migration creating tasks table with id, name, description columns. |
| [`../src/main/resources/db/migration/common/V3__create_appointments_table.sql`](../src/main/resources/db/migration/common/V3__create_appointments_table.sql) | Flyway migration creating appointments table with id, appointmentDate, description columns. |
| [`../src/main/resources/db/migration/common/V4__create_users_table.sql`](../src/main/resources/db/migration/common/V4__create_users_table.sql) | Flyway migration creating users table for authentication with unique constraints. |
| [`../src/main/resources/db/migration/h2/V5__add_user_id_columns.sql`](../src/main/resources/db/migration/h2/V5__add_user_id_columns.sql) | H2-specific migration adding `user_id` FKs plus placeholder-driven system user seed. |
| [`../src/main/resources/db/migration/postgresql/V5__add_user_id_columns.sql`](../src/main/resources/db/migration/postgresql/V5__add_user_id_columns.sql) | Postgres migration adding `user_id` FKs with conditional DDL checks. |
| [`../src/main/resources/db/migration/h2/V6__surrogate_keys_and_unique_constraints.sql`](../src/main/resources/db/migration/h2/V6__surrogate_keys_and_unique_constraints.sql) | H2 migration introducing surrogate numeric IDs + `(id,user_id)` unique constraints. |
| [`../src/main/resources/db/migration/postgresql/V6__surrogate_keys_and_unique_constraints.sql`](../src/main/resources/db/migration/postgresql/V6__surrogate_keys_and_unique_constraints.sql) | Postgres migration for surrogate numeric IDs with FK pre-checks and conditional DDL. |
| [`../src/main/resources/db/migration/common/V8__create_projects_table.sql`](../src/main/resources/db/migration/common/V8__create_projects_table.sql) | Flyway migration creating projects table with id/name/description/status columns (ADR-0045). |
| [`../src/test/java/contactapp/ApplicationTest.java`](../src/test/java/contactapp/ApplicationTest.java) | Spring Boot context load smoke test. |
| [`../src/test/java/contactapp/ActuatorEndpointsTest.java`](../src/test/java/contactapp/ActuatorEndpointsTest.java) | Actuator endpoint security verification tests. |
| [`../src/test/java/contactapp/ServiceBeanTest.java`](../src/test/java/contactapp/ServiceBeanTest.java) | Service bean presence and singleton verification tests. |
| [`../src/test/java/contactapp/LegacySingletonUsageTest.java`](../src/test/java/contactapp/LegacySingletonUsageTest.java) | Regression test guarding against new `getInstance()` references outside the approved legacy tests. |

### Domain Layer (`contactapp.domain`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/domain/Contact.java`](../src/main/java/contactapp/domain/Contact.java) | `Contact` domain object with all field validation rules. |
| [`../src/main/java/contactapp/domain/Task.java`](../src/main/java/contactapp/domain/Task.java) | Task domain object mirroring Contact-style validation (id/name/description). |
| [`../src/main/java/contactapp/domain/Appointment.java`](../src/main/java/contactapp/domain/Appointment.java) | Appointment entity (id/date/description) with date-not-past validation. |
| [`../src/main/java/contactapp/domain/Project.java`](../src/main/java/contactapp/domain/Project.java) | Project entity (id/name/description/status) with optional empty description (ADR-0045). |
| [`../src/main/java/contactapp/domain/ProjectStatus.java`](../src/main/java/contactapp/domain/ProjectStatus.java) | ProjectStatus enum (ACTIVE, ON_HOLD, COMPLETED, ARCHIVED) with display names. |
| [`../src/main/java/contactapp/domain/TaskStatus.java`](../src/main/java/contactapp/domain/TaskStatus.java) | TaskStatus enum (TODO, IN_PROGRESS, DONE) for task workflow tracking. |
| [`../src/main/java/contactapp/domain/ProjectContactConstraints.java`](../src/main/java/contactapp/domain/ProjectContactConstraints.java) | Constants defining project-contact relationship constraints (MAX_CONTACTS_PER_PROJECT, MAX_PROJECTS_PER_CONTACT). |
| [`../src/main/java/contactapp/domain/Validation.java`](../src/main/java/contactapp/domain/Validation.java) | Shared helper with not-blank, length, 10-digit, and date-not-past checks. |
| [`../src/test/java/contactapp/domain/ContactTest.java`](../src/test/java/contactapp/domain/ContactTest.java) | JUnit tests for Contact covering boundary validation and copy behavior. |
| [`../src/test/java/contactapp/domain/TaskTest.java`](../src/test/java/contactapp/domain/TaskTest.java) | JUnit tests for Task covering validation and atomic updates. |
| [`../src/test/java/contactapp/domain/AppointmentTest.java`](../src/test/java/contactapp/domain/AppointmentTest.java) | JUnit tests for Appointment entity validation and date rules. |
| [`../src/test/java/contactapp/domain/ProjectTest.java`](../src/test/java/contactapp/domain/ProjectTest.java) | JUnit tests for Project covering optional description and status validation. |
| [`../src/test/java/contactapp/domain/ProjectStatusTest.java`](../src/test/java/contactapp/domain/ProjectStatusTest.java) | JUnit tests for ProjectStatus enum values and display names. |
| [`../src/test/java/contactapp/domain/TaskStatusTest.java`](../src/test/java/contactapp/domain/TaskStatusTest.java) | JUnit tests for TaskStatus enum values (TODO, IN_PROGRESS, DONE). |
| [`../src/test/java/contactapp/domain/ValidationTest.java`](../src/test/java/contactapp/domain/ValidationTest.java) | Tests for the shared validation helper (length, digits, date constraints). |

### Service Layer (`contactapp.service`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/service/ContactService.java`](../src/main/java/contactapp/service/ContactService.java) | @Service bean backed by the `ContactStore` abstraction (JPA + legacy fallback). |
| [`../src/main/java/contactapp/service/TaskService.java`](../src/main/java/contactapp/service/TaskService.java) | Task service wired through `TaskStore`, retaining `getInstance()` compatibility. |
| [`../src/main/java/contactapp/service/AppointmentService.java`](../src/main/java/contactapp/service/AppointmentService.java) | Appointment service using `AppointmentStore` with transactional CRUD methods. |
| [`../src/main/java/contactapp/service/ProjectService.java`](../src/main/java/contactapp/service/ProjectService.java) | Project service with status filtering, contact linking, and per-user isolation (ADR-0045). |
| [`../src/test/java/contactapp/service/ContactServiceTest.java`](../src/test/java/contactapp/service/ContactServiceTest.java) | Spring Boot test on the `integration` profile using Postgres via Testcontainers (Flyway applied), proving legacy `getInstance()` shares state with DI callers; annotated with `@Isolated` so Surefire doesn’t parallelize its singleton mutations. Tagged `legacy-singleton` for optional profile-based runs. |
| [`../src/test/java/contactapp/service/TaskServiceTest.java`](../src/test/java/contactapp/service/TaskServiceTest.java) | Spring Boot test for TaskService on the `integration` profile (Postgres + Flyway via Testcontainers) including singleton-vs-DI behavior coverage; also marked `@Isolated` to avoid static collisions. Tagged `legacy-singleton` for optional profile-based runs. |
| [`../src/test/java/contactapp/service/AppointmentServiceTest.java`](../src/test/java/contactapp/service/AppointmentServiceTest.java) | Spring Boot test for AppointmentService on the `integration` profile (Postgres + Flyway via Testcontainers) validating shared state with legacy singleton access, running in isolation for the same reason. Tagged `legacy-singleton` for optional profile-based runs. |
| [`../src/test/java/contactapp/service/ContactServiceLegacyTest.java`](../src/test/java/contactapp/service/ContactServiceLegacyTest.java) | Legacy singleton tests ensuring `getInstance()` still works outside Spring and copies data into the first Spring-managed bean; `@Isolated` keeps the fallback store from competing with other suites. Tagged `legacy-singleton`. |
| [`../src/test/java/contactapp/service/TaskServiceLegacyTest.java`](../src/test/java/contactapp/service/TaskServiceLegacyTest.java) | Legacy TaskService singleton tests covering data migration into Spring-managed stores, isolated to avoid duplicate ID collisions. Tagged `legacy-singleton`. |
| [`../src/test/java/contactapp/service/AppointmentServiceLegacyTest.java`](../src/test/java/contactapp/service/AppointmentServiceLegacyTest.java) | Legacy AppointmentService singleton tests covering fallback migration, marked `@Isolated` for the same reason. Tagged `legacy-singleton`. |
| [`../src/test/java/contactapp/service/ProjectServiceLegacyTest.java`](../src/test/java/contactapp/service/ProjectServiceLegacyTest.java) | Legacy ProjectService singleton tests covering migration into the DI-managed store and duplicate handling. Tagged `legacy-singleton`. |
| [`../src/test/java/contactapp/service/ProjectServiceRepositoryGuardTest.java`](../src/test/java/contactapp/service/ProjectServiceRepositoryGuardTest.java) | Unit tests that verify `ensureRepositoriesAvailable()` throws when the Spring-managed repositories haven’t been injected yet. |
| [`../src/test/java/contactapp/service/ServiceSingletonBridgeTest.java`](../src/test/java/contactapp/service/ServiceSingletonBridgeTest.java) | Regression tests using Mockito to ensure {@code getInstance()} defers to the Spring ApplicationContext when it is available; annotated with `@Isolated` because it manipulates the same static singleton fields. |
| [`../src/test/java/contactapp/service/ContactServiceIT.java`](../src/test/java/contactapp/service/ContactServiceIT.java) | Testcontainers-backed integration test hitting real Postgres. |
| [`../src/test/java/contactapp/service/TaskServiceIT.java`](../src/test/java/contactapp/service/TaskServiceIT.java) | TaskService integration tests with Testcontainers. |
| [`../src/test/java/contactapp/service/AppointmentServiceIT.java`](../src/test/java/contactapp/service/AppointmentServiceIT.java) | AppointmentService integration tests with Testcontainers. |
| [`../src/test/java/contactapp/service/ContactServiceMutationTest.java`](../src/test/java/contactapp/service/ContactServiceMutationTest.java) | PIT mutation test kill coverage for ContactService (see ADR-0031). |
| [`../src/test/java/contactapp/service/TaskServiceMutationTest.java`](../src/test/java/contactapp/service/TaskServiceMutationTest.java) | PIT mutation test kill coverage for TaskService. |
| [`../src/test/java/contactapp/service/AppointmentServiceMutationTest.java`](../src/test/java/contactapp/service/AppointmentServiceMutationTest.java) | PIT mutation test kill coverage for AppointmentService. |
| [`../src/test/java/contactapp/service/ProjectServiceTest.java`](../src/test/java/contactapp/service/ProjectServiceTest.java) | Spring Boot test for ProjectService (28 tests: CRUD, status filtering, contact linking) on Postgres via Testcontainers. |
| [`../src/test/java/contactapp/service/ContactServiceValidationTest.java`](../src/test/java/contactapp/service/ContactServiceValidationTest.java) | Validation tests for ContactService boundary conditions and error messages. |
| [`../src/test/java/contactapp/service/ProjectServiceValidationTest.java`](../src/test/java/contactapp/service/ProjectServiceValidationTest.java) | Validation tests for ProjectService boundary conditions and status constraints. |
| [`../src/test/java/contactapp/service/TaskServiceValidationTest.java`](../src/test/java/contactapp/service/TaskServiceValidationTest.java) | Validation tests for TaskService boundary conditions and assignee constraints. |
| [`../src/test/java/contactapp/service/TaskServiceFallbackTest.java`](../src/test/java/contactapp/service/TaskServiceFallbackTest.java) | Tests for TaskService fallback behavior when repositories are unavailable. |
| [`../src/test/java/contactapp/service/TestCleanupUtility.java`](../src/test/java/contactapp/service/TestCleanupUtility.java) | Test utility for cleaning up test data between test runs. |

### Persistence Layer (`contactapp.persistence`)

#### Entity Classes (`persistence.entity`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/persistence/entity/ContactEntity.java`](../src/main/java/contactapp/persistence/entity/ContactEntity.java) | JPA entity for Contact with `@Entity`, `@Table`, and `@Column` annotations. |
| [`../src/main/java/contactapp/persistence/entity/TaskEntity.java`](../src/main/java/contactapp/persistence/entity/TaskEntity.java) | JPA entity for Task with id/name/description columns. |
| [`../src/main/java/contactapp/persistence/entity/AppointmentEntity.java`](../src/main/java/contactapp/persistence/entity/AppointmentEntity.java) | JPA entity for Appointment with id/date/description columns. |
| [`../src/main/java/contactapp/persistence/entity/ProjectEntity.java`](../src/main/java/contactapp/persistence/entity/ProjectEntity.java) | JPA entity for Project with id/name/description/status columns and @Version for optimistic locking. |
| [`../src/main/java/contactapp/persistence/entity/ProjectContactId.java`](../src/main/java/contactapp/persistence/entity/ProjectContactId.java) | Composite key for project-contact links (Serializable, equals/hashCode, JPA ctor). |
| [`../src/main/java/contactapp/persistence/entity/ProjectContactEntity.java`](../src/main/java/contactapp/persistence/entity/ProjectContactEntity.java) | JPA entity for project-contact junction table with composite key and cascade. |
| [`../src/test/java/contactapp/persistence/entity/ContactEntityTest.java`](../src/test/java/contactapp/persistence/entity/ContactEntityTest.java) | Entity tests ensuring protected constructors/setters support Hibernate proxies and PIT coverage. |
| [`../src/test/java/contactapp/persistence/entity/TaskEntityTest.java`](../src/test/java/contactapp/persistence/entity/TaskEntityTest.java) | TaskEntity tests for Hibernate proxy support and setter coverage. |
| [`../src/test/java/contactapp/persistence/entity/AppointmentEntityTest.java`](../src/test/java/contactapp/persistence/entity/AppointmentEntityTest.java) | AppointmentEntity tests for Hibernate proxy support and setter coverage. |
| [`../src/test/java/contactapp/persistence/entity/ProjectContactIdTest.java`](../src/test/java/contactapp/persistence/entity/ProjectContactIdTest.java) | Tests the composite key equals/hashCode/setters to keep project-contact links addressable. |
| [`../src/test/java/contactapp/persistence/entity/ProjectEntityTest.java`](../src/test/java/contactapp/persistence/entity/ProjectEntityTest.java) | ProjectEntity tests for Hibernate proxy support, setters, and status enum coverage. |
| [`../src/test/java/contactapp/persistence/entity/ProjectContactEntityTest.java`](../src/test/java/contactapp/persistence/entity/ProjectContactEntityTest.java) | Tests for project-contact junction entity relationships and cascade behavior. |

#### Mapper Components (`persistence.mapper`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/persistence/mapper/ContactMapper.java`](../src/main/java/contactapp/persistence/mapper/ContactMapper.java) | `@Component` that converts between `Contact` domain and `ContactEntity`. |
| [`../src/main/java/contactapp/persistence/mapper/TaskMapper.java`](../src/main/java/contactapp/persistence/mapper/TaskMapper.java) | `@Component` that converts between `Task` domain and `TaskEntity`. |
| [`../src/main/java/contactapp/persistence/mapper/AppointmentMapper.java`](../src/main/java/contactapp/persistence/mapper/AppointmentMapper.java) | `@Component` that converts between `Appointment` domain and `AppointmentEntity`. |
| [`../src/main/java/contactapp/persistence/mapper/ProjectMapper.java`](../src/main/java/contactapp/persistence/mapper/ProjectMapper.java) | `@Component` that converts between `Project` domain and `ProjectEntity`. |
| [`../src/test/java/contactapp/persistence/mapper/ContactMapperTest.java`](../src/test/java/contactapp/persistence/mapper/ContactMapperTest.java) | Mapper unit tests ensuring conversions re-use domain validation. |
| [`../src/test/java/contactapp/persistence/mapper/TaskMapperTest.java`](../src/test/java/contactapp/persistence/mapper/TaskMapperTest.java) | TaskMapper unit tests for domain-entity conversions. |
| [`../src/test/java/contactapp/persistence/mapper/AppointmentMapperTest.java`](../src/test/java/contactapp/persistence/mapper/AppointmentMapperTest.java) | AppointmentMapper unit tests for domain-entity conversions. |
| [`../src/test/java/contactapp/persistence/mapper/ProjectMapperTest.java`](../src/test/java/contactapp/persistence/mapper/ProjectMapperTest.java) | ProjectMapper unit tests for domain-entity conversions with status enum handling. |

#### Repositories (`persistence.repository`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/persistence/repository/ContactRepository.java`](../src/main/java/contactapp/persistence/repository/ContactRepository.java) | Spring Data `JpaRepository<ContactEntity, Long>` with `(contact_id,user_id)` uniqueness and per-user query helpers. |
| [`../src/main/java/contactapp/persistence/repository/TaskRepository.java`](../src/main/java/contactapp/persistence/repository/TaskRepository.java) | Spring Data `JpaRepository<TaskEntity, Long>` with per-user query helpers and deprecated unscoped accessors. |
| [`../src/main/java/contactapp/persistence/repository/AppointmentRepository.java`](../src/main/java/contactapp/persistence/repository/AppointmentRepository.java) | Spring Data `JpaRepository<AppointmentEntity, Long>` with `(appointment_id,user_id)` uniqueness helpers. |
| [`../src/main/java/contactapp/persistence/repository/ProjectRepository.java`](../src/main/java/contactapp/persistence/repository/ProjectRepository.java) | Spring Data `JpaRepository<ProjectEntity, Long>` with per-user and status filtering query helpers. |
| [`../src/main/java/contactapp/persistence/repository/ProjectContactRepository.java`](../src/main/java/contactapp/persistence/repository/ProjectContactRepository.java) | Spring Data repository for project-contact junction with find/delete by project or contact queries. |
| [`../src/test/java/contactapp/persistence/repository/ContactRepositoryTest.java`](../src/test/java/contactapp/persistence/repository/ContactRepositoryTest.java) | `@DataJpaTest` slice for ContactRepository (H2 + Flyway). |
| [`../src/test/java/contactapp/persistence/repository/TaskRepositoryTest.java`](../src/test/java/contactapp/persistence/repository/TaskRepositoryTest.java) | `@DataJpaTest` slice for TaskRepository (H2 + Flyway). |
| [`../src/test/java/contactapp/persistence/repository/AppointmentRepositoryTest.java`](../src/test/java/contactapp/persistence/repository/AppointmentRepositoryTest.java) | `@DataJpaTest` slice for AppointmentRepository (H2 + Flyway). |
| [`../src/test/java/contactapp/persistence/repository/ProjectRepositoryTest.java`](../src/test/java/contactapp/persistence/repository/ProjectRepositoryTest.java) | `@DataJpaTest` slice for ProjectRepository with status filtering tests. |
| [`../src/test/java/contactapp/persistence/repository/ProjectContactRepositoryTest.java`](../src/test/java/contactapp/persistence/repository/ProjectContactRepositoryTest.java) | `@DataJpaTest` slice for ProjectContactRepository junction table operations. |

#### Store Abstraction (`persistence.store`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/persistence/store/DomainDataStore.java`](../src/main/java/contactapp/persistence/store/DomainDataStore.java) | Generic interface for domain object CRUD operations (see ADR-0024). |
| [`../src/main/java/contactapp/persistence/store/ContactStore.java`](../src/main/java/contactapp/persistence/store/ContactStore.java) | `DomainDataStore<Contact>` specialization for Contact persistence. |
| [`../src/main/java/contactapp/persistence/store/TaskStore.java`](../src/main/java/contactapp/persistence/store/TaskStore.java) | `DomainDataStore<Task>` specialization for Task persistence. |
| [`../src/main/java/contactapp/persistence/store/AppointmentStore.java`](../src/main/java/contactapp/persistence/store/AppointmentStore.java) | `DomainDataStore<Appointment>` specialization for Appointment persistence. |
| [`../src/main/java/contactapp/persistence/store/ProjectStore.java`](../src/main/java/contactapp/persistence/store/ProjectStore.java) | `DomainDataStore<Project>` specialization for Project persistence with status filtering. |
| [`../src/main/java/contactapp/persistence/store/JpaContactStore.java`](../src/main/java/contactapp/persistence/store/JpaContactStore.java) | JPA-backed `ContactStore` implementation using repository + mapper. |
| [`../src/main/java/contactapp/persistence/store/JpaTaskStore.java`](../src/main/java/contactapp/persistence/store/JpaTaskStore.java) | JPA-backed `TaskStore` implementation using repository + mapper. |
| [`../src/main/java/contactapp/persistence/store/JpaAppointmentStore.java`](../src/main/java/contactapp/persistence/store/JpaAppointmentStore.java) | JPA-backed `AppointmentStore` implementation using repository + mapper. |
| [`../src/main/java/contactapp/persistence/store/InMemoryContactStore.java`](../src/main/java/contactapp/persistence/store/InMemoryContactStore.java) | In-memory `ContactStore` fallback for legacy/test scenarios. |
| [`../src/main/java/contactapp/persistence/store/InMemoryTaskStore.java`](../src/main/java/contactapp/persistence/store/InMemoryTaskStore.java) | In-memory `TaskStore` fallback for legacy/test scenarios. |
| [`../src/main/java/contactapp/persistence/store/InMemoryAppointmentStore.java`](../src/main/java/contactapp/persistence/store/InMemoryAppointmentStore.java) | In-memory `AppointmentStore` fallback for legacy/test scenarios. |
| [`../src/main/java/contactapp/persistence/store/JpaProjectStore.java`](../src/main/java/contactapp/persistence/store/JpaProjectStore.java) | JPA-backed `ProjectStore` implementation with status filtering and contact linking. |
| [`../src/main/java/contactapp/persistence/store/InMemoryProjectStore.java`](../src/main/java/contactapp/persistence/store/InMemoryProjectStore.java) | In-memory `ProjectStore` fallback for legacy/test scenarios. |
| [`../src/test/java/contactapp/persistence/store/JpaContactStoreTest.java`](../src/test/java/contactapp/persistence/store/JpaContactStoreTest.java) | JpaContactStore unit tests verifying repository delegations and UnsupportedOperation guards. |
| [`../src/test/java/contactapp/persistence/store/JpaTaskStoreTest.java`](../src/test/java/contactapp/persistence/store/JpaTaskStoreTest.java) | JpaTaskStore unit tests verifying repository delegations and UnsupportedOperation guards. |
| [`../src/test/java/contactapp/persistence/store/JpaAppointmentStoreTest.java`](../src/test/java/contactapp/persistence/store/JpaAppointmentStoreTest.java) | JpaAppointmentStore unit tests verifying repository delegations and UnsupportedOperation guards. |
| [`../src/test/java/contactapp/persistence/store/InMemoryContactStoreTest.java`](../src/test/java/contactapp/persistence/store/InMemoryContactStoreTest.java) | Regression tests proving the in-memory fallback stores keep defensive copies and delete semantics. |
| [`../src/test/java/contactapp/persistence/store/InMemoryTaskStoreTest.java`](../src/test/java/contactapp/persistence/store/InMemoryTaskStoreTest.java) | InMemoryTaskStore tests for defensive copies and delete semantics. |
| [`../src/test/java/contactapp/persistence/store/InMemoryAppointmentStoreTest.java`](../src/test/java/contactapp/persistence/store/InMemoryAppointmentStoreTest.java) | InMemoryAppointmentStore tests for defensive copies and delete semantics. |
| [`../src/test/java/contactapp/persistence/store/JpaProjectStoreTest.java`](../src/test/java/contactapp/persistence/store/JpaProjectStoreTest.java) | JpaProjectStore unit tests (24 tests: CRUD, status filtering, per-user isolation). |
| [`../src/test/java/contactapp/persistence/store/InMemoryProjectStoreTest.java`](../src/test/java/contactapp/persistence/store/InMemoryProjectStoreTest.java) | InMemoryProjectStore tests for non-Spring fallback scenarios. |

### API Layer (`contactapp.api`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/api/ContactController.java`](../src/main/java/contactapp/api/ContactController.java) | REST controller for Contact CRUD at `/api/v1/contacts`. |
| [`../src/main/java/contactapp/api/TaskController.java`](../src/main/java/contactapp/api/TaskController.java) | REST controller for Task CRUD at `/api/v1/tasks`. |
| [`../src/main/java/contactapp/api/AppointmentController.java`](../src/main/java/contactapp/api/AppointmentController.java) | REST controller for Appointment CRUD at `/api/v1/appointments`. |
| [`../src/main/java/contactapp/api/ProjectController.java`](../src/main/java/contactapp/api/ProjectController.java) | REST controller for Project CRUD at `/api/v1/projects` with status filtering (ADR-0045). |
| [`../src/main/java/contactapp/api/AuthController.java`](../src/main/java/contactapp/api/AuthController.java) | REST controller for authentication (login/register) at `/api/auth`. |
| [`../src/main/java/contactapp/api/GlobalExceptionHandler.java`](../src/main/java/contactapp/api/GlobalExceptionHandler.java) | @RestControllerAdvice mapping exceptions to HTTP responses (400, 401, 403, 404, 409 including optimistic locking conflicts). |
| [`../src/main/java/contactapp/api/CustomErrorController.java`](../src/main/java/contactapp/api/CustomErrorController.java) | ErrorController ensuring ALL errors return JSON (including Tomcat-level). |
| [`../src/main/java/contactapp/api/dto/ContactRequest.java`](../src/main/java/contactapp/api/dto/ContactRequest.java) | Contact request DTO with Bean Validation. |
| [`../src/main/java/contactapp/api/dto/ContactResponse.java`](../src/main/java/contactapp/api/dto/ContactResponse.java) | Contact response DTO. |
| [`../src/main/java/contactapp/api/dto/TaskRequest.java`](../src/main/java/contactapp/api/dto/TaskRequest.java) | Task request DTO with Bean Validation. |
| [`../src/main/java/contactapp/api/dto/TaskResponse.java`](../src/main/java/contactapp/api/dto/TaskResponse.java) | Task response DTO. |
| [`../src/main/java/contactapp/api/dto/AppointmentRequest.java`](../src/main/java/contactapp/api/dto/AppointmentRequest.java) | Appointment request DTO with Bean Validation and @FutureOrPresent. |
| [`../src/main/java/contactapp/api/dto/AppointmentResponse.java`](../src/main/java/contactapp/api/dto/AppointmentResponse.java) | Appointment response DTO. |
| [`../src/main/java/contactapp/api/dto/ProjectRequest.java`](../src/main/java/contactapp/api/dto/ProjectRequest.java) | Project request DTO with Bean Validation (name required, description optional). |
| [`../src/main/java/contactapp/api/dto/ProjectResponse.java`](../src/main/java/contactapp/api/dto/ProjectResponse.java) | Project response DTO with status enum. |
| [`../src/main/java/contactapp/api/dto/ProjectContactRequest.java`](../src/main/java/contactapp/api/dto/ProjectContactRequest.java) | Request DTO for linking contacts to projects with validation. |
| [`../src/main/java/contactapp/api/dto/ErrorResponse.java`](../src/main/java/contactapp/api/dto/ErrorResponse.java) | Standard error response DTO. |
| [`../src/main/java/contactapp/api/dto/LoginRequest.java`](../src/main/java/contactapp/api/dto/LoginRequest.java) | Login request DTO (username, password) with Bean Validation. |
| [`../src/main/java/contactapp/api/dto/RegisterRequest.java`](../src/main/java/contactapp/api/dto/RegisterRequest.java) | Registration request DTO (username, email, password) with Bean Validation. |
| [`../src/main/java/contactapp/api/dto/AuthResponse.java`](../src/main/java/contactapp/api/dto/AuthResponse.java) | Authentication response DTO (token, username, email, role, expiresIn). |
| [`../src/main/java/contactapp/api/exception/ResourceNotFoundException.java`](../src/main/java/contactapp/api/exception/ResourceNotFoundException.java) | Exception for 404 Not Found responses. |
| [`../src/main/java/contactapp/api/exception/DuplicateResourceException.java`](../src/main/java/contactapp/api/exception/DuplicateResourceException.java) | Exception for 409 Conflict responses. |
| [`../src/test/java/contactapp/ContactControllerTest.java`](../src/test/java/contactapp/ContactControllerTest.java) | MockMvc integration tests for Contact API (CRUD/auth/validation). |
| [`../src/test/java/contactapp/ContactControllerUnitTest.java`](../src/test/java/contactapp/ContactControllerUnitTest.java) | Unit tests verifying the `?all=true` ADMIN guard before delegating to the service. |
| [`../src/test/java/contactapp/TaskControllerTest.java`](../src/test/java/contactapp/TaskControllerTest.java) | MockMvc integration tests for Task API (CRUD/auth/validation). |
| [`../src/test/java/contactapp/AppointmentControllerTest.java`](../src/test/java/contactapp/AppointmentControllerTest.java) | MockMvc integration tests for Appointment API (CRUD/auth/validation). |
| [`../src/test/java/contactapp/ProjectControllerUnitTest.java`](../src/test/java/contactapp/ProjectControllerUnitTest.java) | Unit tests proving the controller-level ADMIN guard blocks non-admin users before calling the service. |
| [`../src/test/java/contactapp/AuthControllerTest.java`](../src/test/java/contactapp/AuthControllerTest.java) | MockMvc integration tests for Auth API (login, register, validation). |
| [`../src/test/java/contactapp/AuthControllerUnitTest.java`](../src/test/java/contactapp/AuthControllerUnitTest.java) | Unit tests for AuthController logout behavior and token revocation. |
| [`../src/test/java/contactapp/GlobalExceptionHandlerTest.java`](../src/test/java/contactapp/GlobalExceptionHandlerTest.java) | Unit tests for GlobalExceptionHandler methods. |
| [`../src/test/java/contactapp/CustomErrorControllerTest.java`](../src/test/java/contactapp/CustomErrorControllerTest.java) | Unit tests for CustomErrorController JSON responses. |
| [`../src/test/java/contactapp/api/dto/AppointmentRequestTest.java`](../src/test/java/contactapp/api/dto/AppointmentRequestTest.java) | Bean validation tests for AppointmentRequest DTO (@FutureOrPresent, required fields). |

### Test Support Layer (`contactapp.support`)
| Path | Description |
|------|-------------|
| [`../src/test/java/contactapp/support/TestUserFactory.java`](../src/test/java/contactapp/support/TestUserFactory.java) | Factory utility for creating User instances in tests with deterministic usernames/emails and valid BCrypt hashes. |
| [`../src/test/java/contactapp/support/PostgresContainerSupport.java`](../src/test/java/contactapp/support/PostgresContainerSupport.java) | Base class for Testcontainers-based integration tests with shared Postgres container lifecycle (ADR-0048). |
| [`../src/test/java/contactapp/support/TestDates.java`](../src/test/java/contactapp/support/TestDates.java) | Test fixture utility providing deterministic dates for appointment and task testing. |
| [`../src/test/java/contactapp/support/SecuredMockMvcTest.java`](../src/test/java/contactapp/support/SecuredMockMvcTest.java) | Base class for secured MockMvc integration tests with authentication setup (Spring Boot 4.0/Spring Security 7). |

### Config Layer (`contactapp.config`)
| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/config/JacksonConfig.java`](../src/main/java/contactapp/config/JacksonConfig.java) | Disables Jackson type coercion for strict schema compliance (ADR-0023). |
| [`../src/main/java/contactapp/config/JsonErrorReportValve.java`](../src/main/java/contactapp/config/JsonErrorReportValve.java) | Tomcat valve for JSON error responses at container level (ADR-0022). |
| [`../src/main/java/contactapp/config/TomcatConfig.java`](../src/main/java/contactapp/config/TomcatConfig.java) | Registers JsonErrorReportValve with embedded Tomcat. |
| [`../src/main/java/contactapp/config/ApplicationContextProvider.java`](../src/main/java/contactapp/config/ApplicationContextProvider.java) | Captures Spring context for legacy `getInstance()` singleton access. |
| [`../src/main/java/contactapp/config/RateLimitConfig.java`](../src/main/java/contactapp/config/RateLimitConfig.java) | Configuration properties for API rate limiting (login/register/api limits). |
| [`../src/main/java/contactapp/config/RateLimitingFilter.java`](../src/main/java/contactapp/config/RateLimitingFilter.java) | Servlet filter enforcing rate limits via Bucket4j token buckets. |
| [`../src/main/java/contactapp/config/CorrelationIdFilter.java`](../src/main/java/contactapp/config/CorrelationIdFilter.java) | Servlet filter generating/propagating X-Correlation-ID for request tracing. |
| [`../src/main/java/contactapp/config/RequestLoggingFilter.java`](../src/main/java/contactapp/config/RequestLoggingFilter.java) | Servlet filter logging request/response details with MDC correlation. |
| [`../src/main/java/contactapp/config/RequestUtils.java`](../src/main/java/contactapp/config/RequestUtils.java) | Shared helpers for safely extracting/masking client IPs and headers. |
| [`../src/main/java/contactapp/config/PiiMaskingConverter.java`](../src/main/java/contactapp/config/PiiMaskingConverter.java) | Logback converter that masks PII patterns in log output. |
| [`../src/test/java/contactapp/config/JacksonConfigTest.java`](../src/test/java/contactapp/config/JacksonConfigTest.java) | Verifies the ObjectMapper bean rejects boolean/numeric coercion per ADR-0023. |
| [`../src/test/java/contactapp/config/TomcatConfigTest.java`](../src/test/java/contactapp/config/TomcatConfigTest.java) | Ensures Tomcat customizer installs the JSON error valve and guards non-host parents. |
| [`../src/test/java/contactapp/config/JsonErrorReportValveTest.java`](../src/test/java/contactapp/config/JsonErrorReportValveTest.java) | Unit tests for JsonErrorReportValve error wrapping. |
| [`../src/test/java/contactapp/config/RateLimitingFilterTest.java`](../src/test/java/contactapp/config/RateLimitingFilterTest.java) | Unit tests for RateLimitingFilter (rate limit enforcement, retry-after headers, log sanitization, bucket reset paths). |
| [`../src/test/java/contactapp/config/RequestLoggingFilterTest.java`](../src/test/java/contactapp/config/RequestLoggingFilterTest.java) | Tests covering masked IP/query logging, sanitization helpers, duration math, and enable/disable toggles. |
| [`../src/test/java/contactapp/config/CorrelationIdFilterTest.java`](../src/test/java/contactapp/config/CorrelationIdFilterTest.java) | Ensures correlation IDs propagate across requests and MDC cleanup. |
| [`../src/test/java/contactapp/config/RequestUtilsTest.java`](../src/test/java/contactapp/config/RequestUtilsTest.java) | Unit tests for safe client IP extraction/masking helpers. |
| [`../src/test/java/contactapp/config/PiiMaskingConverterTest.java`](../src/test/java/contactapp/config/PiiMaskingConverterTest.java) | Tests verifying log converter redacts phone/email patterns. |

### Security Layer (`contactapp.security`)

> **ADR-0052 Complete (2025-12-06):** All phases implemented - UUID migration, JWT hardening, HTTPS setup, refresh tokens, token fingerprinting. 1107 tests pass.

| Path | Description |
|------|-------------|
| [`../src/main/java/contactapp/security/User.java`](../src/main/java/contactapp/security/User.java) | User entity implementing `UserDetails` for Spring Security (UUID id, @JsonIgnore on password; see ADR-0037, ADR-0052). |
| [`../src/main/java/contactapp/security/Role.java`](../src/main/java/contactapp/security/Role.java) | Role enum (`USER`, `ADMIN`) for authorization. |
| [`../src/main/java/contactapp/security/UserRepository.java`](../src/main/java/contactapp/security/UserRepository.java) | Spring Data repository for User persistence with UUID-based lookup methods. |
| [`../src/main/java/contactapp/security/RefreshToken.java`](../src/main/java/contactapp/security/RefreshToken.java) | Refresh token entity with UUID PK, opaque token, expiry, revoked flag (ADR-0052 Phase B). |
| [`../src/main/java/contactapp/security/RefreshTokenRepository.java`](../src/main/java/contactapp/security/RefreshTokenRepository.java) | Spring Data repository for refresh tokens with find/revoke/delete queries. |
| [`../src/main/java/contactapp/security/RefreshTokenService.java`](../src/main/java/contactapp/security/RefreshTokenService.java) | Refresh token lifecycle: single-session model, token rotation, scheduled cleanup (ADR-0052 Phase B). |
| [`../src/main/java/contactapp/security/TokenFingerprintService.java`](../src/main/java/contactapp/security/TokenFingerprintService.java) | Token fingerprinting: 50-byte generation, SHA-256 hashing, constant-time verification (ADR-0052 Phase C). |
| [`../src/main/java/contactapp/security/JwtService.java`](../src/main/java/contactapp/security/JwtService.java) | JWT token generation and validation service (HMAC-SHA256, issuer/audience claims, 60s clock skew tolerance, fingerprint binding per ADR-0052). |
| [`../src/main/java/contactapp/security/JwtAuthenticationFilter.java`](../src/main/java/contactapp/security/JwtAuthenticationFilter.java) | Filter that validates JWT tokens from HttpOnly cookie first, then Authorization header fallback. Verifies fingerprints when present (ADR-0052 Phase C). |
| [`../src/main/java/contactapp/security/SpaCsrfTokenRequestHandler.java`](../src/main/java/contactapp/security/SpaCsrfTokenRequestHandler.java) | CSRF token handler for SPA double-submit cookie pattern with BREACH protection via XOR masking. |
| [`../src/main/java/contactapp/security/CsrfCookieFilter.java`](../src/main/java/contactapp/security/CsrfCookieFilter.java) | Filter ensuring CSRF token cookie is set on every response for SPA clients. |
| [`../src/main/java/contactapp/security/SecurityConfig.java`](../src/main/java/contactapp/security/SecurityConfig.java) | Spring Security configuration (JWT auth, CookieCsrfTokenRepository for SPA routes with `SameSite=Lax`/secure inheritance, CORS, security headers, AuthenticationEntryPoint for 401, AccessDeniedHandler for 403). |
| [`../src/main/java/contactapp/security/CustomUserDetailsService.java`](../src/main/java/contactapp/security/CustomUserDetailsService.java) | `UserDetailsService` implementation loading users from repository. |
| [`../src/main/java/contactapp/config/SchedulingConfig.java`](../src/main/java/contactapp/config/SchedulingConfig.java) | `@EnableScheduling` configuration for refresh token cleanup job. |
| [`../src/test/java/contactapp/security/UserTest.java`](../src/test/java/contactapp/security/UserTest.java) | Unit tests for User entity validation (boundary, null/blank, role tests). |
| [`../src/test/java/contactapp/security/JwtServiceTest.java`](../src/test/java/contactapp/security/JwtServiceTest.java) | Unit tests for JWT lifecycle (+9 mutation tests: expiration boundaries, refresh window, case sensitivity, fingerprinting). |
| [`../src/test/java/contactapp/security/JwtAuthenticationFilterTest.java`](../src/test/java/contactapp/security/JwtAuthenticationFilterTest.java) | Unit tests for JWT filter (missing cookie/header, invalid token, valid token, fingerprint verification). |
| [`../src/test/java/contactapp/security/RefreshTokenServiceTest.java`](../src/test/java/contactapp/security/RefreshTokenServiceTest.java) | Unit tests for refresh token lifecycle (17 tests: creation, rotation, revocation, cleanup). |
| [`../src/test/java/contactapp/security/CustomUserDetailsServiceTest.java`](../src/test/java/contactapp/security/CustomUserDetailsServiceTest.java) | Unit tests for user lookup and exception handling. |
| [`../src/test/java/contactapp/security/SpaCsrfTokenRequestHandlerTest.java`](../src/test/java/contactapp/security/SpaCsrfTokenRequestHandlerTest.java) | Unit tests for SPA CSRF handler (cookie persistence, SameSite attribute, parameter resolution). |
| [`../src/test/java/contactapp/security/TokenFingerprintServiceTest.java`](../src/test/java/contactapp/security/TokenFingerprintServiceTest.java) | Unit tests for token fingerprinting (generation, hashing, constant-time verification). |
| [`../src/test/java/contactapp/security/WithMockAppUser.java`](../src/test/java/contactapp/security/WithMockAppUser.java) | Custom annotation for populating SecurityContext with real User instances in tests. Uses UUID string for `id` parameter. |
| [`../src/test/java/contactapp/security/WithMockAppUserSecurityContextFactory.java`](../src/test/java/contactapp/security/WithMockAppUserSecurityContextFactory.java) | SecurityContextFactory creating concrete User entities for @WithMockAppUser annotation. |
| [`../src/test/java/contactapp/security/TestUserSetup.java`](../src/test/java/contactapp/security/TestUserSetup.java) | Test utility component for database persistence and SecurityContext setup of authenticated users; clears stale auth and recreates the seed user if missing to keep FK-backed tests stable. |

### React UI Layer (`ui/contact-app`)

#### Application Entry & Layout
| Path | Description |
|------|-------------|
| [`../ui/contact-app/src/main.tsx`](../ui/contact-app/src/main.tsx) | React application entry point, renders App component into DOM. |
| [`../ui/contact-app/src/App.tsx`](../ui/contact-app/src/App.tsx) | Root component with React Router and TanStack Query setup. |
| [`../ui/contact-app/src/index.css`](../ui/contact-app/src/index.css) | Tailwind CSS v4 imports + theme CSS variables (5 themes). |
| [`../ui/contact-app/src/components/layout/AppShell.tsx`](../ui/contact-app/src/components/layout/AppShell.tsx) | Main layout with sidebar, topbar, and content outlet. |
| [`../ui/contact-app/src/components/layout/Sidebar.tsx`](../ui/contact-app/src/components/layout/Sidebar.tsx) | Navigation sidebar with collapsible behavior. |
| [`../ui/contact-app/src/components/layout/TopBar.tsx`](../ui/contact-app/src/components/layout/TopBar.tsx) | Top bar with title, theme switcher, dark mode toggle. |
| [`../ui/contact-app/src/components/ui/`](../ui/contact-app/src/components/ui/) | shadcn/ui components (Button, Card, Table, Sheet, Dialog, etc.). |
| [`../ui/contact-app/src/components/ui/empty-state.tsx`](../ui/contact-app/src/components/ui/empty-state.tsx) | Empty state component for tables with no data. |
| [`../ui/contact-app/src/components/ui/pagination.tsx`](../ui/contact-app/src/components/ui/pagination.tsx) | Pagination controls for table navigation. |
| [`../ui/contact-app/src/components/ui/search-input.tsx`](../ui/contact-app/src/components/ui/search-input.tsx) | Search input with debounced filtering. |
| [`../ui/contact-app/src/components/ui/sortable-table-head.tsx`](../ui/contact-app/src/components/ui/sortable-table-head.tsx) | Sortable table header with ascending/descending indicators. |
| [`../ui/contact-app/src/components/layout/SkipLink.tsx`](../ui/contact-app/src/components/layout/SkipLink.tsx) | Accessibility skip link for keyboard navigation. |

#### Pages
| Path | Description |
|------|-------------|
| [`../ui/contact-app/src/pages/OverviewPage.tsx`](../ui/contact-app/src/pages/OverviewPage.tsx) | Dashboard with summary cards for all entities. |
| [`../ui/contact-app/src/pages/ContactsPage.tsx`](../ui/contact-app/src/pages/ContactsPage.tsx) | Contacts table with detail sheet and CRUD operations. |
| [`../ui/contact-app/src/pages/TasksPage.tsx`](../ui/contact-app/src/pages/TasksPage.tsx) | Tasks table with detail sheet and CRUD operations. |
| [`../ui/contact-app/src/pages/AppointmentsPage.tsx`](../ui/contact-app/src/pages/AppointmentsPage.tsx) | Appointments table with detail sheet and CRUD operations. |
| [`../ui/contact-app/src/pages/SettingsPage.tsx`](../ui/contact-app/src/pages/SettingsPage.tsx) | User settings page with profile management. |
| [`../ui/contact-app/src/pages/HelpPage.tsx`](../ui/contact-app/src/pages/HelpPage.tsx) | Help page with documentation and support links. |
| [`../ui/contact-app/src/pages/LoginPage.tsx`](../ui/contact-app/src/pages/LoginPage.tsx) | Login screen that exchanges credentials for JWTs before unlocking the SPA. |
| [`../ui/contact-app/src/pages/AdminDashboard.tsx`](../ui/contact-app/src/pages/AdminDashboard.tsx) | Admin-only dashboard with system metrics and user management. |

#### Forms & Dialogs
| Path | Description |
|------|-------------|
| [`../ui/contact-app/src/components/forms/ContactForm.tsx`](../ui/contact-app/src/components/forms/ContactForm.tsx) | Contact form component with Zod validation and react-hook-form. |
| [`../ui/contact-app/src/components/forms/TaskForm.tsx`](../ui/contact-app/src/components/forms/TaskForm.tsx) | Task form component with Zod validation and react-hook-form. |
| [`../ui/contact-app/src/components/forms/AppointmentForm.tsx`](../ui/contact-app/src/components/forms/AppointmentForm.tsx) | Appointment form component with Zod validation and date picker. |
| [`../ui/contact-app/src/components/dialogs/DeleteConfirmDialog.tsx`](../ui/contact-app/src/components/dialogs/DeleteConfirmDialog.tsx) | Reusable confirmation dialog for delete operations. |

#### Auth Guards
| Path | Description |
|------|-------------|
| [`../ui/contact-app/src/components/auth/RequireAuth.tsx`](../ui/contact-app/src/components/auth/RequireAuth.tsx) | `RequireAuth` and `PublicOnlyRoute` wrappers enforcing login/logout flows and redirect handling. |
| [`../ui/contact-app/src/components/auth/RequireAdmin.tsx`](../ui/contact-app/src/components/auth/RequireAdmin.tsx) | Admin-only route guard redirecting non-admin users. |

#### Hooks & Utilities
| Path | Description |
|------|-------------|
| [`../ui/contact-app/src/hooks/useTheme.ts`](../ui/contact-app/src/hooks/useTheme.ts) | Theme switching hook with localStorage persistence. |
| [`../ui/contact-app/src/hooks/useMediaQuery.ts`](../ui/contact-app/src/hooks/useMediaQuery.ts) | Responsive breakpoint detection hook. |
| [`../ui/contact-app/src/hooks/useProfile.ts`](../ui/contact-app/src/hooks/useProfile.ts) | User profile management hook with sessionStorage persistence (clears when the browser session ends; restore/bfcache handling included). |
| [`../ui/contact-app/src/hooks/useToast.ts`](../ui/contact-app/src/hooks/useToast.ts) | Toast notification hook for success/error feedback. |
| [`../ui/contact-app/src/lib/queryClient.ts`](../ui/contact-app/src/lib/queryClient.ts) | Central TanStack Query client configured for auth-aware cache invalidation. |
| [`../ui/contact-app/src/lib/api.ts`](../ui/contact-app/src/lib/api.ts) | Typed fetch wrapper handling HttpOnly cookie auth, credentialed CORS/CSRF headers, and sessionStorage-backed user/profile caching. |
| [`../ui/contact-app/src/lib/schemas.ts`](../ui/contact-app/src/lib/schemas.ts) | Zod schemas matching backend Validation.java constants. |
| [`../ui/contact-app/src/lib/utils.ts`](../ui/contact-app/src/lib/utils.ts) | `cn()` utility for class name merging. |
| [`../ui/contact-app/src/lib/dateUtils.ts`](../ui/contact-app/src/lib/dateUtils.ts) | Timezone-safe date formatting using date-fns `parseISO()` to prevent date shift bugs in negative UTC offsets (see ADR-0053). |
| [`../ui/contact-app/src/lib/dateUtils.test.ts`](../ui/contact-app/src/lib/dateUtils.test.ts) | Timezone regression tests ensuring dates don't shift backwards by one day. |

#### Configuration
| Path | Description |
|------|-------------|
| [`../ui/contact-app/vite.config.ts`](../ui/contact-app/vite.config.ts) | Vite config with Tailwind plugin and API proxy. |
| [`../ui/contact-app/components.json`](../ui/contact-app/components.json) | shadcn/ui configuration file. |
| [`../ui/contact-app/package.json`](../ui/contact-app/package.json) | npm dependencies (React 19, Tailwind v4, TanStack Query). |
| [`../ui/contact-app/tsconfig.app.json`](../ui/contact-app/tsconfig.app.json) | TypeScript config with @/* path alias. |
| [`../ui/contact-app/vitest.config.ts`](../ui/contact-app/vitest.config.ts) | Vitest configuration for component tests. |
| [`../ui/contact-app/playwright.config.ts`](../ui/contact-app/playwright.config.ts) | Playwright configuration for E2E tests. |

#### Tests
| Path | Description |
|------|-------------|
| [`../ui/contact-app/src/test/setup.ts`](../ui/contact-app/src/test/setup.ts) | Test setup for Vitest (jest-dom, mocks). |
| [`../ui/contact-app/src/test/test-utils.tsx`](../ui/contact-app/src/test/test-utils.tsx) | Custom render with providers (QueryClient, Router). |
| [`../ui/contact-app/src/lib/schemas.test.ts`](../ui/contact-app/src/lib/schemas.test.ts) | Zod schema validation tests. |
| [`../ui/contact-app/src/components/forms/ContactForm.test.tsx`](../ui/contact-app/src/components/forms/ContactForm.test.tsx) | ContactForm component tests. |
| [`../ui/contact-app/src/pages/ContactsPage.test.tsx`](../ui/contact-app/src/pages/ContactsPage.test.tsx) | ContactsPage component tests. |
| [`../ui/contact-app/e2e/contacts.spec.ts`](../ui/contact-app/e2e/contacts.spec.ts) | Playwright E2E tests for Contacts CRUD. |

### Build & Configuration
| Path | Description |
|------|-------------|
| [`../pom.xml`](../pom.xml) | Maven project file with Spring Boot 4.0.0 parent and dependencies. |
| [`../config/checkstyle/checkstyle.xml`](../config/checkstyle/checkstyle.xml) | Custom Checkstyle rules enforced in CI. |
| [`../config/owasp-suppressions.xml`](../config/owasp-suppressions.xml) | Placeholder suppression list for OWASP Dependency-Check. |
| [`../scripts/ci_metrics_summary.py`](../scripts/ci_metrics_summary.py) | Prints the QA metrics table (tests/coverage/mutations/dependencies) in GitHub Actions. |
| [`../scripts/serve_quality_dashboard.py`](../scripts/serve_quality_dashboard.py) | Launches a local server for `target/site/qa-dashboard` when reading downloaded artifacts. |
| [`../scripts/api_fuzzing.py`](../scripts/api_fuzzing.py) | API fuzzing helper for local Schemathesis runs (starts app, fuzzes, exports OpenAPI spec). |
| [`architecture/2025-11-19-task-entity-and-service.md`](architecture/2025-11-19-task-entity-and-service.md) | Task entity/service plan with Definition of Done and phase breakdown. |
| [`architecture/2025-11-24-appointment-entity-and-service.md`](architecture/2025-11-24-appointment-entity-and-service.md) | Appointment entity/service implementation record. |
| [`architecture/threat-model.md`](architecture/threat-model.md) | End-to-end threat model (assets, trust zones, mitigations, residual risks). |
| [`adrs/README.md`](adrs/README.md) | ADR index: CS320 Foundation + Production Auth + Frontend fixes + Security Audit (ADR-0001-0054) complete. |
| [`design-notes/README.md`](design-notes/README.md) | Landing page for informal design notes (individual topics in `design-notes/notes/`). |
| [`logs/backlog.md`](logs/backlog.md) | Backlog for reporting and domain enhancements. |
| [`logs/CHANGELOG.md`](logs/CHANGELOG.md) | Project changelog. |
| [`../.github/workflows`](../.github/workflows) | GitHub Actions pipelines (CI, release packaging, CodeQL, API fuzzing). |

### Operations & Deployment
| Path | Description |
|------|-------------|
| [`operations/README.md`](operations/README.md) | Operations documentation index with quick reference commands. |
| [`operations/DOCKER_SETUP.md`](operations/DOCKER_SETUP.md) | Docker setup guide: building images, docker-compose, health checks, troubleshooting. |
| [`operations/ACTUATOR_ENDPOINTS.md`](operations/ACTUATOR_ENDPOINTS.md) | Actuator endpoints reference: health, metrics, prometheus, liveness/readiness probes. |
| [`../Dockerfile`](../Dockerfile) | Multi-stage production Docker image (Eclipse Temurin 17, non-root user, layered JAR). |
| [`../docker-compose.yml`](../docker-compose.yml) | Production-like stack (Postgres + App + optional pgAdmin) with health checks. |
| [`../docker-compose.dev.yml`](../docker-compose.dev.yml) | Development stack (Postgres only for local development). |
| [`../.env.example`](../.env.example) | Environment variable template for Docker deployment. |

## Milestone Requirements (Original Assignments)

| Path | Description |
|------|-------------|
| [`cs320-requirements/contact-requirements/`](cs320-requirements/contact-requirements/) | Original Contact class specification and checklist. |
| [`cs320-requirements/task-requirements/`](cs320-requirements/task-requirements/) | Original Task class specification and checklist. |
| [`cs320-requirements/appointment-requirements/`](cs320-requirements/appointment-requirements/) | Original Appointment class specification and checklist. |
| [`showcase/PROJECT_SHOWCASE.md`](showcase/PROJECT_SHOWCASE.md) | Comprehensive recruiter-focused project guide with demos. |
