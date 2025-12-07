# Jira-Like Evolution Roadmap

> Evolving ContactApp into a full-featured project management platform

**Status:** Planning | **Created:** 2025-12-03 | **Updated:** 2025-12-03 | **Owner:** Justin Guida

---

## Table of Contents

1. [Current State Summary](#current-state-summary)
2. [Evolution Roadmap](#evolution-roadmap)
3. [Phase 8-10: Backend Foundations](#phase-8-task-enhancements)
4. [Phase 11: Kanban Board UI](#phase-11-kanban-board-ui)
5. [**Phase UI-1: Frontend Polish & UX**](#phase-ui-1-frontend-polish--ux) ← NEW
6. [**Phase UI-2: Advanced Theme System**](#phase-ui-2-advanced-theme-system) ← NEW
7. [**Phase UI-3: Advanced Frontend Features**](#phase-ui-3-advanced-frontend-features) ← NEW
8. [Phase 12-17: Advanced Backend](#phase-12-reporting--analytics)
9. [Implementation Priority Matrix](#implementation-priority-matrix)

---

## Current State Summary

### What We Have (Phases 0-7 Complete)

| Domain          | Features                                                                   |
|-----------------|----------------------------------------------------------------------------|
| **User**        | JWT auth, roles (USER/ADMIN), BCrypt passwords, session management         |
| **Project**     | CRUD, status (ACTIVE/ON_HOLD/COMPLETED/ARCHIVED), contact linking          |
| **Task**        | CRUD, status (TODO/IN_PROGRESS/DONE), due dates, project linking, assignee |
| **Appointment** | CRUD, task/project linking, calendar context                               |
| **Contact**     | CRUD, project stakeholder linking                                          |

### Technical Foundation

| Category     | Implementation                                              |
|--------------|-------------------------------------------------------------|
| **Backend**  | Spring Boot 3.4, Spring Security 7, JPA/Hibernate           |
| **Frontend** | React 19, Vite 7, Tailwind v4, shadcn/ui, TanStack Query    |
| **Database** | PostgreSQL + Flyway (17 migrations)                         |
| **Testing**  | 1107 tests, 84% mutation coverage, E2E with Playwright      |
| **CI/CD**    | GitHub Actions, CodeQL, ZAP, API fuzzing, Docker            |
| **Security** | JWT HttpOnly cookies, CSRF, rate limiting, refresh tokens   |
| **Docs**     | 54 ADRs, threat model, design notes                         |
| **Theming**  | 5 color themes, dark/light mode, CSS variables              |

---

## Evolution Roadmap

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           JIRA-LIKE EVOLUTION                                │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  CURRENT STATE ────────────────────────────────────────────────────────────► │
│  ✅ Projects, Tasks, Appointments, Contacts, Users                           │
│  ✅ Basic task status workflow (TODO → IN_PROGRESS → DONE)                   │
│  ✅ Assignment, due dates, project linking                                   │
│                                                                              │
│  PHASE 8: Task Enhancements ────────────────────────────────────────────────►│
│  • Task types (Bug, Story, Epic, Subtask)                                    │
│  • Priority levels (Critical, High, Medium, Low)                             │
│  • Story points / effort estimation                                          │
│  • Labels/tags for categorization                                            │
│                                                                              │
│  PHASE 9: Sprint Management ───────────────────────────────────────────────► │
│  • Sprint entity with start/end dates                                        │
│  • Backlog vs sprint views                                                   │
│  • Sprint planning and capacity                                              │
│  • Velocity tracking                                                         │
│                                                                              │
│  PHASE 10: Activity & Comments ────────────────────────────────────────────► │
│  • Task comments with rich text                                              │
│  • Activity/audit log per task                                               │
│  • @mentions in comments                                                     │
│  • File attachments                                                          │
│                                                                              │
│  PHASE 11: Kanban Board UI ────────────────────────────────────────────────► │
│  • Drag-and-drop columns                                                     │
│  • Swimlanes (by assignee, priority)                                         │
│  • WIP limits per column                                                     │
│  • Quick filters and search                                                  │
│                                                                              │
│  PHASE 12: Reporting & Analytics ──────────────────────────────────────────► │
│  • Burndown/burnup charts                                                    │
│  • Velocity charts                                                           │
│  • Cumulative flow diagrams                                                  │
│  • Custom dashboards                                                         │
│                                                                              │
│  PHASE 13: Notifications & Real-time ──────────────────────────────────────► │
│  • WebSocket for live updates                                                │
│  • Email notifications                                                       │
│  • In-app notification center                                                │
│  • Watchers/subscribers on tasks                                             │
│                                                                              │
│  PHASE 14: Epic & Roadmap ─────────────────────────────────────────────────► │
│  • Epic entity (story grouping)                                              │
│  • Task dependencies (blocked by)                                            │
│  • Timeline/Gantt view                                                       │
│  • Release planning                                                          │
│                                                                              │
│  PHASE 15: Advanced Workflows ─────────────────────────────────────────────► │
│  • Custom status definitions per project                                     │
│  • Workflow transitions with rules                                           │
│  • Automation (auto-assign, triggers)                                        │
│  • SLA tracking                                                              │
│                                                                              │
│  PHASE 16: Integrations ───────────────────────────────────────────────────► │
│  • GitHub/GitLab commit linking                                              │
│  • Slack notifications                                                       │
│  • Webhook system                                                            │
│  • Public API with OAuth                                                     │
│                                                                              │
│  PHASE 17: Teams & Permissions ────────────────────────────────────────────► │
│  • Team entity with members                                                  │
│  • Project-level permissions                                                 │
│  • Team workload views                                                       │
│  • Organization hierarchy                                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 8: Task Enhancements

**Goal:** Transform simple tasks into rich work items like Jira issues

### New Domain Models

```java
// TaskType.java
public enum TaskType {
    TASK,       // Generic work item (default)
    BUG,        // Defect/issue to fix
    STORY,      // User story (feature)
    EPIC,       // Large body of work (contains stories)
    SUBTASK     // Child of another task
}

// Priority.java
public enum Priority {
    CRITICAL,   // P0 - Drop everything
    HIGH,       // P1 - Do next
    MEDIUM,     // P2 - Normal priority (default)
    LOW         // P3 - Nice to have
}

// Label.java (new entity)
public class Label {
    private String labelId;      // Unique per project
    private String name;         // Display name (e.g., "frontend")
    private String color;        // Hex color code
    private String projectId;    // Scoped to project
}
```

### Task Domain Updates

```java
// Task.java additions
private TaskType type;           // BUG, STORY, EPIC, SUBTASK
private Priority priority;       // CRITICAL, HIGH, MEDIUM, LOW
private Integer storyPoints;     // Effort estimation (1, 2, 3, 5, 8, 13, 21)
private String parentTaskId;     // For subtasks - FK to parent task
private Set<String> labelIds;    // Many-to-many with labels
```

### Database Migrations

```sql
-- V14__add_task_type_and_priority.sql
ALTER TABLE tasks ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'TASK';
ALTER TABLE tasks ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE tasks ADD COLUMN story_points INTEGER;
ALTER TABLE tasks ADD COLUMN parent_task_id BIGINT;

ALTER TABLE tasks ADD CONSTRAINT chk_tasks_type
    CHECK (type IN ('TASK', 'BUG', 'STORY', 'EPIC', 'SUBTASK'));
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_priority
    CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'));
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_parent
    FOREIGN KEY (parent_task_id) REFERENCES tasks(id) ON DELETE CASCADE;

CREATE INDEX idx_tasks_type ON tasks(type);
CREATE INDEX idx_tasks_priority ON tasks(priority);
CREATE INDEX idx_tasks_parent ON tasks(parent_task_id);

-- V18__create_labels_table.sql (future - after current V17)
CREATE TABLE labels (
    id BIGSERIAL PRIMARY KEY,
    label_id VARCHAR(20) NOT NULL,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(7) NOT NULL DEFAULT '#6B7280',
    project_id BIGINT NOT NULL,
    CONSTRAINT fk_labels_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_labels_project_label_id UNIQUE (project_id, label_id)
);

CREATE TABLE task_labels (
    task_id BIGINT NOT NULL,
    label_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, label_id),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (label_id) REFERENCES labels(id) ON DELETE CASCADE
);
```

### API Endpoints

```
POST   /api/v1/projects/{projectId}/labels     - Create label
GET    /api/v1/projects/{projectId}/labels     - List project labels
DELETE /api/v1/projects/{projectId}/labels/{id} - Delete label

GET    /api/v1/tasks?type=BUG                  - Filter by type
GET    /api/v1/tasks?priority=HIGH             - Filter by priority
GET    /api/v1/tasks/{id}/subtasks             - Get subtasks
POST   /api/v1/tasks/{id}/labels               - Add label to task
DELETE /api/v1/tasks/{id}/labels/{labelId}     - Remove label
```

### Test Coverage

- `TaskTypeTest.java` - Enum coverage
- `PriorityTest.java` - Enum coverage
- `LabelTest.java` - Domain validation
- `LabelServiceTest.java` - CRUD operations
- `TaskLabelingTest.java` - Many-to-many operations
- `SubtaskTest.java` - Parent-child relationships

---

## Phase 9: Sprint Management

**Goal:** Enable Scrum-style iteration planning

### New Domain Models

```java
// Sprint.java
public class Sprint {
    private String sprintId;        // Unique identifier
    private String name;            // "Sprint 1", "Q1 Week 3"
    private String goal;            // Sprint objective
    private String projectId;       // FK to project
    private LocalDate startDate;    // Sprint start
    private LocalDate endDate;      // Sprint end
    private SprintStatus status;    // PLANNING, ACTIVE, COMPLETED
    private Integer capacity;       // Team capacity in story points
}

// SprintStatus.java
public enum SprintStatus {
    PLANNING,   // Not yet started
    ACTIVE,     // Currently in progress (only 1 per project)
    COMPLETED   // Sprint finished
}
```

### Task Domain Updates

```java
// Task.java additions
private String sprintId;         // FK to current sprint (nullable = backlog)
```

### Database Migrations

```sql
-- V19__create_sprints_table.sql (future)
CREATE TABLE sprints (
    id BIGSERIAL PRIMARY KEY,
    sprint_id VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    goal VARCHAR(500),
    project_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNING',
    capacity INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT fk_sprints_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_sprints_project_sprint_id UNIQUE (project_id, sprint_id),
    CONSTRAINT chk_sprints_status CHECK (status IN ('PLANNING', 'ACTIVE', 'COMPLETED')),
    CONSTRAINT chk_sprints_dates CHECK (end_date >= start_date)
);

CREATE INDEX idx_sprints_project_id ON sprints(project_id);
CREATE INDEX idx_sprints_status ON sprints(status);

-- V20__add_sprint_to_tasks.sql (future)
ALTER TABLE tasks ADD COLUMN sprint_id BIGINT;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_sprint
    FOREIGN KEY (sprint_id) REFERENCES sprints(id) ON DELETE SET NULL;
CREATE INDEX idx_tasks_sprint_id ON tasks(sprint_id);
```

### API Endpoints

```
POST   /api/v1/projects/{projectId}/sprints           - Create sprint
GET    /api/v1/projects/{projectId}/sprints           - List sprints
GET    /api/v1/projects/{projectId}/sprints/active    - Get active sprint
GET    /api/v1/projects/{projectId}/sprints/{id}      - Get sprint details
PUT    /api/v1/projects/{projectId}/sprints/{id}      - Update sprint
POST   /api/v1/projects/{projectId}/sprints/{id}/start   - Start sprint
POST   /api/v1/projects/{projectId}/sprints/{id}/complete - Complete sprint

GET    /api/v1/projects/{projectId}/backlog           - Get backlog (unassigned)
POST   /api/v1/tasks/{taskId}/sprint                  - Move task to sprint
DELETE /api/v1/tasks/{taskId}/sprint                  - Move task to backlog

GET    /api/v1/projects/{projectId}/velocity          - Sprint velocity history
```

### Sprint Metrics

```java
// SprintMetrics.java
public record SprintMetrics(
    int totalPoints,          // Sum of story points in sprint
    int completedPoints,      // Points for DONE tasks
    int remainingPoints,      // Points for non-DONE tasks
    int taskCount,            // Total tasks
    int completedCount,       // DONE tasks
    double completionRate,    // completedCount / taskCount
    List<DailyProgress> burndown  // For burndown chart
) {}

public record DailyProgress(
    LocalDate date,
    int remainingPoints,
    int idealRemaining
) {}
```

---

## Phase 10: Activity & Comments

**Goal:** Enable collaboration through discussions and audit trails

### New Domain Models

```java
// Comment.java
public class Comment {
    private String commentId;
    private String taskId;           // FK to task
    private Long authorId;           // FK to user
    private String content;          // Markdown-supported text
    private Instant createdAt;
    private Instant updatedAt;
    private boolean edited;          // Was this comment modified?
}

// ActivityEntry.java (audit log)
public class ActivityEntry {
    private Long id;
    private String taskId;
    private UUID userId;             // UUID per ADR-0052
    private ActivityType type;       // STATUS_CHANGED, ASSIGNED, etc.
    private String oldValue;
    private String newValue;
    private Instant timestamp;
}

// ActivityType.java
public enum ActivityType {
    CREATED,
    STATUS_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    PRIORITY_CHANGED,
    DUE_DATE_CHANGED,
    SPRINT_CHANGED,
    COMMENT_ADDED,
    LABEL_ADDED,
    LABEL_REMOVED,
    ATTACHMENT_ADDED
}
```

### Database Migrations

```sql
-- V21__create_comments_table.sql (future)
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    comment_id VARCHAR(36) NOT NULL,
    task_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_comments_task FOREIGN KEY (task_id)
        REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id)
        REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_comments_task_id ON comments(task_id);
CREATE INDEX idx_comments_author_id ON comments(author_id);
CREATE INDEX idx_comments_created_at ON comments(created_at);

-- V22__create_activity_log.sql (future)
CREATE TABLE activity_log (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT,
    activity_type VARCHAR(30) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_activity_task FOREIGN KEY (task_id)
        REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_activity_task_id ON activity_log(task_id);
CREATE INDEX idx_activity_timestamp ON activity_log(timestamp);
```

### API Endpoints

```
POST   /api/v1/tasks/{taskId}/comments              - Add comment
GET    /api/v1/tasks/{taskId}/comments              - List comments
PUT    /api/v1/tasks/{taskId}/comments/{id}         - Edit comment
DELETE /api/v1/tasks/{taskId}/comments/{id}         - Delete comment

GET    /api/v1/tasks/{taskId}/activity              - Get activity log
GET    /api/v1/projects/{projectId}/activity        - Project-wide activity
GET    /api/v1/activity/me                          - My recent activity
```

### Mention System

```java
// MentionService.java
public class MentionService {
    // Parse @username from comment content
    public Set<String> extractMentions(String content);

    // Create notifications for mentioned users
    public void notifyMentionedUsers(Comment comment, Set<String> usernames);
}
```

---

## Phase 11: Kanban Board UI

**Goal:** Visual drag-and-drop task management

### Frontend Components

```
ui/contact-app/src/
├── components/
│   └── board/
│       ├── KanbanBoard.tsx       # Main board container
│       ├── KanbanColumn.tsx      # Status column (TODO, IN_PROGRESS, DONE)
│       ├── KanbanCard.tsx        # Task card with preview
│       ├── KanbanSwimlane.tsx    # Horizontal grouping (by assignee)
│       └── QuickCreateCard.tsx   # Inline task creation
├── hooks/
│   ├── useDragAndDrop.ts         # DnD logic with optimistic updates
│   └── useBoardFilters.ts        # Filter state management
└── pages/
    └── BoardPage.tsx             # Board view route
```

### Key Features

1. **Drag-and-Drop**
   - React DnD or dnd-kit library
   - Optimistic UI updates
   - Auto-save on drop
   - Undo capability

2. **Columns**
   - One column per task status
   - WIP limits with visual warnings
   - Collapsed column support
   - Column task count

3. **Cards**
   - Task type icon (bug, story, etc.)
   - Priority indicator (color bar)
   - Assignee avatar
   - Due date warning (overdue = red)
   - Story points badge
   - Label chips

4. **Swimlanes**
   - Group by: None, Assignee, Priority, Epic
   - Collapsible rows
   - Row task counts

5. **Quick Filters**
   - My tasks only
   - By label
   - By assignee
   - By priority
   - Search by title

### API Updates

```
PATCH /api/v1/tasks/{id}/status    - Quick status update (for drag-drop)
PATCH /api/v1/tasks/reorder        - Batch reorder within column

Request: { taskIds: ["T1", "T2", "T3"], status: "IN_PROGRESS" }
```

---

## Phase UI-1: Frontend Polish & UX

**Goal:** Elevate the UI from functional to professional SaaS quality

### Current UI Issues (Identified from Screenshots)

| Issue | Impact | Fix |
|-------|--------|-----|
| Low dark mode contrast | Text/borders blend into background | Increase border opacity, use `border-border/50` |
| Inconsistent spacing | Cards/tables have varying padding | Standardize with `px-6 py-4` pattern |
| Flat typography hierarchy | Headings feel same weight as body | Use distinct sizes: `2xl`, `base`, `sm` |
| Missing hover states | Tables don't highlight on hover | Add `hover:bg-muted cursor-pointer` |
| No loading feedback | Users see blank during data fetch | Add skeleton loaders |
| Action icons lack context | Edit/Delete unclear without labels | Add Radix tooltips |

### A. Design Token Refinement

Already have CSS variables in `index.css`, but need to ensure consistency:

```css
/* Ensure all themes define these critical tokens */
:root {
  --background: 220 13% 96%;
  --foreground: 222.2 84% 4.9%;
  --card: 0 0% 100%;
  --card-foreground: 222.2 84% 4.9%;
  --border: 214.3 31.8% 91.4%;
  --muted: 220 13% 91%;
  --muted-foreground: 215.4 16.3% 46.9%;
  --accent: 210 40% 96.1%;
  --accent-foreground: 222.2 47.4% 11.2%;
  --ring: 222.2 84% 4.9%;
  --radius: 0.5rem;
}
```

### B. Typography Hierarchy

```tsx
// Standardized text classes across all pages
text-2xl font-semibold   → Page titles (Dashboard, Contacts, etc.)
text-lg font-medium      → Section headings (within cards)
text-base font-medium    → Table headers
text-sm text-muted-foreground → Table row content, descriptions
text-xs text-muted-foreground → Timestamps, secondary info
```

### C. Interactive States

```tsx
// Button improvements
<Button className="transition-colors hover:bg-accent/90 active:scale-[.97]">

// Table row hover
<TableRow className="hover:bg-muted/50 cursor-pointer transition-colors">

// Icon button feedback
<button className="opacity-70 hover:opacity-100 transition-opacity">
```

### D. Skeleton Loaders

```
ui/contact-app/src/
├── components/
│   └── ui/
│       ├── skeleton.tsx           # Base skeleton component
│       ├── table-skeleton.tsx     # Table loading state
│       └── card-skeleton.tsx      # Card loading state
```

```tsx
// Usage with TanStack Query (already in place)
const { data, isLoading } = useQuery(['contacts'], fetchContacts);

if (isLoading) return <TableSkeleton rows={5} columns={4} />;
```

### E. Tooltips on Actions

```tsx
// Already have @radix-ui/react-tooltip installed
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

<Tooltip>
  <TooltipTrigger asChild>
    <Button variant="ghost" size="icon">
      <Pencil className="h-4 w-4" />
    </Button>
  </TooltipTrigger>
  <TooltipContent>Edit contact</TooltipContent>
</Tooltip>
```

### F. Empty States

```tsx
// Already have empty-state.tsx - ensure all pages use it
<EmptyState
  icon={<Inbox className="h-12 w-12" />}
  title="No contacts yet"
  description="Create your first contact to get started"
  action={<Button onClick={openCreateDialog}>Add Contact</Button>}
/>
```

### Files to Update

| File | Changes |
|------|---------|
| `index.css` | Improve dark mode contrast tokens |
| `ContactsPage.tsx` | Add hover states, tooltips, skeleton |
| `TasksPage.tsx` | Add hover states, tooltips, skeleton |
| `AppointmentsPage.tsx` | Add hover states, tooltips, skeleton |
| `ProjectsPage.tsx` | Add hover states, tooltips, skeleton |
| `OverviewPage.tsx` | Add card hover effects |
| `table.tsx` | Default hover class on TableRow |

### Effort Estimate: 1-2 days

---

## Phase UI-2: Advanced Theme System

**Goal:** Full user-customizable theming with export/import capability

### Current Theme Implementation

Already have:
- ✅ 5 theme presets (Slate, Ocean, Forest, Violet, Zinc)
- ✅ Dark/Light mode toggle
- ✅ CSS variables design token system
- ✅ localStorage persistence
- ✅ Settings page with theme picker

### A. Add More Built-in Themes (10 Total New Themes)

Original 5: `slate`, `ocean`, `forest`, `violet`, `zinc`

```typescript
// useTheme.ts - expand to 15 themes total
const themes = [
  // Existing (5)
  'slate', 'ocean', 'forest', 'violet', 'zinc',
  // New themes (10)
  'rose',      // Pink/red - warm, friendly
  'amber',     // Orange/gold - energetic, creative
  'cyan',      // Teal - modern tech
  'emerald',   // Rich green - growth
  'indigo',    // Deep blue-purple - professional
  'mono',      // Black/white high contrast - developer-focused
  'neon',      // High-saturation accent, dark background
  'pastel',    // Soft hues - Figma-style
  'terminal',  // Green-on-black hacker theme
  'latte',     // Warm beige - Notion/Arc aesthetic
] as const;
```

```css
/* index.css - all new theme definitions */

/* Rose theme - Warm, friendly (Consumer apps) */
.theme-rose {
  --primary: 346 77% 50%;
  --primary-foreground: 210 40% 98%;
  --accent: 350 89% 60%;
  --accent-foreground: 210 40% 98%;
}

/* Amber theme - Energetic (Creative tools) */
.theme-amber {
  --primary: 38 92% 50%;
  --primary-foreground: 222.2 47.4% 11.2%;
  --accent: 43 96% 56%;
  --accent-foreground: 222.2 47.4% 11.2%;
}

/* Cyan theme - Modern tech (SaaS) */
.theme-cyan {
  --primary: 187 85% 43%;
  --primary-foreground: 210 40% 98%;
  --accent: 192 91% 36%;
  --accent-foreground: 210 40% 98%;
}

/* Emerald theme - Growth (Productivity) */
.theme-emerald {
  --primary: 160 84% 39%;
  --primary-foreground: 210 40% 98%;
  --accent: 158 64% 52%;
  --accent-foreground: 222.2 47.4% 11.2%;
}

/* Indigo theme - Professional (Enterprise) */
.theme-indigo {
  --primary: 239 84% 67%;
  --primary-foreground: 210 40% 98%;
  --accent: 243 75% 59%;
  --accent-foreground: 210 40% 98%;
}

/* Mono theme - High contrast developer-focused */
.theme-mono {
  --primary: 0 0% 9%;
  --primary-foreground: 0 0% 98%;
  --accent: 0 0% 20%;
  --accent-foreground: 0 0% 98%;
  --muted: 0 0% 96%;
  --border: 0 0% 90%;
}

.dark.theme-mono {
  --primary: 0 0% 98%;
  --primary-foreground: 0 0% 9%;
  --accent: 0 0% 80%;
  --accent-foreground: 0 0% 9%;
  --muted: 0 0% 15%;
  --border: 0 0% 20%;
}

/* Neon theme - High saturation, cyberpunk style */
.theme-neon {
  --primary: 280 100% 60%;
  --primary-foreground: 0 0% 100%;
  --accent: 180 100% 50%;
  --accent-foreground: 0 0% 0%;
}

.dark.theme-neon {
  --background: 270 50% 5%;
  --primary: 300 100% 50%;
  --accent: 180 100% 50%;
}

/* Pastel theme - Soft Figma-style */
.theme-pastel {
  --background: 210 40% 98%;
  --primary: 262 52% 65%;
  --primary-foreground: 0 0% 100%;
  --accent: 339 76% 75%;
  --accent-foreground: 0 0% 100%;
  --muted: 210 40% 94%;
}

/* Terminal theme - Green-on-black hacker */
.dark.theme-terminal {
  --background: 120 10% 5%;
  --foreground: 120 100% 50%;
  --primary: 120 100% 40%;
  --primary-foreground: 0 0% 0%;
  --accent: 120 80% 60%;
  --muted: 120 20% 15%;
  --border: 120 50% 20%;
}

/* Latte theme - Warm beige, Notion/Arc style */
.theme-latte {
  --background: 40 30% 96%;
  --foreground: 30 10% 20%;
  --card: 40 30% 98%;
  --primary: 25 60% 45%;
  --primary-foreground: 40 30% 98%;
  --accent: 35 70% 50%;
  --muted: 40 20% 90%;
  --border: 40 20% 85%;
}
```

### B. Custom Theme Editor

```
ui/contact-app/src/
├── components/
│   └── settings/
│       ├── ThemeEditor.tsx        # Full theme customization UI
│       ├── ColorPicker.tsx        # HSL color picker component
│       ├── ThemePreview.tsx       # Live preview panel
│       ├── ThemeExportImport.tsx  # Export/import buttons
│       ├── ContrastChecker.tsx    # WCAG contrast validation
│       ├── FontSelector.tsx       # Font family dropdown
│       ├── SpacingSelector.tsx    # Spacing scale picker
│       └── RadiusSelector.tsx     # Border radius picker
```

#### Theme Editor Form Fields

The settings page theme editor should include:

| Field | Input Type | Options/Range |
|-------|------------|---------------|
| Theme Name | Text input | User-defined name |
| Background Color | Color picker | Any color |
| Text Color (Foreground) | Color picker | Any color |
| Primary/Accent Color | Color picker | Any color |
| Border Color | Color picker | Any color |
| Muted Color | Color picker | Any color |
| Destructive Color | Color picker | Red variants |
| Font Family | Dropdown select | System UI, Inter, Roboto, Mono, Custom |
| Spacing Scale | Segmented control | Compact / Normal / Relaxed |
| Border Radius | Slider | 0px (flat) → 16px (rounded) |

#### Live Preview Panel

Show a mini version of UI elements so user sees changes in real-time:

```tsx
// ThemePreview.tsx - shows sample UI with current theme
function ThemePreview({ theme }: { theme: CustomTheme }) {
  return (
    <div
      className="border rounded-lg p-4 space-y-4"
      style={themeToStyles(theme)}
    >
      {/* Sample Card */}
      <div className="bg-card border rounded-lg p-4">
        <h3 className="font-semibold">Sample Card</h3>
        <p className="text-muted-foreground text-sm">
          This is how your cards will look.
        </p>
      </div>

      {/* Sample Buttons */}
      <div className="flex gap-2">
        <button className="bg-primary text-primary-foreground px-4 py-2 rounded">
          Primary
        </button>
        <button className="bg-secondary text-secondary-foreground px-4 py-2 rounded">
          Secondary
        </button>
        <button className="bg-destructive text-destructive-foreground px-4 py-2 rounded">
          Delete
        </button>
      </div>

      {/* Sample Table Row */}
      <div className="border rounded">
        <div className="flex items-center justify-between p-3 hover:bg-muted">
          <span>Sample row item</span>
          <span className="text-muted-foreground">Action</span>
        </div>
      </div>

      {/* Sample Input */}
      <input
        className="w-full border rounded px-3 py-2 bg-input"
        placeholder="Sample input field"
      />
    </div>
  );
}
```

#### Full Theme Type Definition

```tsx
// types/theme.ts
interface CustomTheme {
  name: string;
  colors: {
    background: string;      // Page background
    foreground: string;      // Default text color
    card: string;            // Card backgrounds
    cardForeground: string;  // Card text
    primary: string;         // Primary buttons/links
    primaryForeground: string;
    secondary: string;       // Secondary elements
    secondaryForeground: string;
    muted: string;           // Muted backgrounds
    mutedForeground: string; // Muted text
    accent: string;          // Accent highlights
    accentForeground: string;
    border: string;          // Border color
    input: string;           // Input backgrounds
    ring: string;            // Focus rings
    destructive: string;     // Delete/error actions
    destructiveForeground: string;
  };
  typography: {
    fontFamily: 'system-ui' | 'inter' | 'roboto' | 'mono' | string;
    baseFontSize: string;    // e.g., '16px'
    headingFontSize: string; // e.g., '1.5rem'
  };
  spacing: {
    scale: 'compact' | 'normal' | 'relaxed';
    small: string;   // e.g., '0.5rem'
    medium: string;  // e.g., '1rem'
    large: string;   // e.g., '2rem'
  };
  radii: {
    base: string;    // e.g., '0.5rem'
    card: string;    // e.g., '1rem'
    button: string;  // e.g., '0.375rem'
    input: string;   // e.g., '0.375rem'
  };
}
```

```tsx
// ThemeEditor.tsx - full implementation
function ThemeEditor() {
  const [customTheme, setCustomTheme] = useState<CustomTheme>(defaultTheme);
  const [contrastWarning, setContrastWarning] = useState<string | null>(null);

  // Check WCAG contrast when colors change
  useEffect(() => {
    const ratio = getContrastRatio(
      customTheme.colors.background,
      customTheme.colors.foreground
    );
    if (ratio < 4.5) {
      setContrastWarning(
        `Low contrast (${ratio.toFixed(1)}:1). WCAG AA requires 4.5:1 minimum.`
      );
    } else {
      setContrastWarning(null);
    }
  }, [customTheme.colors.background, customTheme.colors.foreground]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Custom Theme</CardTitle>
        <CardDescription>Create your own color scheme</CardDescription>
      </CardHeader>
      <CardContent className="grid grid-cols-2 gap-6">
        {/* Left: All controls */}
        <div className="space-y-6">
          {/* Theme Name */}
          <div className="space-y-2">
            <Label>Theme Name</Label>
            <Input
              value={customTheme.name}
              onChange={(e) => setCustomTheme(t => ({ ...t, name: e.target.value }))}
              placeholder="My Custom Theme"
            />
          </div>

          {/* Color Pickers */}
          <div className="space-y-4">
            <Label>Colors</Label>
            <ColorPicker
              label="Background"
              value={customTheme.colors.background}
              onChange={(v) => updateColor('background', v)}
            />
            <ColorPicker
              label="Text (Foreground)"
              value={customTheme.colors.foreground}
              onChange={(v) => updateColor('foreground', v)}
            />
            <ColorPicker
              label="Primary / Accent"
              value={customTheme.colors.primary}
              onChange={(v) => updateColor('primary', v)}
            />
            <ColorPicker
              label="Border"
              value={customTheme.colors.border}
              onChange={(v) => updateColor('border', v)}
            />
            <ColorPicker
              label="Muted"
              value={customTheme.colors.muted}
              onChange={(v) => updateColor('muted', v)}
            />
          </div>

          {/* Contrast Warning */}
          {contrastWarning && (
            <Alert variant="warning">
              <AlertTriangle className="h-4 w-4" />
              <AlertDescription>{contrastWarning}</AlertDescription>
            </Alert>
          )}

          {/* Font Family */}
          <div className="space-y-2">
            <Label>Font Family</Label>
            <Select
              value={customTheme.typography.fontFamily}
              onValueChange={(v) => updateTypography('fontFamily', v)}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="system-ui">System UI</SelectItem>
                <SelectItem value="inter">Inter</SelectItem>
                <SelectItem value="roboto">Roboto</SelectItem>
                <SelectItem value="mono">Monospace</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Spacing Scale */}
          <div className="space-y-2">
            <Label>Spacing</Label>
            <div className="flex gap-2">
              {(['compact', 'normal', 'relaxed'] as const).map((scale) => (
                <Button
                  key={scale}
                  variant={customTheme.spacing.scale === scale ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => updateSpacing('scale', scale)}
                >
                  {scale.charAt(0).toUpperCase() + scale.slice(1)}
                </Button>
              ))}
            </div>
          </div>

          {/* Border Radius */}
          <div className="space-y-2">
            <Label>Border Radius: {customTheme.radii.base}</Label>
            <Slider
              value={[parseFloat(customTheme.radii.base)]}
              min={0}
              max={16}
              step={1}
              onValueChange={([v]) => updateRadii('base', `${v}px`)}
            />
            <div className="flex justify-between text-xs text-muted-foreground">
              <span>Flat</span>
              <span>Rounded</span>
            </div>
          </div>
        </div>

        {/* Right: Live preview */}
        <ThemePreview theme={customTheme} />
      </CardContent>

      <CardFooter className="flex justify-between">
        <Button variant="outline" onClick={resetToDefault}>
          Reset to Default
        </Button>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => exportTheme(customTheme)}>
            <Download className="mr-2 h-4 w-4" />
            Export
          </Button>
          <Button variant="outline" onClick={triggerImport}>
            <Upload className="mr-2 h-4 w-4" />
            Import
          </Button>
          <Button
            onClick={saveTheme}
            disabled={!!contrastWarning}
          >
            Save Theme
          </Button>
        </div>
      </CardFooter>
    </Card>
  );
}
```

### C. Theme Export/Import

```typescript
// Theme export - download as JSON
function exportTheme(theme: CustomTheme) {
  const blob = new Blob([JSON.stringify(theme, null, 2)], {
    type: 'application/json'
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${theme.name.toLowerCase().replace(/\s+/g, '-')}-theme.json`;
  a.click();
  URL.revokeObjectURL(url);
}

// Theme import - upload JSON file
function importTheme(file: File): Promise<CustomTheme> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const theme = JSON.parse(reader.result as string) as CustomTheme;
        // Validate theme structure
        if (validateTheme(theme)) {
          resolve(theme);
        } else {
          reject(new Error('Invalid theme structure'));
        }
      } catch {
        reject(new Error('Invalid JSON file'));
      }
    };
    reader.readAsText(file);
  });
}

// Apply custom theme dynamically
function applyCustomTheme(theme: CustomTheme) {
  const root = document.documentElement;
  Object.entries(theme.colors).forEach(([key, value]) => {
    // Convert camelCase to kebab-case for CSS variables
    const cssVar = `--${key.replace(/([A-Z])/g, '-$1').toLowerCase()}`;
    root.style.setProperty(cssVar, value);
  });
  root.style.setProperty('--radius', theme.radius);
}
```

### D. Theme Validation & Safety

When users import arbitrary JSON or create custom themes, validate to prevent issues:

```typescript
// themeValidation.ts

// Validate theme structure before applying
function validateTheme(theme: unknown): theme is CustomTheme {
  if (typeof theme !== 'object' || theme === null) return false;

  const t = theme as Record<string, unknown>;

  // Check required fields exist
  if (typeof t.name !== 'string') return false;
  if (typeof t.colors !== 'object' || t.colors === null) return false;

  // Validate colors are valid CSS values
  const colors = t.colors as Record<string, string>;
  const colorKeys = ['background', 'foreground', 'primary', 'border'];
  for (const key of colorKeys) {
    if (!isValidColor(colors[key])) return false;
  }

  return true;
}

// Check if string is a valid CSS color
function isValidColor(color: string): boolean {
  if (!color) return false;
  // Accept hex, rgb, hsl, or CSS color names
  const s = new Option().style;
  s.color = color;
  return s.color !== '';
}

// WCAG contrast ratio calculator
function getContrastRatio(bg: string, fg: string): number {
  const bgLum = getLuminance(bg);
  const fgLum = getLuminance(fg);
  const lighter = Math.max(bgLum, fgLum);
  const darker = Math.min(bgLum, fgLum);
  return (lighter + 0.05) / (darker + 0.05);
}

function getLuminance(color: string): number {
  // Convert color to RGB, then calculate relative luminance
  // Per WCAG 2.0 formula
  const rgb = parseColorToRGB(color);
  const [r, g, b] = rgb.map((c) => {
    c = c / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
```

#### ⚠️ Caveats & Best Practices

| Issue | Risk | Mitigation |
|-------|------|------------|
| Poor contrast | Text becomes unreadable | Add WCAG contrast checker, warn before save |
| Too much freedom | Users create unusable themes | Limit to safe tokens (bg, fg, accent, radius, spacing) |
| Invalid JSON import | App crashes or behaves oddly | Validate schema before applying |
| Malicious JSON | XSS or injection attacks | Sanitize all values, only allow CSS color formats |
| Theme breaks components | Styling inconsistencies | Test all themes against all components |
| Performance | Large theme objects slow down | Keep theme JSON small (~1KB), use CSS variables not inline styles |
| localStorage limits | Storage quota exceeded (5-10MB) | Theme JSON is small, but monitor if storing many themes |

```tsx
// Safe theme application with fallbacks
function applyThemeSafely(theme: CustomTheme) {
  const root = document.documentElement;

  // Default fallbacks for missing tokens
  const defaults: Partial<CustomTheme['colors']> = {
    background: '0 0% 100%',
    foreground: '222.2 84% 4.9%',
    primary: '222.2 47.4% 11.2%',
    border: '214.3 31.8% 91.4%',
  };

  // Apply colors with fallback
  Object.entries(theme.colors).forEach(([key, value]) => {
    const cssVar = `--${key.replace(/([A-Z])/g, '-$1').toLowerCase()}`;
    const safeValue = value || defaults[key as keyof typeof defaults] || '';
    root.style.setProperty(cssVar, safeValue);
  });
}
```

### E. Backend Persistence (Optional)

```java
// User preferences stored in database
// Add to User.java or create UserPreferences entity
private String themePreferences;  // JSON blob of custom theme
```

```
POST /api/auth/me/preferences
{ "theme": { ... custom theme object ... } }

GET /api/auth/me/preferences
Returns user's saved theme preferences
```

#### Storage Strategy

| User State | Storage Location | Persistence |
|------------|------------------|-------------|
| Anonymous | localStorage | Browser only |
| Logged in | Backend database | Cross-device |
| Logged in + offline | localStorage cache | Syncs on reconnect |

```typescript
// Hybrid storage strategy
async function saveTheme(theme: CustomTheme) {
  // Always save to localStorage for immediate access
  localStorage.setItem('contactAppTheme', JSON.stringify(theme));

  // If logged in, also persist to backend
  if (isAuthenticated()) {
    try {
      await api.post('/auth/me/preferences', { theme });
    } catch (error) {
      console.warn('Failed to sync theme to server', error);
      // Theme still saved locally, will sync later
    }
  }
}

// Load theme on app start
async function loadTheme(): Promise<CustomTheme | null> {
  // If logged in, prefer server version
  if (isAuthenticated()) {
    try {
      const { data } = await api.get('/auth/me/preferences');
      if (data.theme) {
        // Update local cache
        localStorage.setItem('contactAppTheme', JSON.stringify(data.theme));
        return data.theme;
      }
    } catch {
      // Fall through to localStorage
    }
  }

  // Fall back to localStorage
  const stored = localStorage.getItem('contactAppTheme');
  return stored ? JSON.parse(stored) : null;
}
```

### Updated Settings Page Structure

```tsx
// SettingsPage.tsx - expanded
<Tabs defaultValue="appearance">
  <TabsList>
    <TabsTrigger value="profile">Profile</TabsTrigger>
    <TabsTrigger value="appearance">Appearance</TabsTrigger>
    <TabsTrigger value="custom-theme">Custom Theme</TabsTrigger>
  </TabsList>

  <TabsContent value="appearance">
    {/* Existing: Dark mode toggle + preset themes */}
  </TabsContent>

  <TabsContent value="custom-theme">
    <ThemeEditor />
  </TabsContent>
</Tabs>
```

### Effort Estimate: 2-3 days

---

## Phase UI-3: Advanced Frontend Features

**Goal:** Professional SaaS-level interactions and functionality

### A. Command Palette (⌘K / Ctrl+K)

```bash
npm install cmdk
```

```tsx
// CommandPalette.tsx
import { Command } from 'cmdk';

function CommandPalette() {
  const [open, setOpen] = useState(false);

  // Toggle with keyboard shortcut
  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.key === 'k' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    document.addEventListener('keydown', down);
    return () => document.removeEventListener('keydown', down);
  }, []);

  return (
    <Command.Dialog open={open} onOpenChange={setOpen}>
      <Command.Input placeholder="Search everything..." />
      <Command.List>
        <Command.Group heading="Navigation">
          <Command.Item onSelect={() => navigate('/contacts')}>
            <Users className="mr-2 h-4 w-4" />
            Go to Contacts
          </Command.Item>
          <Command.Item onSelect={() => navigate('/tasks')}>
            <CheckSquare className="mr-2 h-4 w-4" />
            Go to Tasks
          </Command.Item>
        </Command.Group>
        <Command.Group heading="Actions">
          <Command.Item onSelect={createContact}>
            <Plus className="mr-2 h-4 w-4" />
            Create Contact
          </Command.Item>
          <Command.Item onSelect={createTask}>
            <Plus className="mr-2 h-4 w-4" />
            Create Task
          </Command.Item>
        </Command.Group>
        <Command.Group heading="Recent">
          {recentItems.map(item => (
            <Command.Item key={item.id}>
              {item.name}
            </Command.Item>
          ))}
        </Command.Group>
      </Command.List>
    </Command.Dialog>
  );
}
```

### B. Keyboard Shortcuts

```typescript
// useKeyboardShortcuts.ts
const shortcuts = {
  'g h': () => navigate('/'),           // Go home
  'g c': () => navigate('/contacts'),   // Go contacts
  'g t': () => navigate('/tasks'),      // Go tasks
  'g p': () => navigate('/projects'),   // Go projects
  'c': () => openCreateDialog(),        // Create new
  '?': () => openShortcutsHelp(),       // Show shortcuts
  'Escape': () => closeAllDialogs(),
};

// Shortcuts help modal
function ShortcutsHelpDialog() {
  return (
    <Dialog>
      <DialogContent>
        <DialogHeader>Keyboard Shortcuts</DialogHeader>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <h4 className="font-medium">Navigation</h4>
            <ul className="text-sm">
              <li><kbd>g</kbd> then <kbd>h</kbd> - Dashboard</li>
              <li><kbd>g</kbd> then <kbd>c</kbd> - Contacts</li>
              <li><kbd>g</kbd> then <kbd>t</kbd> - Tasks</li>
            </ul>
          </div>
          <div>
            <h4 className="font-medium">Actions</h4>
            <ul className="text-sm">
              <li><kbd>c</kbd> - Create new item</li>
              <li><kbd>⌘</kbd> <kbd>k</kbd> - Command palette</li>
              <li><kbd>?</kbd> - Show this help</li>
            </ul>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

### C. Animations with Framer Motion

```bash
npm install framer-motion
```

```tsx
// Animated page transitions
import { motion, AnimatePresence } from 'framer-motion';

const pageVariants = {
  initial: { opacity: 0, y: 20 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -20 }
};

function AnimatedPage({ children }: { children: React.ReactNode }) {
  return (
    <motion.div
      variants={pageVariants}
      initial="initial"
      animate="animate"
      exit="exit"
      transition={{ duration: 0.2 }}
    >
      {children}
    </motion.div>
  );
}

// Animated list items
const listItemVariants = {
  hidden: { opacity: 0, x: -20 },
  visible: (i: number) => ({
    opacity: 1,
    x: 0,
    transition: { delay: i * 0.05 }
  })
};

// Animated table rows
{tasks.map((task, i) => (
  <motion.tr
    key={task.id}
    custom={i}
    variants={listItemVariants}
    initial="hidden"
    animate="visible"
    className="hover:bg-muted/50"
  >
    {/* ... */}
  </motion.tr>
))}
```

### D. Dashboard Charts

Already have `--chart-*` CSS variables defined. Use them with a charting library:

```bash
npm install recharts
```

```tsx
// Dashboard enhancement with charts
import { LineChart, Line, XAxis, YAxis, Tooltip } from 'recharts';

function DashboardCharts() {
  const { data: stats } = useQuery(['dashboard-stats'], fetchDashboardStats);

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mt-6">
      {/* Tasks created over time */}
      <Card>
        <CardHeader>
          <CardTitle>Task Activity</CardTitle>
        </CardHeader>
        <CardContent>
          <LineChart data={stats?.taskActivity}>
            <XAxis dataKey="date" />
            <YAxis />
            <Tooltip />
            <Line
              type="monotone"
              dataKey="created"
              stroke="hsl(var(--chart-1))"
            />
            <Line
              type="monotone"
              dataKey="completed"
              stroke="hsl(var(--chart-2))"
            />
          </LineChart>
        </CardContent>
      </Card>

      {/* Task distribution pie chart */}
      <Card>
        <CardHeader>
          <CardTitle>Task Status</CardTitle>
        </CardHeader>
        <CardContent>
          <PieChart data={stats?.statusDistribution}>
            {/* ... */}
          </PieChart>
        </CardContent>
      </Card>
    </div>
  );
}
```

### E. Bulk Operations

```tsx
// Table with row selection
function ContactsTable() {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const toggleSelect = (id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const selectAll = () => {
    setSelectedIds(new Set(contacts.map(c => c.id)));
  };

  return (
    <>
      {/* Bulk action bar - appears when items selected */}
      {selectedIds.size > 0 && (
        <div className="flex items-center gap-4 p-4 bg-muted rounded-lg mb-4">
          <span className="text-sm font-medium">
            {selectedIds.size} selected
          </span>
          <Button variant="outline" size="sm" onClick={bulkDelete}>
            <Trash className="mr-2 h-4 w-4" />
            Delete
          </Button>
          <Button variant="outline" size="sm" onClick={bulkExport}>
            <Download className="mr-2 h-4 w-4" />
            Export CSV
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setSelectedIds(new Set())}>
            Clear selection
          </Button>
        </div>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className="w-12">
              <Checkbox
                checked={selectedIds.size === contacts.length}
                onCheckedChange={selectAll}
              />
            </TableHead>
            {/* ... other headers */}
          </TableRow>
        </TableHeader>
        <TableBody>
          {contacts.map(contact => (
            <TableRow key={contact.id}>
              <TableCell>
                <Checkbox
                  checked={selectedIds.has(contact.id)}
                  onCheckedChange={() => toggleSelect(contact.id)}
                />
              </TableCell>
              {/* ... other cells */}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  );
}
```

### F. CSV Export

```typescript
// Export utility
function exportToCSV<T extends Record<string, any>>(
  data: T[],
  filename: string,
  columns: { key: keyof T; header: string }[]
) {
  const headers = columns.map(c => c.header).join(',');
  const rows = data.map(item =>
    columns.map(c => {
      const value = item[c.key];
      // Escape commas and quotes
      const str = String(value ?? '');
      return str.includes(',') || str.includes('"')
        ? `"${str.replace(/"/g, '""')}"`
        : str;
    }).join(',')
  );

  const csv = [headers, ...rows].join('\n');
  const blob = new Blob([csv], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${filename}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

// Usage
exportToCSV(contacts, 'contacts-export', [
  { key: 'contactId', header: 'ID' },
  { key: 'firstName', header: 'First Name' },
  { key: 'lastName', header: 'Last Name' },
  { key: 'phone', header: 'Phone' },
  { key: 'address', header: 'Address' },
]);
```

### G. Calendar View for Appointments

```bash
npm install @fullcalendar/react @fullcalendar/daygrid @fullcalendar/interaction
```

```tsx
// CalendarView.tsx
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';

function CalendarView() {
  const { data: appointments } = useQuery(['appointments'], fetchAppointments);

  const events = appointments?.map(apt => ({
    id: apt.appointmentId,
    title: apt.description,
    date: apt.appointmentDate,
    className: apt.archived ? 'opacity-50' : ''
  }));

  return (
    <Card>
      <CardContent className="p-6">
        <FullCalendar
          plugins={[dayGridPlugin, interactionPlugin]}
          initialView="dayGridMonth"
          events={events}
          eventClick={(info) => openAppointmentDialog(info.event.id)}
          dateClick={(info) => openCreateDialog(info.dateStr)}
          headerToolbar={{
            left: 'prev,next today',
            center: 'title',
            right: 'dayGridMonth,dayGridWeek'
          }}
        />
      </CardContent>
    </Card>
  );
}
```

### H. PWA Support (Progressive Web App)

```typescript
// vite.config.ts
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'ContactApp',
        short_name: 'ContactApp',
        theme_color: '#0f172a',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png' }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg}']
      }
    })
  ]
});
```

### I. Responsive / Mobile-Friendly Layout

```tsx
// Ensure tables behave well on mobile
// Use responsive table wrapper
function ResponsiveTable({ children }: { children: React.ReactNode }) {
  return (
    <div className="overflow-x-auto -mx-4 sm:mx-0">
      <div className="inline-block min-w-full align-middle">
        {children}
      </div>
    </div>
  );
}

