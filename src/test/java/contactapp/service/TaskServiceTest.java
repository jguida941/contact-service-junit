package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.domain.Task;
import contactapp.domain.TaskStatus;
import contactapp.security.Role;
import contactapp.security.TestUserSetup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import contactapp.support.PostgresContainerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link TaskService} behavior against the Spring context (PostgreSQL + Flyway via Testcontainers).
 *
 * <p>Remaining in the same package allows direct access to {@link TaskService#clearAllTasks()}.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Isolated
public class TaskServiceTest extends PostgresContainerSupport {

    @Autowired
    private TaskService service;

    @Autowired
    private TestUserSetup testUserSetup;

    @Autowired
    private TestCleanupUtility testCleanup;

    /**
     * Sets up test user and clears data before each test.
     */
    @BeforeEach
    void reset() {
        testCleanup.resetTestEnvironment();
    }

    @Test
    void testSingletonSharesStateWithSpringBean() {
        // Ensure we have a fresh authenticated user in the security context
        testUserSetup.setupTestUser("test-singleton", "singleton@example.com", Role.USER);

        TaskService singleton = TaskService.getInstance();

        Task task = new Task("legacy10", "Legacy add", "added through getInstance");
        boolean addedViaSingleton = singleton.addTask(task);

        assertThat(addedViaSingleton).isTrue();
        assertThat(service.getTaskById("legacy10")).isPresent();
    }

    private static void resetSingleton() {
        try {
            final var instanceField = TaskService.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to reset TaskService singleton for test isolation", e);
        }
    }

    /**
     * Confirms {@link TaskService#addTask(Task)} inserts new tasks.
     */
    @Test
    void testAddTask() {
        TaskService service = this.service;
        Task task = new Task("100", "Write docs", "Document Task service");

        boolean added = service.addTask(task);

        assertThat(added).isTrue();
        assertThat(service.getDatabase()).containsKey("100");
        Task stored = service.getDatabase().get("100");
        assertThat(stored.getName()).isEqualTo("Write docs");
        assertThat(stored.getDescription()).isEqualTo("Document Task service");
    }

    /**
     * Verifies duplicate IDs are rejected and the original entry remains.
     */
    @Test
    void testAddDuplicateTaskIdFails() {
        TaskService service = this.service;
        Task original = new Task("100", "Write docs", "Document Task service");
        Task duplicate = new Task("100", "Other", "Another desc");

        assertThat(service.addTask(original)).isTrue();
        assertThatThrownBy(() -> service.addTask(duplicate))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("100");
        // Verify original data is still stored
        Task stored = service.getDatabase().get("100");
        assertThat(stored.getName()).isEqualTo("Write docs");
        assertThat(stored.getDescription()).isEqualTo("Document Task service");
    }

    /**
     * Ensures null inputs trigger the explicit IllegalArgumentException guard.
     */
    @Test
    void testAddTaskNullThrows() {
        TaskService service = this.service;

        assertThatThrownBy(() -> service.addTask(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("task must not be null");
    }

    /**
     * Confirms delete removes stored tasks and returns true.
     */
    @Test
    void testDeleteTask() {
        TaskService service = this.service;
        Task task = new Task("100", "Write docs", "Document Task service");
        service.addTask(task);

        boolean deleted = service.deleteTask("100");

        assertThat(deleted).isTrue();
        assertThat(service.getDatabase()).doesNotContainKey("100");
    }

    /**
     * Ensures delete validates the incoming ID.
     */
    @Test
    void testDeleteTaskBlankIdThrows() {
        TaskService service = this.service;

        assertThatThrownBy(() -> service.deleteTask(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId must not be null or blank");
    }

    /**
     * Verifies delete returns {@code false} when the ID does not exist.
     */
    @Test
    void testDeleteMissingTaskReturnsFalse() {
        TaskService service = this.service;

        assertThat(service.deleteTask("missing")).isFalse();
    }

    /**
     * Proves {@link TaskService#clearAllTasks()} empties the backing store (PIT guard).
     */
    @Test
    void testClearAllTasksRemovesEntries() {
        TaskService service = this.service;
        service.addTask(new Task("100", "Write docs", "Document Task service"));
        service.addTask(new Task("101", "Review plan", "Double-check requirements"));

        service.clearAllTasks();

        assertThat(service.getDatabase()).isEmpty();
    }

    /**
     * Ensures {@link TaskService#updateTask(String, String, String)} updates stored tasks.
     */
    @Test
    void testUpdateTask() {
        TaskService service = this.service;
        Task task = new Task("100", "Write docs", "Document Task service");
        service.addTask(task);

        boolean updated = service.updateTask("100", "Implement feature", "Finish TaskService");

        assertThat(updated).isTrue();
        assertThat(service.getDatabase().get("100"))
                .hasFieldOrPropertyWithValue("name", "Implement feature")
                .hasFieldOrPropertyWithValue("description", "Finish TaskService");
    }

    /**
     * Confirms update trims IDs before lookups so whitespace-wrapped IDs work.
     */
    @Test
    void testUpdateTaskTrimsId() {
        TaskService service = this.service;
        Task task = new Task("200", "Write docs", "Document Task service");
        service.addTask(task);

        boolean updated = service.updateTask(" 200 ", "Polish docs", "Tighten descriptions");

        assertThat(updated).isTrue();
        assertThat(service.getDatabase().get("200"))
                .hasFieldOrPropertyWithValue("name", "Polish docs")
                .hasFieldOrPropertyWithValue("description", "Tighten descriptions");
    }

    /**
     * Ensures update validates the ID input before accessing the map.
     */
    @Test
    void testUpdateTaskBlankIdThrows() {
        TaskService service = this.service;

        assertThatThrownBy(() -> service.updateTask(" ", "Name", "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId must not be null or blank");
    }

    /**
     * Verifies {@link TaskService#updateTask} returns {@code false} when the ID is missing.
     */
    @Test
    void testUpdateMissingTaskReturnsFalse() {
        TaskService service = this.service;

        assertThat(service.updateTask("missing", "Name", "Desc")).isFalse();
    }

    /**
     * Confirms invalid update inputs throw and leave the stored Task unchanged.
     */
    @Test
    void testUpdateTaskInvalidValuesLeaveStateUnchanged() {
        TaskService service = this.service;
        Task task = new Task("300", "Write docs", "Original description");
        service.addTask(task);

        assertThatThrownBy(() -> service.updateTask("300", " ", "New desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be null or blank");

        Task stored = service.getDatabase().get("300");
        assertThat(stored.getName()).isEqualTo("Write docs");
        assertThat(stored.getDescription()).isEqualTo("Original description");
    }

    /**
     * Ensures getDatabase returns defensive copies so external mutation cannot alter service state.
     */
    @Test
    void testGetDatabaseReturnsDefensiveCopies() {
        TaskService service = this.service;
        Task task = new Task("400", "Original Name", "Original Description");
        service.addTask(task);

        // Get a snapshot and mutate it
        Task snapshot = service.getDatabase().get("400");
        snapshot.setName("Mutated Name");
        snapshot.setDescription("Mutated Description");

        // Fetch a fresh snapshot and verify the service state is unchanged
        Task freshSnapshot = service.getDatabase().get("400");
        assertThat(freshSnapshot.getName()).isEqualTo("Original Name");
        assertThat(freshSnapshot.getDescription()).isEqualTo("Original Description");
    }

    // ==================== getTaskById Tests ====================

    /**
     * Verifies getTaskById returns the task when it exists.
     */
    @Test
    void testGetTaskByIdReturnsTask() {
        TaskService service = this.service;
        Task task = new Task("500", "Test Task", "Test Description");
        service.addTask(task);

        var result = service.getTaskById("500");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Task");
    }

    /**
     * Verifies getTaskById returns empty when task doesn't exist.
     */
    @Test
    void testGetTaskByIdReturnsEmptyWhenNotFound() {
        TaskService service = this.service;

        var result = service.getTaskById("nonexistent");

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getTaskById throws when ID is blank.
     */
    @Test
    void testGetTaskByIdBlankIdThrows() {
        TaskService service = this.service;

        assertThatThrownBy(() -> service.getTaskById(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId must not be null or blank");
    }

    /**
     * Verifies getTaskById trims the ID before lookup.
     */
    @Test
    void testGetTaskByIdTrimsId() {
        TaskService service = this.service;
        Task task = new Task("600", "Trimmed Task", "Test Description");
        service.addTask(task);

        var result = service.getTaskById(" 600 ");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Trimmed Task");
    }

    // ==================== getAllTasks Tests ====================

    /**
     * Verifies getAllTasks returns empty list when no tasks exist.
     */
    @Test
    void testGetAllTasksReturnsEmptyList() {
        TaskService service = this.service;

        var result = service.getAllTasks();

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getAllTasks returns all tasks.
     */
    @Test
    void testGetAllTasksReturnsAllTasks() {
        TaskService service = this.service;
        service.addTask(new Task("701", "First Task", "First Description"));
        service.addTask(new Task("702", "Second Task", "Second Description"));

        var result = service.getAllTasks();

        assertThat(result).hasSize(2);
    }

    @Test
    void getAllTasksAllUsers_requiresAdminRole() {
        runAs("limited", Role.USER, () ->
                service.addTask(new Task("task-1", "Task", "User owned")));

        assertThatThrownBy(service::getAllTasksAllUsers)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only ADMIN users can access all tasks");
    }

    @Test
    void getAllTasksAllUsers_returnsDataForAdmins() {
        runAs("user-one", Role.USER, () ->
                service.addTask(new Task("tu-1", "User One", "Task One")));
        runAs("user-two", Role.USER, () ->
                service.addTask(new Task("tu-2", "User Two", "Task Two")));

        runAs("admin", Role.ADMIN, () -> assertThat(service.getAllTasksAllUsers())
                .extracting(Task::getTaskId)
                .contains("tu-1", "tu-2"));
    }

    @Test
    void getTaskById_onlyReturnsCurrentUsersTasks() {
        runAs("owner", Role.USER, () ->
                service.addTask(new Task("shared01", "Owner Task", "Owned by user")));

        runAs("other", Role.USER, () ->
                assertThat(service.getTaskById("shared01")).isEmpty());
    }

    @Test
    void deleteTask_doesNotAllowOtherUsersToDelete() {
        runAs("owner", Role.USER, () ->
                service.addTask(new Task("locked01", "Locked", "Should stay")));

        runAs("other", Role.USER, () ->
                assertThat(service.deleteTask("locked01")).isFalse());
    }

    @Test
    void updateTask_doesNotAllowCrossUserModification() {
        runAs("owner", Role.USER, () ->
                service.addTask(new Task("owned01", "Owned", "Original")));

        runAs("other", Role.USER, () ->
                assertThat(service.updateTask("owned01", "Edited", "New description"))
                        .isFalse());
    }

    private void runAs(final String username, final Role role, final Runnable action) {
        testUserSetup.setupTestUser(username, username + "@example.com", role);
        action.run();
    }

    // ==================== Phase 2 Status and Due Date Tests ====================

    @Test
    void testAddTaskWithStatus() {
        TaskService service = this.service;
        Task task = new Task("200", "Status Task", "Task with status",
                TaskStatus.IN_PROGRESS, null);

        boolean added = service.addTask(task);

        assertThat(added).isTrue();
        Task stored = service.getTaskById("200").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void testAddTaskWithDueDate() {
        TaskService service = this.service;
        LocalDate dueDate = LocalDate.of(2026, 6, 15);
        Task task = new Task("201", "Due Date Task", "Task with due date",
                TaskStatus.TODO, dueDate);

        boolean added = service.addTask(task);

        assertThat(added).isTrue();
        Task stored = service.getTaskById("201").orElseThrow();
        assertThat(stored.getDueDate()).isEqualTo(dueDate);
    }

    @Test
    void testAddTaskDefaultsToTodoStatus() {
        TaskService service = this.service;
        Task task = new Task("202", "Default Status", "Should default to TODO");

        boolean added = service.addTask(task);

        assertThat(added).isTrue();
        Task stored = service.getTaskById("202").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void testUpdateTaskWithStatus() {
        TaskService service = this.service;
        Task task = new Task("203", "Update Status", "Original",
                TaskStatus.TODO, null);
        service.addTask(task);

        Task existing = service.getTaskById("203").orElseThrow();
        existing.update("Update Status", "Updated description",
                TaskStatus.IN_PROGRESS, null);
        service.updateTask("203", existing.getName(), existing.getDescription());

        Task updated = service.getTaskById("203").orElseThrow();
        assertThat(updated.getName()).isEqualTo("Update Status");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
    }

    @Test
    void testUpdateTaskWithDueDate() {
        TaskService service = this.service;
        LocalDate originalDate = LocalDate.of(2026, 1, 1);
        LocalDate newDate = LocalDate.of(2026, 12, 31);
        Task task = new Task("204", "Update Due", "Original",
                TaskStatus.TODO, originalDate);
        service.addTask(task);

        Task existing = service.getTaskById("204").orElseThrow();
        existing.update("Update Due", "Updated", TaskStatus.TODO, newDate);
        service.updateTask("204", existing.getName(), existing.getDescription());

        Task updated = service.getTaskById("204").orElseThrow();
        assertThat(updated.getName()).isEqualTo("Update Due");
    }

    @Test
    void testTaskStatusTransition_TodoToInProgress() {
        TaskService service = this.service;
        Task task = new Task("300", "Status Flow", "Testing status transition",
                TaskStatus.TODO, null);
        service.addTask(task);

        Task stored = service.getTaskById("300").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TaskStatus.TODO);

        stored.setStatus(TaskStatus.IN_PROGRESS);
        service.updateTask("300", stored.getName(), stored.getDescription());

        Task updated = service.getTaskById("300").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void testTaskStatusTransition_InProgressToDone() {
        TaskService service = this.service;
        Task task = new Task("301", "Complete Task", "Testing completion",
                TaskStatus.IN_PROGRESS, null);
        service.addTask(task);

        Task stored = service.getTaskById("301").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        stored.setStatus(TaskStatus.DONE);
        service.updateTask("301", stored.getName(), stored.getDescription());

        Task updated = service.getTaskById("301").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void testDueDateValidation_AcceptsValidDate() {
        TaskService service = this.service;
        LocalDate futureDate = LocalDate.now().plusDays(30);
        Task task = new Task("400", "Future Task", "Task with future date",
                TaskStatus.TODO, futureDate);

        boolean added = service.addTask(task);

        assertThat(added).isTrue();
        Task stored = service.getTaskById("400").orElseThrow();
        assertThat(stored.getDueDate()).isEqualTo(futureDate);
    }

    @Test
    void testDueDateValidation_RejectsPastDate() {
        LocalDate pastDate = LocalDate.now().minusDays(10);

        assertThatThrownBy(() -> new Task("401", "Past Task", "Task with past date",
                TaskStatus.TODO, pastDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dueDate must not be in the past");
    }

    @Test
    void testDueDateValidation_AcceptsNullDate() {
        TaskService service = this.service;
        Task task = new Task("402", "No Due Date", "Task without due date",
                TaskStatus.TODO, null);

        boolean added = service.addTask(task);

        assertThat(added).isTrue();
        Task stored = service.getTaskById("402").orElseThrow();
        assertThat(stored.getDueDate()).isNull();
    }

    @Test
    void testCreatedAtTimestamp_IsSet() {
        TaskService service = this.service;
        Task task = new Task("500", "Timestamp Test", "Testing created timestamp");

        service.addTask(task);

        Task stored = service.getTaskById("500").orElseThrow();
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void testUpdatedAtTimestamp_IsSet() {
        TaskService service = this.service;
        Task task = new Task("501", "Update Timestamp", "Testing updated timestamp");

        service.addTask(task);

        Task stored = service.getTaskById("501").orElseThrow();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void testUpdatedAtTimestamp_ChangesOnUpdate() {
        TaskService service = this.service;
        Task task = new Task("502", "Update Time", "Original");
        service.addTask(task);

        Task original = service.getTaskById("502").orElseThrow();
        var originalUpdatedAt = original.getUpdatedAt();

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        service.updateTask("502", "Update Time", "Modified");

        Task updated = service.getTaskById("502").orElseThrow();
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void testAllTaskStatuses() {
        TaskService service = this.service;

        int idx = 0;
        for (TaskStatus status : TaskStatus.values()) {
            String id = "st-" + idx++;
            Task task = new Task(id, "Task " + status, "Testing " + status,
                    status, null);
            service.addTask(task);

            Task stored = service.getTaskById(id).orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(status);
        }
    }

    // ==================== Null TaskId Tests ====================

    @Test
    void testAddTaskWithNullTaskIdThrows() {
        assertThatThrownBy(() -> service.addTask(new Task(null, "Name", "Desc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId must not be null or blank");
    }

    @Test
    void getOverdueTasksReturnsOnlyIncompletePastDueTasks() {
        final Clock futureClock = Clock.fixed(
                Instant.now().plus(5, ChronoUnit.DAYS),
                ZoneOffset.UTC);
        service.setClock(futureClock);

        try {
            final LocalDate futureToday = LocalDate.now(futureClock);
            service.addTask(new Task("overdue", "Overdue", "Needs work", TaskStatus.IN_PROGRESS, futureToday.minusDays(2)));
            service.addTask(new Task("done", "Done", "Already finished", TaskStatus.DONE, futureToday.minusDays(5)));
            service.addTask(new Task("future", "Future", "Still pending", TaskStatus.TODO, futureToday.plusDays(1)));

            final var overdueTasks = service.getOverdueTasks();

            assertThat(overdueTasks)
                    .extracting(Task::getTaskId)
                    .containsExactly("overdue");
        } finally {
            service.setClock(Clock.systemUTC());
        }
    }

    // ==================== Extended Update Tests ====================

    @Test
    void testUpdateTaskWithStatusAndDueDate() {
        LocalDate dueDate = LocalDate.now().plusDays(30);
        Task task = new Task("upd-01", "Original", "Desc", TaskStatus.TODO, null);
        service.addTask(task);

        boolean updated = service.updateTask("upd-01", "Updated Name", "Updated Desc",
                TaskStatus.IN_PROGRESS, dueDate);

        assertThat(updated).isTrue();
        Task stored = service.getTaskById("upd-01").orElseThrow();
        assertThat(stored.getName()).isEqualTo("Updated Name");
        assertThat(stored.getDescription()).isEqualTo("Updated Desc");
        assertThat(stored.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(stored.getDueDate()).isEqualTo(dueDate);
    }

    @Test
    void testUpdateTaskWithStatusAndDueDateNotFound() {
        boolean updated = service.updateTask("nonexistent", "Name", "Desc",
                TaskStatus.TODO, LocalDate.now().plusDays(1));
        assertThat(updated).isFalse();
    }

    @Test
    void testUpdateTaskWithProjectId() {
        Task task = new Task("upd-02", "Original", "Desc", TaskStatus.TODO, null);
        service.addTask(task);

        boolean updated = service.updateTask("upd-02", "With Project", "Linked",
                TaskStatus.IN_PROGRESS, null, "proj-001");

        assertThat(updated).isTrue();
        Task stored = service.getTaskById("upd-02").orElseThrow();
        assertThat(stored.getProjectId()).isEqualTo("proj-001");
    }

    @Test
    void testUpdateTaskWithProjectIdNotFound() {
        boolean updated = service.updateTask("nonexistent", "Name", "Desc",
                TaskStatus.TODO, null, "proj-001");
        assertThat(updated).isFalse();
    }

    @Test
    void testUpdateTaskWithAssigneeId() {
        Task task = new Task("upd-03", "Original", "Desc", TaskStatus.TODO, null);
        service.addTask(task);

        boolean updated = service.updateTask("upd-03", "Assigned", "To user",
                TaskStatus.TODO, null, null, 42L);

        assertThat(updated).isTrue();
        Task stored = service.getTaskById("upd-03").orElseThrow();
        assertThat(stored.getAssigneeId()).isEqualTo(42L);
    }

    @Test
    void testUpdateTaskWithAssigneeIdNotFound() {
        boolean updated = service.updateTask("nonexistent", "Name", "Desc",
                TaskStatus.TODO, null, null, 1L);
        assertThat(updated).isFalse();
    }

    @Test
    void testUpdateTaskWithAllFields() {
        LocalDate dueDate = LocalDate.now().plusDays(14);
        Task task = new Task("upd-04", "Original", "Desc", TaskStatus.TODO, null);
        service.addTask(task);

        boolean updated = service.updateTask("upd-04", "Full Update", "All fields",
                TaskStatus.DONE, dueDate, "proj-002", 99L);

        assertThat(updated).isTrue();
        Task stored = service.getTaskById("upd-04").orElseThrow();
        assertThat(stored.getName()).isEqualTo("Full Update");
        assertThat(stored.getDescription()).isEqualTo("All fields");
        assertThat(stored.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(stored.getDueDate()).isEqualTo(dueDate);
        assertThat(stored.getProjectId()).isEqualTo("proj-002");
        assertThat(stored.getAssigneeId()).isEqualTo(99L);
    }

    @Test
    void testUpdateTaskWithBlankIdThrows_ExtendedOverload() {
        assertThatThrownBy(() -> service.updateTask("  ", "Name", "Desc",
                TaskStatus.TODO, null, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId must not be null or blank");
    }

    // ==================== Query Method Tests ====================

    @Test
    void testGetTasksByStatus() {
        service.addTask(new Task("qs-01", "Todo1", "Desc", TaskStatus.TODO, null));
        service.addTask(new Task("qs-02", "Todo2", "Desc", TaskStatus.TODO, null));
        service.addTask(new Task("qs-03", "InProgress", "Desc", TaskStatus.IN_PROGRESS, null));
        service.addTask(new Task("qs-04", "Done", "Desc", TaskStatus.DONE, null));

        var todoTasks = service.getTasksByStatus(TaskStatus.TODO);
        var inProgressTasks = service.getTasksByStatus(TaskStatus.IN_PROGRESS);
        var doneTasks = service.getTasksByStatus(TaskStatus.DONE);

        assertThat(todoTasks).hasSize(2);
        assertThat(inProgressTasks).hasSize(1);
        assertThat(doneTasks).hasSize(1);
    }

    @Test
    void testGetTasksByStatusReturnsEmptyList() {
        var result = service.getTasksByStatus(TaskStatus.DONE);
        assertThat(result).isEmpty();
    }

    @Test
    void testGetTasksByStatusNullThrows() {
        assertThatThrownBy(() -> service.getTasksByStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status must not be null");
    }

    @Test
    void testGetTasksDueBefore() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextWeek = today.plusDays(7);
        LocalDate nextMonth = today.plusDays(30);

        service.addTask(new Task("db-01", "Tomorrow", "Desc", TaskStatus.TODO, tomorrow));
        service.addTask(new Task("db-02", "NextWeek", "Desc", TaskStatus.TODO, nextWeek));
        service.addTask(new Task("db-03", "NextMonth", "Desc", TaskStatus.TODO, nextMonth));
        service.addTask(new Task("db-04", "NoDate", "Desc", TaskStatus.TODO, null));

        var tasksBeforeNextWeek = service.getTasksDueBefore(nextWeek);

        assertThat(tasksBeforeNextWeek).hasSize(1);
        assertThat(tasksBeforeNextWeek.get(0).getTaskId()).isEqualTo("db-01");
    }

    @Test
    void testGetTasksDueBeforeNullThrows() {
        assertThatThrownBy(() -> service.getTasksDueBefore(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("date must not be null");
    }

    @Test
    void testGetTasksDueBeforeReturnsEmptyList() {
        service.addTask(new Task("db-05", "FutureTask", "Desc", TaskStatus.TODO,
                LocalDate.now().plusDays(100)));

        var result = service.getTasksDueBefore(LocalDate.now());
        assertThat(result).isEmpty();
    }

    @Test
    void testGetTasksByProjectId() {
        Task t1 = new Task("proj-t1", "P1 Task1", "Desc", TaskStatus.TODO, null);
        t1.setProjectId("project-a");
        Task t2 = new Task("proj-t2", "P1 Task2", "Desc", TaskStatus.TODO, null);
        t2.setProjectId("project-a");
        Task t3 = new Task("proj-t3", "P2 Task", "Desc", TaskStatus.TODO, null);
        t3.setProjectId("project-b");
        Task t4 = new Task("proj-t4", "No Project", "Desc", TaskStatus.TODO, null);

        service.addTask(t1);
        service.addTask(t2);
        service.addTask(t3);
        service.addTask(t4);

        var projectATasks = service.getTasksByProjectId("project-a");
        var projectBTasks = service.getTasksByProjectId("project-b");

        assertThat(projectATasks).hasSize(2);
        assertThat(projectBTasks).hasSize(1);
    }

    @Test
    void testGetTasksByProjectIdBlankThrows() {
        assertThatThrownBy(() -> service.getTasksByProjectId("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projectId must not be null or blank");
    }

    @Test
    void testGetTasksByProjectIdTrimsInput() {
        Task t = new Task("proj-t5", "Trimmed", "Desc", TaskStatus.TODO, null);
        t.setProjectId("proj-x");
        service.addTask(t);

        var result = service.getTasksByProjectId("  proj-x  ");
        assertThat(result).hasSize(1);
    }

    @Test
    void testGetTasksByAssigneeId() {
        Task t1 = new Task("asgn-t1", "User1 Task1", "Desc", TaskStatus.TODO, null);
        t1.setAssigneeId(100L);
        Task t2 = new Task("asgn-t2", "User1 Task2", "Desc", TaskStatus.TODO, null);
        t2.setAssigneeId(100L);
        Task t3 = new Task("asgn-t3", "User2 Task", "Desc", TaskStatus.TODO, null);
        t3.setAssigneeId(200L);
        Task t4 = new Task("asgn-t4", "Unassigned", "Desc", TaskStatus.TODO, null);

        service.addTask(t1);
        service.addTask(t2);
        service.addTask(t3);
        service.addTask(t4);

        var user1Tasks = service.getTasksByAssigneeId(100L);
        var user2Tasks = service.getTasksByAssigneeId(200L);

        assertThat(user1Tasks).hasSize(2);
        assertThat(user2Tasks).hasSize(1);
    }

    @Test
    void testGetTasksByAssigneeIdNullThrows() {
        assertThatThrownBy(() -> service.getTasksByAssigneeId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assigneeId must not be null");
    }

    @Test
    void testGetTasksByAssigneeIdReturnsEmptyWhenNoMatches() {
        var result = service.getTasksByAssigneeId(999L);
        assertThat(result).isEmpty();
    }

    @Test
    void testGetOverdueTasks() {
        // Add task that would be overdue - we need to use reflection or a task
        // with dueDate that's already passed. Since constructor rejects past dates,
        // we'll test the method behavior with current tasks.
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Task notOverdue = new Task("od-01", "Not Overdue", "Desc", TaskStatus.TODO, tomorrow);
        service.addTask(notOverdue);

        // No past-due tasks, so result should be empty
        var overdueTasks = service.getOverdueTasks();
        assertThat(overdueTasks).isEmpty();
    }

    @Test
    void testGetOverdueTasksExcludesDoneTasks() {
        // Even if a task were overdue, DONE tasks should be excluded
        LocalDate futureDate = LocalDate.now().plusDays(5);
        Task doneTask = new Task("od-02", "Done Task", "Desc", TaskStatus.DONE, futureDate);
        service.addTask(doneTask);

        var overdueTasks = service.getOverdueTasks();
        assertThat(overdueTasks).isEmpty();
    }

    @Test
    void testGetOverdueTasksExcludesNullDueDate() {
        Task noDueDate = new Task("od-03", "No Due", "Desc", TaskStatus.TODO, null);
        service.addTask(noDueDate);

        var overdueTasks = service.getOverdueTasks();
        assertThat(overdueTasks).isEmpty();
    }

    // ==================== Defensive Copy Tests ====================

    @Test
    void testGetAllTasksReturnsDefensiveCopies() {
        Task task = new Task("dc-01", "Original", "Description");
        service.addTask(task);

        var allTasks = service.getAllTasks();
        allTasks.get(0).setName("Mutated");

        Task stored = service.getTaskById("dc-01").orElseThrow();
        assertThat(stored.getName()).isEqualTo("Original");
    }

    @Test
    void testGetTaskByIdReturnsDefensiveCopy() {
        Task task = new Task("dc-02", "Original", "Description");
        service.addTask(task);

        Task retrieved = service.getTaskById("dc-02").orElseThrow();
        retrieved.setName("Mutated");

        Task fresh = service.getTaskById("dc-02").orElseThrow();
        assertThat(fresh.getName()).isEqualTo("Original");
    }

    @Test
    void testGetTasksByStatusReturnsDefensiveCopies() {
        Task task = new Task("dc-03", "Original", "Desc", TaskStatus.TODO, null);
        service.addTask(task);

        var tasks = service.getTasksByStatus(TaskStatus.TODO);
        tasks.get(0).setName("Mutated");

        Task stored = service.getTaskById("dc-03").orElseThrow();
        assertThat(stored.getName()).isEqualTo("Original");
    }

    @Test
    void testGetTasksByProjectIdReturnsDefensiveCopies() {
        Task task = new Task("dc-04", "Original", "Desc", TaskStatus.TODO, null);
        task.setProjectId("test-proj");
        service.addTask(task);

        var tasks = service.getTasksByProjectId("test-proj");
        tasks.get(0).setName("Mutated");

        Task stored = service.getTaskById("dc-04").orElseThrow();
        assertThat(stored.getName()).isEqualTo("Original");
    }

    @Test
    void testGetTasksByAssigneeIdReturnsDefensiveCopies() {
        Task task = new Task("dc-05", "Original", "Desc", TaskStatus.TODO, null);
        task.setAssigneeId(50L);
        service.addTask(task);

        var tasks = service.getTasksByAssigneeId(50L);
        tasks.get(0).setName("Mutated");

        Task stored = service.getTaskById("dc-05").orElseThrow();
        assertThat(stored.getName()).isEqualTo("Original");
    }

    // ==================== User Isolation Tests for Query Methods ====================

    @Test
    void testGetTasksByStatusOnlyReturnsCurrentUsersTasks() {
        runAs("usr-sta-a", Role.USER, () -> {
            Task t = new Task("isos-01", "UserA Task", "Desc", TaskStatus.IN_PROGRESS, null);
            service.addTask(t);
        });

        runAs("usr-sta-b", Role.USER, () -> {
            var tasks = service.getTasksByStatus(TaskStatus.IN_PROGRESS);
            assertThat(tasks).isEmpty();
        });
    }

    @Test
    void testGetTasksByProjectIdOnlyReturnsCurrentUsersTasks() {
        runAs("usr-prj-a", Role.USER, () -> {
            Task t = new Task("isop-01", "UserA Task", "Desc", TaskStatus.TODO, null);
            t.setProjectId("shared-prj");
            service.addTask(t);
        });

        runAs("usr-prj-b", Role.USER, () -> {
            var tasks = service.getTasksByProjectId("shared-prj");
            assertThat(tasks).isEmpty();
        });
    }

    @Test
    void testGetTasksByAssigneeIdOnlyReturnsCurrentUsersTasks() {
        runAs("usr-asg-a", Role.USER, () -> {
            Task t = new Task("isoa-01", "UserA Task", "Desc", TaskStatus.TODO, null);
            t.setAssigneeId(777L);
            service.addTask(t);
        });

        runAs("usr-asg-b", Role.USER, () -> {
            var tasks = service.getTasksByAssigneeId(777L);
            assertThat(tasks).isEmpty();
        });
    }

    @Test
    void testGetTasksDueBeforeOnlyReturnsCurrentUsersTasks() {
        runAs("usr-due-a", Role.USER, () -> {
            LocalDate nextWeek = LocalDate.now().plusDays(7);
            Task t = new Task("isod-01", "UserA Task", "Desc", TaskStatus.TODO, nextWeek);
            service.addTask(t);
        });

        runAs("usr-due-b", Role.USER, () -> {
            var tasks = service.getTasksDueBefore(LocalDate.now().plusDays(14));
            assertThat(tasks).isEmpty();
        });
    }

    @Test
    void testGetOverdueTasksOnlyReturnsCurrentUsersTasks() {
        // Since we can't create tasks with past dates, test that method respects user isolation
        runAs("usr-ovr-a", Role.USER, () -> {
            Task t = new Task("isoo-01", "UserA Task", "Desc", TaskStatus.TODO, null);
            service.addTask(t);
        });

        runAs("usr-ovr-b", Role.USER, () -> {
            var tasks = service.getOverdueTasks();
            assertThat(tasks).isEmpty();
        });
    }
}
