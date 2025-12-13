# Implementation Plan: Project/Task Tracker Evolution (Option C)

## Overview

This plan evolves the existing Contact/Task/Appointment application into a **per-user project + task + calendar tracker** without discarding any existing functionality.

**Entity Roles After Evolution:**
- **User** = account / team member
- **Project** = container for tasks (like Trello board / Jira project)
- **Task** = work item / ticket with status and due dates
- **Appointment** = scheduled event linked to projects/tasks
- **Contact** = external person (client/stakeholder) - unchanged

---

## Phase 1: Project Entity

### 1.1 Domain Layer

**New File: `src/main/java/contactapp/domain/Project.java`**

```java
public final class Project {
    private final String projectId;      // 1-10 chars, immutable
    private String name;                 // 1-50 chars
    private String description;          // 1-100 chars (optional)
    private ProjectStatus status;        // ACTIVE, ON_HOLD, COMPLETED, ARCHIVED

    // Constructor with validation
    // update(name, description, status) method
    // copy() method for defensive copying
}
```

**New File: `src/main/java/contactapp/domain/ProjectStatus.java`**

```java
public enum ProjectStatus {
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    ARCHIVED
}
```

**Update: `src/main/java/contactapp/domain/Validation.java`**

Add constants:
```java
public static final int MAX_PROJECT_NAME_LENGTH = 50;
public static final int MAX_PROJECT_DESCRIPTION_LENGTH = 100;
```

### 1.2 Database Migration

**New File: `src/main/resources/db/migration/common/V8__create_projects_table.sql`**

```sql
CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    project_id VARCHAR(10) NOT NULL,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_projects_project_id_user_id
        UNIQUE (project_id, user_id),
    CONSTRAINT chk_projects_status
        CHECK (status IN ('ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'))
);

CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_projects_status ON projects(status);
```

### 1.3 Persistence Layer

**New Files:**
- `src/main/java/contactapp/persistence/entity/ProjectEntity.java`
- `src/main/java/contactapp/persistence/repository/ProjectRepository.java`
- `src/main/java/contactapp/persistence/mapper/ProjectMapper.java`
- `src/main/java/contactapp/persistence/store/ProjectStore.java` (interface)
- `src/main/java/contactapp/persistence/store/JpaProjectStore.java`

Follow existing patterns from TaskEntity/TaskRepository/TaskMapper/JpaTaskStore.

### 1.4 Service Layer

**New File: `src/main/java/contactapp/service/ProjectService.java`**

Methods:
- `addProject(Project project)` - returns boolean
- `updateProject(String projectId, String name, String description, ProjectStatus status)`
- `deleteProject(String projectId)` - returns boolean
- `getProjectById(String projectId)` - returns `Optional<Project>`
- `getAllProjects()` - per-user
- `getAllProjectsAllUsers()` - ADMIN only
- `getProjectsByStatus(ProjectStatus status)` - filtered query

### 1.5 API Layer

**New Files:**
- `src/main/java/contactapp/api/ProjectController.java`
- `src/main/java/contactapp/api/dto/ProjectRequest.java`
- `src/main/java/contactapp/api/dto/ProjectResponse.java`

**Endpoints:**
```
POST   /api/v1/projects              - Create project (201)
GET    /api/v1/projects              - List projects (200) + ?status=ACTIVE filter
GET    /api/v1/projects/{id}         - Get by ID (200/404)
PUT    /api/v1/projects/{id}         - Update (200/404)
DELETE /api/v1/projects/{id}         - Delete (204/404)
```

### 1.6 Tests

**New Test Files:**
- `src/test/java/contactapp/domain/ProjectTest.java`
- `src/test/java/contactapp/domain/ProjectStatusTest.java`
- `src/test/java/contactapp/persistence/entity/ProjectEntityTest.java`
- `src/test/java/contactapp/persistence/repository/ProjectRepositoryTest.java`
- `src/test/java/contactapp/persistence/mapper/ProjectMapperTest.java`
- `src/test/java/contactapp/persistence/store/JpaProjectStoreTest.java`
- `src/test/java/contactapp/service/ProjectServiceTest.java`
- `src/test/java/contactapp/service/ProjectServiceIT.java`
- `src/test/java/contactapp/ProjectControllerTest.java`
- `src/test/java/contactapp/ProjectControllerUnitTest.java`

---

## Phase 2: Enhance Task with Status + Due Date

### 2.1 Domain Layer Updates

**Update: `src/main/java/contactapp/domain/Task.java`**

Add fields:
```java
private TaskStatus status;           // TODO, IN_PROGRESS, DONE (default: TODO)
private LocalDate dueDate;           // Optional, nullable
private Instant createdAt;           // Set on creation
private Instant updatedAt;           // Set on update
```

**New File: `src/main/java/contactapp/domain/TaskStatus.java`**