// Mobile-first card layout for small screens
function ContactsList({ contacts }: { contacts: Contact[] }) {
  const isMobile = useMediaQuery('(max-width: 640px)');

  if (isMobile) {
    return (
      <div className="space-y-4">
        {contacts.map(c => (
          <Card key={c.id} className="p-4">
            <div className="font-medium">{c.firstName} {c.lastName}</div>
            <div className="text-sm text-muted-foreground">{c.phone}</div>
            <div className="text-sm text-muted-foreground">{c.address}</div>
          </Card>
        ))}
      </div>
    );
  }

  return <ContactsTable contacts={contacts} />;
}
```

### J. Onboarding Tour (First-time Users)

```bash
npm install @reactour/tour
```

```tsx
// OnboardingTour.tsx
import { TourProvider, useTour } from '@reactour/tour';

const tourSteps = [
  {
    selector: '[data-tour="sidebar"]',
    content: 'Navigate between Contacts, Tasks, Projects, and Appointments here.',
  },
  {
    selector: '[data-tour="create-button"]',
    content: 'Click here to create a new item.',
  },
  {
    selector: '[data-tour="search"]',
    content: 'Search and filter your data using this search bar.',
  },
  {
    selector: '[data-tour="theme-toggle"]',
    content: 'Switch between light and dark mode here.',
  },
  {
    selector: '[data-tour="settings"]',
    content: 'Customize themes and preferences in Settings.',
  },
];

// Show on first login
function useShowOnboarding() {
  const { setIsOpen } = useTour();

  useEffect(() => {
    const hasSeenTour = localStorage.getItem('hasSeenOnboarding');
    if (!hasSeenTour) {
      setIsOpen(true);
      localStorage.setItem('hasSeenOnboarding', 'true');
    }
  }, []);
}
```

### K. Accessibility (a11y) Improvements

```tsx
// Ensure all interactive elements are keyboard accessible

// Skip link for keyboard users (already have SkipLink.tsx)
<SkipLink href="#main-content">Skip to main content</SkipLink>

// ARIA labels on icon-only buttons
<Button variant="ghost" size="icon" aria-label="Edit contact">
  <Pencil className="h-4 w-4" />
</Button>

// Focus visible rings (already in Tailwind)
className="focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"

// Announce dynamic content changes
import { useAnnounce } from '@/hooks/useAnnounce';
const announce = useAnnounce();
// After delete:
announce('Contact deleted successfully');

// Color contrast checker
// Ensure all text meets WCAG AA (4.5:1 for normal text, 3:1 for large)
```

### L. Performance Optimizations

```tsx
// 1. Code-splitting routes
const ContactsPage = React.lazy(() => import('@/pages/ContactsPage'));
const TasksPage = React.lazy(() => import('@/pages/TasksPage'));