```java
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
```

**Update: `src/main/java/contactapp/domain/Validation.java`**

Add method:
```java
public static LocalDate validateDueDateNotPast(LocalDate date, String label, Clock clock) {
    // Similar to validateDateNotPast but for LocalDate
}
```

### 2.2 Database Migration

**New File: `src/main/resources/db/migration/common/V9__enhance_tasks_with_status_and_duedate.sql`**

```sql
ALTER TABLE tasks ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'TODO';
ALTER TABLE tasks ADD COLUMN due_date DATE;
ALTER TABLE tasks ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tasks ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE tasks ADD CONSTRAINT chk_tasks_status
    CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE'));

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_due_date ON tasks(due_date);
```

### 2.3 Persistence Layer Updates

**Update: `src/main/java/contactapp/persistence/entity/TaskEntity.java`**

Add columns:
```java
@Column(name = "status", nullable = false)
@Enumerated(EnumType.STRING)
private TaskStatus status = TaskStatus.TODO;

@Column(name = "due_date")
private LocalDate dueDate;

@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

@Column(name = "updated_at", nullable = false)
private Instant updatedAt;

@PrePersist
void prePersist() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
}

@PreUpdate
void preUpdate() {
    updatedAt = Instant.now();
}
```

**Update: `src/main/java/contactapp/persistence/repository/TaskRepository.java`**

Add query methods:
```java
List<TaskEntity> findByUserAndStatus(User user, TaskStatus status);
List<TaskEntity> findByUserAndDueDateBefore(User user, LocalDate date);
List<TaskEntity> findByUserAndDueDateBetween(User user, LocalDate start, LocalDate end);
```

**Update: `src/main/java/contactapp/persistence/mapper/TaskMapper.java`**

Map new fields in both directions.

### 2.4 Service Layer Updates

**Update: `src/main/java/contactapp/service/TaskService.java`**

Add/modify methods:
```java
// Enhanced update
boolean updateTask(String taskId, String name, String description,
                   TaskStatus status, LocalDate dueDate);

// New filter methods
List<Task> getTasksByStatus(TaskStatus status);
List<Task> getTasksDueBefore(LocalDate date);
List<Task> getTasksDueBetween(LocalDate start, LocalDate end);
List<Task> getOverdueTasks(); // due_date < today AND status != DONE
```

### 2.5 API Layer Updates

**Update DTOs:**

`TaskRequest.java`:
```java
public record TaskRequest(
    @NotBlank @Size(max = 10) String id,
    @NotBlank @Size(max = 20) String name,
    @NotBlank @Size(max = 50) String description,
    TaskStatus status,        // Optional, defaults to TODO
    LocalDate dueDate         // Optional
) {}
```

`TaskResponse.java`:
```java
public record TaskResponse(
    String id,
    String name,
    String description,
    TaskStatus status,
    LocalDate dueDate,
    Instant createdAt,
    Instant updatedAt
) {}
```

**Update Controller:**

Add query params to GET /api/v1/tasks:
- `?status=TODO` - filter by status
- `?dueBefore=2024-12-31` - filter by due date
- `?overdue=true` - show overdue tasks only

### 2.6 Tests

Update all existing Task tests + add new tests for:
- Status transitions
- Due date validation
- Filter queries
- Overdue task detection

---

## Phase 3: Link Tasks to Projects

### 3.1 Domain Layer Updates

**Update: `src/main/java/contactapp/domain/Task.java`**

Add field:
```java
private String projectId;  // Optional FK to Project (can be null = unassigned)
```

### 3.2 Database Migration

**New File: `src/main/resources/db/migration/common/V9__add_project_fk_to_tasks.sql`**

```sql
ALTER TABLE tasks ADD COLUMN project_id BIGINT;

ALTER TABLE tasks ADD CONSTRAINT fk_tasks_project
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL;

CREATE INDEX idx_tasks_project_id ON tasks(project_id);
```

### 3.3 Persistence Layer Updates