<Suspense fallback={<PageSkeleton />}>
  <Routes>
    <Route path="/contacts" element={<ContactsPage />} />
    <Route path="/tasks" element={<TasksPage />} />
  </Routes>
</Suspense>

// 2. Virtualization for large lists
import { useVirtualizer } from '@tanstack/react-virtual';

function VirtualizedTable({ rows }: { rows: Contact[] }) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 48, // row height
  });

  return (
    <div ref={parentRef} className="h-[600px] overflow-auto">
      <div style={{ height: `${virtualizer.getTotalSize()}px` }}>
        {virtualizer.getVirtualItems().map((virtualRow) => (
          <TableRow key={virtualRow.key} data={rows[virtualRow.index]} />
        ))}
      </div>
    </div>
  );
}

// 3. Memoization for expensive renders
const MemoizedRow = React.memo(({ contact }: { contact: Contact }) => (
  <TableRow>...</TableRow>
));

// 4. Debounced search input
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
const [search, setSearch] = useState('');
const debouncedSearch = useDebouncedValue(search, 300);
// Only fetch when debouncedSearch changes
```

### Feature Summary

| Feature | Library | Effort | Impact |
|---------|---------|--------|--------|
| Command Palette | cmdk | Low | High |
| Keyboard Shortcuts | Custom hook | Low | Medium |
| Page Animations | framer-motion | Medium | High |
| Dashboard Charts | recharts | Medium | High |
| Bulk Operations | TanStack Table | Medium | High |
| CSV Export | Native JS | Low | Medium |
| Calendar View | FullCalendar | Medium | High |
| PWA Support | vite-plugin-pwa | Low | Medium |
| Responsive Layout | useMediaQuery | Low | High |
| Onboarding Tour | @reactour/tour | Low | Medium |
| Accessibility (a11y) | Native + ARIA | Low | High |
| Virtualized Lists | @tanstack/react-virtual | Medium | High |
| Code Splitting | React.lazy | Low | Medium |
| Debounced Search | Custom hook | Low | Medium |

### Effort Estimate: 2-3 weeks (all features)

---

## Phase 12: Reporting & Analytics

**Goal:** Data-driven insights for teams

### Charts & Visualizations

1. **Burndown Chart**
   - X: Days in sprint
   - Y: Remaining story points
   - Ideal line + actual line

2. **Burnup Chart**
   - X: Days in sprint
   - Y: Completed story points
   - Shows scope changes

3. **Velocity Chart**
   - X: Sprint number
   - Y: Completed story points
   - Running average line

4. **Cumulative Flow Diagram**
   - X: Time
   - Y: Task count by status
   - Stacked area chart

5. **Task Distribution**
   - Pie charts: By type, priority, assignee
   - Bar charts: Created vs completed over time

### Dashboard Components

```
ui/contact-app/src/
├── components/
│   └── charts/
│       ├── BurndownChart.tsx
│       ├── VelocityChart.tsx
│       ├── CumulativeFlowChart.tsx
│       └── DistributionChart.tsx
└── pages/
    └── ReportsPage.tsx
```

### API Endpoints

```
GET /api/v1/projects/{id}/reports/burndown?sprintId={sprintId}
GET /api/v1/projects/{id}/reports/velocity?sprints=5
GET /api/v1/projects/{id}/reports/cumulative-flow?days=30
GET /api/v1/projects/{id}/reports/distribution
GET /api/v1/projects/{id}/reports/export?format=csv
```

---

## Phase 13: Notifications & Real-time

**Goal:** Keep team members informed and synchronized

### Notification Types

| Event | Recipients |
|-------|------------|
| Task assigned to me | Assignee |
| Task I own was updated | Owner |
| Comment on my task | Owner + assignee |
| @mentioned in comment | Mentioned user |
| Task moved to my sprint | Assignee |
| Sprint started/completed | All team members |
| Task overdue | Assignee + owner |

### Implementation

```java
// Notification.java
public class Notification {
    private Long id;
    private UUID userId;              // Recipient - UUID per ADR-0052
    private NotificationType type;
    private String title;
    private String message;
    private String link;              // /tasks/T123
    private boolean read;
    private Instant createdAt;
}

// NotificationService.java
@Service
public class NotificationService {
    public void notify(UUID userId, NotificationType type, String message);  // UUID per ADR-0052
    public void notifyAssignee(Task task, String message);
    public void notifyMentioned(Comment comment);
    public List<Notification> getUnread(UUID userId);  // UUID per ADR-0052
    public void markAsRead(Long notificationId);
}
```

### WebSocket Real-time Updates

```java
// WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
}