**Update: `TaskEntity.java`**

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "project_id")
private ProjectEntity project;  // nullable
```

**Update: `TaskRepository.java`**

```java
List<TaskEntity> findByUserAndProject(User user, ProjectEntity project);
List<TaskEntity> findByUserAndProjectIsNull(User user);  // Unassigned tasks
```

### 3.4 Service Layer Updates

**Update: `TaskService.java`**

```java
List<Task> getTasksByProject(String projectId);
List<Task> getUnassignedTasks();  // Tasks with no project
boolean assignTaskToProject(String taskId, String projectId);
boolean removeTaskFromProject(String taskId);  // Sets projectId to null
```

### 3.5 API Layer Updates

**Update Task endpoints:**

- `GET /api/v1/tasks?projectId={projectId}` - tasks in project
- `GET /api/v1/tasks?projectId=none` - unassigned tasks
- `PATCH /api/v1/tasks/{id}/project` - assign/remove project

**Update TaskResponse:**
```java
String projectId,  // nullable
String projectName // nullable, for display convenience
```

---

## Phase 4: Link Appointments to Tasks/Projects

### 4.1 Domain Layer Updates

**Update: `src/main/java/contactapp/domain/Appointment.java`**

Add fields:
```java
private String taskId;     // Optional FK to Task (soft reference)
private String projectId;  // Optional FK to Project (soft reference)
```

Semantics:
- Appointment with `taskId` = "meeting/review for this task"
- Appointment with `projectId` only = "project milestone/demo/release"
- Appointment with neither = standalone calendar event (current behavior)

### 4.2 Database Migration

**New File: `src/main/resources/db/migration/common/V10__link_appointments_to_tasks_projects.sql`**

```sql
ALTER TABLE appointments ADD COLUMN task_id BIGINT;
ALTER TABLE appointments ADD COLUMN project_id BIGINT;

ALTER TABLE appointments ADD CONSTRAINT fk_appointments_task
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL;
ALTER TABLE appointments ADD CONSTRAINT fk_appointments_project
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL;

CREATE INDEX idx_appointments_task_id ON appointments(task_id);
CREATE INDEX idx_appointments_project_id ON appointments(project_id);
```

### 4.3 Persistence/Service/API Updates

Follow same pattern as Phase 3 for Task-Project linking.

**New queries:**
```java
List<Appointment> getAppointmentsByTask(String taskId);
List<Appointment> getAppointmentsByProject(String projectId);
```

**Calendar view support:**
- `GET /api/v1/appointments?start=2024-01-01&end=2024-01-31` - date range
- `GET /api/v1/appointments?projectId={id}` - by project

---

## Phase 5: Task Assignment (Collaboration)

### 5.1 Domain Layer Updates

**Update: `src/main/java/contactapp/domain/Task.java`**

Add field:
```java
private Long assignedToUserId;  // FK to User (nullable = unassigned)
```

### 5.2 Database Migration

**New File: `src/main/resources/db/migration/common/V11__add_assignee_to_tasks.sql`**

```sql
ALTER TABLE tasks ADD COLUMN assigned_to_user_id BIGINT;