// Message topics:
// /topic/project/{projectId}/tasks   - Task updates
// /topic/project/{projectId}/board   - Board changes
// /user/queue/notifications          - Personal notifications
```

### Email Notifications (Optional)

```yaml
# application.yml
notifications:
  email:
    enabled: true
    digest: daily  # or: immediate, hourly, weekly
    templates:
      task-assigned: templates/email/task-assigned.html
      comment-added: templates/email/comment-added.html
```

---

## Phase 14: Epic & Roadmap

**Goal:** High-level planning and dependencies

### New Domain Models

```java
// Epic is just a Task with type=EPIC
// Stories link to epic via parentTaskId

// Dependency.java
public class TaskDependency {
    private Long id;
    private String blockingTaskId;   // This task blocks...
    private String blockedTaskId;    // ...this task
    private DependencyType type;     // BLOCKS, RELATES_TO
}

public enum DependencyType {
    BLOCKS,      // Must complete before
    RELATES_TO   // Related but not blocking
}
```

### Database Migration

```sql
-- V20__create_task_dependencies.sql
CREATE TABLE task_dependencies (
    id BIGSERIAL PRIMARY KEY,
    blocking_task_id BIGINT NOT NULL,
    blocked_task_id BIGINT NOT NULL,
    dependency_type VARCHAR(20) NOT NULL DEFAULT 'BLOCKS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dep_blocking FOREIGN KEY (blocking_task_id)
        REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_dep_blocked FOREIGN KEY (blocked_task_id)
        REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT uq_task_dependency UNIQUE (blocking_task_id, blocked_task_id),
    CONSTRAINT chk_no_self_dep CHECK (blocking_task_id != blocked_task_id)
);
```

### Roadmap View

```
ui/contact-app/src/
├── components/
│   └── roadmap/
│       ├── RoadmapTimeline.tsx     # Gantt-style view
│       ├── EpicLane.tsx            # Epic row with child stories
│       ├── DependencyArrow.tsx     # SVG arrows between items
│       └── MilestoneMarker.tsx     # Key dates
└── pages/
    └── RoadmapPage.tsx