ALTER TABLE tasks ADD CONSTRAINT fk_tasks_assignee
    FOREIGN KEY (assigned_to_user_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_tasks_assigned_to_user_id ON tasks(assigned_to_user_id);
```

### 5.3 Service Layer Updates

**Update: `TaskService.java`**

```java
// Assignment methods
boolean assignTask(String taskId, Long userId);
boolean unassignTask(String taskId);

// Query by assignee
List<Task> getTasksAssignedToMe();  // Current user
List<Task> getTasksAssignedTo(Long userId);  // For project owners/admins
List<Task> getTasksCreatedByMeAssignedToOthers();
```

### 5.4 Access Control Considerations

**Visibility Rules:**
1. Users see tasks they **own** (created)
2. Users see tasks **assigned to them** (even if owned by another user)
3. Project owners see all tasks in their projects
4. ADMIN sees everything

**Update `TaskService.getAllTasks()`:**
```java
// Return: owned tasks + assigned-to-me tasks
List<Task> myTasks = store.findByUser(currentUser);
List<Task> assignedToMe = store.findByAssignee(currentUser);
return mergeDeduplicate(myTasks, assignedToMe);
```

### 5.5 API Updates

**New endpoints:**
- `PATCH /api/v1/tasks/{id}/assignee` - assign task
- `DELETE /api/v1/tasks/{id}/assignee` - unassign task
- `GET /api/v1/tasks?assignee=me` - my assigned tasks
- `GET /api/v1/tasks?assignee={userId}` - tasks assigned to user

**Update TaskResponse:**
```java
Long assignedToUserId,
String assignedToUsername  // For display
```

---

## Phase 6: Optional Contact Linking

### 6.1 Link Contacts to Projects (Client/Stakeholder)

**Migration V12:**
```sql
CREATE TABLE project_contacts (
    project_id BIGINT NOT NULL,
    contact_id BIGINT NOT NULL,
    role VARCHAR(50),  -- 'CLIENT', 'STAKEHOLDER', 'VENDOR', etc.
    PRIMARY KEY (project_id, contact_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (contact_id) REFERENCES contacts(id) ON DELETE CASCADE
);
```

This is optional and can be deferred.

---

## Implementation Order

```
Phase 1: Project Entity                    [Foundation]
    ├── 1.1 Domain + Validation
    ├── 1.2 Migration V8
    ├── 1.3 Persistence layer
    ├── 1.4 Service layer
    ├── 1.5 API layer
    └── 1.6 Tests

Phase 2: Task Status + Due Date            [Core Enhancement]
    ├── 2.1 Domain updates
    ├── 2.2 Migration V8
    ├── 2.3 Persistence updates
    ├── 2.4 Service updates
    ├── 2.5 API updates
    └── 2.6 Tests

Phase 3: Task-Project Linking              [Organization]
    ├── 3.1 Domain updates
    ├── 3.2 Migration V9
    ├── 3.3 Persistence updates
    ├── 3.4 Service updates
    └── 3.5 API updates + Tests

Phase 4: Appointment Linking               [Calendar Integration]
    ├── 4.1 Domain updates
    ├── 4.2 Migration V10
    └── 4.3 Full stack updates + Tests

Phase 5: Task Assignment                   [Collaboration]
    ├── 5.1 Domain updates
    ├── 5.2 Migration V11
    ├── 5.3 Service updates
    ├── 5.4 Access control updates
    └── 5.5 API updates + Tests

Phase 6: Contact-Project Linking           [Optional/Future]
    └── 6.1 Migration V12 + Full stack
```

---

## Breaking Changes & Migration Notes

### Backward Compatibility

1. **Task API**: Existing clients sending `{id, name, description}` will still work
   - `status` defaults to `TODO`
   - `dueDate`, `projectId`, `assignedToUserId` default to `null`

2. **Appointment API**: Same - new fields are optional

3. **Database**: All migrations use `ADD COLUMN` with defaults, no data loss

### Frontend Updates Needed

1. **Task List View**: Add status badges, due date display, project filter
2. **Task Form**: Add status dropdown, date picker, project selector
3. **New Project Pages**: List, Create, Edit, Detail views
4. **Calendar View**: Color-code appointments by project
5. **Dashboard**: Overdue tasks widget, tasks by status chart

---

## Validation Summary

| Entity | Field | Constraint |
|--------|-------|------------|
| Project | projectId | 1-10 chars, immutable |
| Project | name | 1-50 chars |
| Project | description | 0-100 chars (optional) |
| Project | status | ACTIVE/ON_HOLD/COMPLETED/ARCHIVED |
| Task | status | TODO/IN_PROGRESS/DONE |
| Task | dueDate | Optional, no past validation (can have past due dates) |
| Task | projectId | Optional FK, ON DELETE SET NULL |
| Task | assignedToUserId | Optional FK, ON DELETE SET NULL |
| Appointment | taskId | Optional FK, ON DELETE SET NULL |
| Appointment | projectId | Optional FK, ON DELETE SET NULL |

---

## API Endpoint Summary (After All Phases)

### Projects
```
POST   /api/v1/projects              - Create
GET    /api/v1/projects              - List (+ ?status=ACTIVE)
GET    /api/v1/projects/{id}         - Get
PUT    /api/v1/projects/{id}         - Update
DELETE /api/v1/projects/{id}         - Delete
```

### Tasks (Enhanced)
```
POST   /api/v1/tasks                 - Create
GET    /api/v1/tasks                 - List (+ filters below)
       ?status=TODO|IN_PROGRESS|DONE
       ?projectId={id}|none
       ?assignee=me|{userId}
       ?dueBefore={date}
       ?overdue=true
GET    /api/v1/tasks/{id}            - Get
PUT    /api/v1/tasks/{id}            - Update
DELETE /api/v1/tasks/{id}            - Delete
PATCH  /api/v1/tasks/{id}/project    - Assign to project
PATCH  /api/v1/tasks/{id}/assignee   - Assign to user
```

### Appointments (Enhanced)
```
POST   /api/v1/appointments          - Create
GET    /api/v1/appointments          - List (+ filters)
       ?start={date}&end={date}
       ?projectId={id}
       ?taskId={id}
GET    /api/v1/appointments/{id}     - Get
PUT    /api/v1/appointments/{id}     - Update
DELETE /api/v1/appointments/{id}     - Delete
```

---

## Estimated File Changes

### New Files (~25)
- Domain: 3 (Project, ProjectStatus, TaskStatus)
- Persistence: 5 (Entity, Repo, Mapper, Store interface, JpaStore)
- Service: 1 (ProjectService)
- API: 3 (Controller, Request DTO, Response DTO)
- Tests: 13+ (Unit + Integration for each layer)

### Modified Files (~15)
- Task.java, TaskEntity.java, TaskRepository.java, TaskMapper.java
- JpaTaskStore.java, TaskService.java, TaskController.java
- TaskRequest.java, TaskResponse.java
- Appointment.java, AppointmentEntity.java, etc.
- Validation.java
- All existing Task/Appointment tests

### Migrations (~5-6)
- V7: projects table
- V8: tasks status/dueDate
- V9: tasks.project_id FK
- V10: appointments.task_id, project_id FKs
- V11: tasks.assigned_to_user_id FK
- V12: project_contacts junction (optional)