```

### API Endpoints

```
GET    /api/v1/projects/{id}/epics                    - List epics with stories
GET    /api/v1/projects/{id}/roadmap?months=6         - Timeline data
POST   /api/v1/tasks/{id}/dependencies                - Add dependency
DELETE /api/v1/tasks/{id}/dependencies/{depId}        - Remove dependency
GET    /api/v1/tasks/{id}/blocked-by                  - What blocks this?
GET    /api/v1/tasks/{id}/blocks                      - What does this block?
```

---

## Phase 15-17: Advanced Features (Future)

### Phase 15: Custom Workflows

- Define custom statuses per project
- Workflow transitions with permissions
- Automation rules (e.g., auto-assign on label)
- SLA tracking for issue resolution

### Phase 16: Integrations

- GitHub/GitLab: Link commits to tasks
- Slack: Post task updates to channels
- Webhooks: Custom integrations
- OAuth2: Public API access

### Phase 17: Teams & Permissions

- Team entity with member management
- Project-level role assignments
- Team workload distribution views
- Organization hierarchy

---

## Implementation Priority Matrix

| Phase | Business Value | Technical Effort | Priority | Timeline |
|-------|---------------|------------------|----------|----------|
| **UI-1: Frontend Polish** | HIGH | LOW | **P0** | 1-2 days |
| **UI-2: Theme System** | MEDIUM | MEDIUM | **P1** | 2-3 days |
| 8: Task Enhancements | HIGH | MEDIUM | **P1** | 1 week |
| 9: Sprint Management | HIGH | MEDIUM | **P1** | 1 week |
| 10: Activity & Comments | HIGH | MEDIUM | **P1** | 1 week |
| 11: Kanban Board | VERY HIGH | HIGH | **P1** | 2 weeks |
| **UI-3: Advanced Frontend** | HIGH | MEDIUM | **P2** | 1-2 weeks |
| 12: Reporting | MEDIUM | MEDIUM | **P2** | 1 week |
| 13: Notifications | MEDIUM | HIGH | **P2** | 2 weeks |
| 14: Epic & Roadmap | MEDIUM | HIGH | **P2** | 2 weeks |
| 15: Custom Workflows | MEDIUM | VERY HIGH | **P3** | 3+ weeks |
| 16: Integrations | HIGH | HIGH | **P3** | 2+ weeks |
| 17: Teams | MEDIUM | HIGH | **P3** | 2+ weeks |

### Recommended Implementation Order

```
Week 1:     UI-1 (Quick Wins) → Immediate visual impact
Week 2:     UI-2 (Theme System) → User customization
Week 3-4:   Phase 8 (Task Enhancements) → Core functionality
Week 5-6:   Phase 9 (Sprint Management) → Agile features
Week 7-8:   Phase 11 (Kanban Board) + UI-3 (Advanced Frontend)
```

---

## Quality Standards (Maintain Throughout)

### Each Phase Must Include

- [ ] ADR documenting design decisions
- [ ] Flyway migration scripts (no breaking changes)
- [ ] Domain validation in `Validation.java`
- [ ] Full test coverage (unit + integration)
- [ ] API documentation (OpenAPI)
- [ ] UI components with accessibility
- [ ] Security review (tenant isolation, auth checks)

### CI/CD Requirements

- [ ] All existing tests pass
- [ ] Mutation testing coverage maintained (>80%)
- [ ] No new CodeQL warnings
- [ ] ZAP scan passes
- [ ] API fuzzing validates new endpoints
- [ ] Docker build succeeds

### Documentation

- [ ] Update ROADMAP.md with completion status
- [ ] Update threat model if security-relevant
- [ ] Add design notes for complex features
- [ ] Update CHANGELOG.md

---

## Quick Reference: What Makes This Jira-Like?

| Jira Feature | Our Implementation | Phase |
|--------------|-------------------|-------|
| Issues | Task entity | ✅ Done |
| Projects | Project entity | ✅ Done |
| Assignees | Task.assigneeId | ✅ Done |
| Status workflow | TaskStatus enum | ✅ Done |
| Due dates | Task.dueDate | ✅ Done |
| Dark/Light mode | CSS variables + useTheme | ✅ Done |
| Theme presets | 5 color schemes | ✅ Done |
| **Skeleton loaders** | TanStack Query | **UI-1** |
| **Tooltips** | Radix Tooltip | **UI-1** |
| **Hover states** | CSS transitions | **UI-1** |
| **10 new themes** | CSS variables | **UI-2** |
| **Custom themes** | Theme editor | **UI-2** |
| **Theme export/import** | JSON blob | **UI-2** |
| Issue types | TaskType enum | Phase 8 |
| Priority | Priority enum | Phase 8 |
| Story points | Task.storyPoints | Phase 8 |
| Labels | Label entity | Phase 8 |
| Subtasks | Task.parentTaskId | Phase 8 |
| Sprints | Sprint entity | Phase 9 |
| Backlog | Tasks with no sprint | Phase 9 |
| Velocity | SprintMetrics | Phase 9 |
| Comments | Comment entity | Phase 10 |
| Activity log | ActivityEntry | Phase 10 |
| Kanban board | React DnD UI | Phase 11 |
| **Command palette** | cmdk library | **UI-3** |
| **Keyboard shortcuts** | Custom hooks | **UI-3** |
| **Calendar view** | FullCalendar | **UI-3** |
| **Bulk operations** | Row selection | **UI-3** |
| **CSV export** | Native JS | **UI-3** |
| **Page animations** | framer-motion | **UI-3** |
| **Responsive layout** | useMediaQuery | **UI-3** |
| **Onboarding tour** | @reactour/tour | **UI-3** |
| **Accessibility (a11y)** | ARIA + focus | **UI-3** |
| **Virtualized lists** | @tanstack/react-virtual | **UI-3** |
| **PWA support** | vite-plugin-pwa | **UI-3** |
| Burndown charts | Recharts | Phase 12 |
| Notifications | WebSocket + DB | Phase 13 |
| Epics | Task with type=EPIC | Phase 14 |
| Dependencies | TaskDependency | Phase 14 |
| Custom workflows | WorkflowDefinition | Phase 15 |
| Integrations | Webhook system | Phase 16 |
| Teams | Team entity | Phase 17 |

---

## Next Steps

1. **Review this roadmap** with stakeholders
2. **Prioritize Phase 8** (Task Enhancements) as first iteration
3. **Create ADR for Phase 8** design decisions (ADR-0050 is Domain Reconstitution Pattern, ADR-0051 is CLI Tool; use ADR-0052+)
4. **Estimate effort** for each phase
5. **Begin implementation** following established patterns

---

*This roadmap builds on the solid foundation of 54 ADRs, 1107 tests, production-grade auth (ADR-0052), and enterprise CI/CD. UI phases (UI-1 through UI-3) were added 2025-12-03 based on screenshot analysis and best practices research.*
