package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.domain.Task;
import contactapp.domain.TaskStatus;
import contactapp.persistence.store.InMemoryTaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mutation-killing tests for TaskService validation guards.
 *
 * <p>These tests target VoidMethodCallMutator and boundary condition mutations
 * that survive in the H2/in-memory code path. Uses InMemoryTaskStore to ensure
 * tests run on both Windows and Linux CI environments without Testcontainers.
 *
 * <p><b>NOT tagged with "legacy-singleton"</b> - these tests run in the main pipeline.
 */
class TaskServiceValidationTest {

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(new InMemoryTaskStore());
        service.clearAllTasks();
    }

    // ==================== Mutation Testing: Validation Guards ====================

    /**
     * Kills VoidMethodCallMutator on deleteTask validation (line 191).
     * Ensures deleteTask throws IllegalArgumentException for null taskId, not NPE or other exception.
     */
    @Test
    void testDeleteTaskNullIdThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> service.deleteTask(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Kills VoidMethodCallMutator on updateTask validation (line 216).
     * Ensures updateTask throws IllegalArgumentException for null taskId, not NPE.
     */
    @Test
    void testUpdateTaskNullIdThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> service.updateTask(null, "Name", "Desc"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Kills VoidMethodCallMutator on getTaskById validation (line 419).
     * Ensures getTaskById throws IllegalArgumentException for null taskId, not NPE.
     */
    @Test
    void testGetTaskByIdNullIdThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getTaskById(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Kills VoidMethodCallMutator on clearAllTasks (line 574).
     * Verifies that clearAllTasks actually removes data from the store,
     * not just returns without calling store.deleteAll().
     */
    @Test
    void testClearAllTasksActuallyDeletesData() {
        // Add multiple tasks
        service.addTask(new Task("clear-01", "Task 1", "Desc 1"));
        service.addTask(new Task("clear-02", "Task 2", "Desc 2"));
        service.addTask(new Task("clear-03", "Task 3", "Desc 3"));

        // Verify tasks exist
        assertThat(service.getAllTasks()).hasSize(3);

        // Clear all tasks
        service.clearAllTasks();

        // Verify all tasks are gone - this kills the mutation that removes store.deleteAll()
        assertThat(service.getAllTasks()).isEmpty();

        // Also verify getTaskById returns empty for previously existing tasks
        assertThat(service.getTaskById("clear-01")).isEmpty();
        assertThat(service.getTaskById("clear-02")).isEmpty();
        assertThat(service.getTaskById("clear-03")).isEmpty();
    }

    /**
     * Kills BooleanTrueReturnValsMutator on addTask (line 168).
     * Although addTask can't return false (it throws on duplicate),
     * this test documents the expected behavior.
     */
    @Test
    void testAddTaskCannotReturnFalseOnlyThrowsOrReturnsTrue() {
        Task task = new Task("bool-test", "Task", "Desc");

        // First add should succeed
        boolean result = service.addTask(task);
        assertThat(result).isTrue();

        // Second add should throw, not return false
        Task duplicate = new Task("bool-test", "Another", "Desc");
        assertThatThrownBy(() -> service.addTask(duplicate))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("bool-test");
    }

    // ==================== Mutation Testing: Boundary Conditions ====================

    /**
     * Tests deleteTask with edge case: empty string after trim.
     * Ensures validation catches "" as blank, not just null.
     */
    @Test
    void testDeleteTaskEmptyStringAfterTrimThrows() {
        assertThatThrownBy(() -> service.deleteTask("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Tests updateTask with edge case: tab and newline characters.
     * Ensures validation rejects whitespace-only IDs.
     */
    @Test
    void testUpdateTaskWhitespaceOnlyIdThrows() {
        assertThatThrownBy(() -> service.updateTask("\t\n", "Name", "Desc"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Tests getTaskById with edge case: ID that's blank after trim.
     */
    @Test
    void testGetTaskByIdBlankAfterTrimThrows() {
        assertThatThrownBy(() -> service.getTaskById("  \t  "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    // ==================== 5-arg updateTask Overload Tests ====================

    /**
     * Kills VoidMethodCallMutator on 5-arg updateTask validation (line 261).
     */
    @Test
    void testUpdateTask5ArgNullIdThrows() {
        assertThatThrownBy(() -> service.updateTask(null, "Name", "Desc",
                TaskStatus.TODO, LocalDate.now().plusDays(1)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Kills BooleanTrueReturnValsMutator - verifies 5-arg updateTask returns true on success.
     */
    @Test
    void testUpdateTask5ArgReturnsTrueOnSuccess() {
        service.addTask(new Task("upd5-01", "Original", "Desc"));

        boolean updated = service.updateTask("upd5-01", "Updated", "New Desc",
                TaskStatus.IN_PROGRESS, LocalDate.now().plusDays(7));

        assertThat(updated).isTrue();
        assertThat(service.getTaskById("upd5-01").orElseThrow().getStatus())
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    /**
     * Kills BooleanFalseReturnValsMutator - verifies 5-arg updateTask returns false when not found.
     */
    @Test
    void testUpdateTask5ArgReturnsFalseWhenNotFound() {
        boolean updated = service.updateTask("nonexistent", "Name", "Desc",
                TaskStatus.TODO, null);

        assertThat(updated).isFalse();
    }

    // ==================== 6-arg updateTask Overload Tests ====================

    /**
     * Kills VoidMethodCallMutator on 6-arg updateTask validation (line 283).
     */
    @Test
    void testUpdateTask6ArgNullIdThrows() {
        assertThatThrownBy(() -> service.updateTask(null, "Name", "Desc",
                TaskStatus.TODO, null, "proj-001"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Kills BooleanTrueReturnValsMutator - verifies 6-arg updateTask returns true on success.
     */
    @Test
    void testUpdateTask6ArgReturnsTrueOnSuccess() {
        service.addTask(new Task("upd6-01", "Original", "Desc"));

        boolean updated = service.updateTask("upd6-01", "Updated", "New Desc",
                TaskStatus.TODO, null, "project-123");

        assertThat(updated).isTrue();
        assertThat(service.getTaskById("upd6-01").orElseThrow().getProjectId())
                .isEqualTo("project-123");
    }

    /**
     * Kills BooleanFalseReturnValsMutator - verifies 6-arg updateTask returns false when not found.
     */
    @Test
    void testUpdateTask6ArgReturnsFalseWhenNotFound() {
        boolean updated = service.updateTask("nonexistent", "Name", "Desc",
                TaskStatus.TODO, null, "proj-001");

        assertThat(updated).isFalse();
    }

    // ==================== 7-arg updateTask Overload Tests ====================

    /**
     * Kills VoidMethodCallMutator on 7-arg updateTask validation (line 308).
     * This is the SURVIVED mutation from PITest report.
     */
    @Test
    void testUpdateTask7ArgNullIdThrows() {
        assertThatThrownBy(() -> service.updateTask(null, "Name", "Desc",
                TaskStatus.TODO, null, null, UUID.randomUUID()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Kills VoidMethodCallMutator on 7-arg updateTask validation - blank ID.
     */
    @Test
    void testUpdateTask7ArgBlankIdThrows() {
        assertThatThrownBy(() -> service.updateTask("   ", "Name", "Desc",
                TaskStatus.TODO, null, null, UUID.randomUUID()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Kills BooleanTrueReturnValsMutator - verifies 7-arg updateTask returns true on success.
     * This is the SURVIVED mutation from PITest report.
     */
    @Test
    void testUpdateTask7ArgReturnsTrueOnSuccess() {
        service.addTask(new Task("upd7-01", "Original", "Desc"));
        UUID assigneeId = UUID.randomUUID();

        boolean updated = service.updateTask("upd7-01", "Updated", "New Desc",
                TaskStatus.DONE, LocalDate.now().plusDays(14), "proj-xyz", assigneeId);

        assertThat(updated).isTrue();
        Task stored = service.getTaskById("upd7-01").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(stored.getProjectId()).isEqualTo("proj-xyz");
        assertThat(stored.getAssigneeId()).isEqualTo(assigneeId);
    }

    /**
     * Kills BooleanFalseReturnValsMutator - verifies 7-arg updateTask returns false when not found.
     */
    @Test
    void testUpdateTask7ArgReturnsFalseWhenNotFound() {
        boolean updated = service.updateTask("nonexistent", "Name", "Desc",
                TaskStatus.TODO, null, null, UUID.randomUUID());

        assertThat(updated).isFalse();
    }

    // ==================== Legacy Path Coverage Tests ====================

    /**
     * Kills NegateConditionalsMutator on legacy updateTask path (line 236).
     * Verifies the legacy store path returns false when task not found.
     */
    @Test
    void testUpdateTaskLegacyPathReturnsFalse() {
        // InMemoryTaskStore is used, so we're testing the legacy path
        boolean result = service.updateTask("nonexistent-id", "New Name", "New Desc");

        assertThat(result).isFalse();
    }

    /**
     * Kills VoidMethodCallMutator on legacy updateTask path - verifies store.save() is called.
     */
    @Test
    void testUpdateTaskLegacyPathSavesChanges() {
        service.addTask(new Task("legacy-upd", "Original", "Original Desc"));

        boolean updated = service.updateTask("legacy-upd", "Updated Name", "Updated Desc");

        assertThat(updated).isTrue();
        Task stored = service.getTaskById("legacy-upd").orElseThrow();
        assertThat(stored.getName()).isEqualTo("Updated Name");
        assertThat(stored.getDescription()).isEqualTo("Updated Desc");
    }

    /**
     * Kills mutations on deleteTask legacy path - returns false for nonexistent task.
     */
    @Test
    void testDeleteTaskLegacyPathReturnsFalseWhenNotFound() {
        boolean deleted = service.deleteTask("nonexistent");

        assertThat(deleted).isFalse();
    }

    /**
     * Kills VoidMethodCallMutator on deleteTask legacy path - verifies deletion.
     */
    @Test
    void testDeleteTaskLegacyPathDeletesTask() {
        service.addTask(new Task("del-legacy", "To Delete", "Will be removed"));
        assertThat(service.getTaskById("del-legacy")).isPresent();

        boolean deleted = service.deleteTask("del-legacy");

        assertThat(deleted).isTrue();
        assertThat(service.getTaskById("del-legacy")).isEmpty();
    }

    // ==================== Query Method Legacy Path Tests ====================

    /**
     * Kills mutations on getTasksByStatus legacy path.
     */
    @Test
    void testGetTasksByStatusLegacyPath() {
        service.addTask(new Task("qs-01", "Todo Task", "Desc", TaskStatus.TODO, null));
        service.addTask(new Task("qs-02", "Done Task", "Desc", TaskStatus.DONE, null));

        var todoTasks = service.getTasksByStatus(TaskStatus.TODO);
        var doneTasks = service.getTasksByStatus(TaskStatus.DONE);

        assertThat(todoTasks).hasSize(1);
        assertThat(todoTasks.get(0).getTaskId()).isEqualTo("qs-01");
        assertThat(doneTasks).hasSize(1);
        assertThat(doneTasks.get(0).getTaskId()).isEqualTo("qs-02");
    }

    /**
     * Kills mutations on getTasksByStatus - verifies empty list returned.
     */
    @Test
    void testGetTasksByStatusReturnsEmptyList() {
        var result = service.getTasksByStatus(TaskStatus.IN_PROGRESS);

        assertThat(result).isEmpty();
    }

    /**
     * Kills VoidMethodCallMutator on getTasksByStatus validation.
     */
    @Test
    void testGetTasksByStatusNullThrows() {
        assertThatThrownBy(() -> service.getTasksByStatus(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status must not be null");
    }

    /**
     * Kills mutations on getTasksDueBefore legacy path.
     */
    @Test
    void testGetTasksDueBeforeLegacyPath() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate nextWeek = today.plusDays(7);

        service.addTask(new Task("db-01", "Tomorrow", "Desc", TaskStatus.TODO, tomorrow));
        service.addTask(new Task("db-02", "Next Week", "Desc", TaskStatus.TODO, nextWeek));
        service.addTask(new Task("db-03", "No Date", "Desc", TaskStatus.TODO, null));

        var beforeNextWeek = service.getTasksDueBefore(nextWeek);

        assertThat(beforeNextWeek).hasSize(1);
        assertThat(beforeNextWeek.get(0).getTaskId()).isEqualTo("db-01");
    }

    /**
     * Kills VoidMethodCallMutator on getTasksDueBefore validation.
     */
    @Test
    void testGetTasksDueBeforeNullThrows() {
        assertThatThrownBy(() -> service.getTasksDueBefore(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date must not be null");
    }

    /**
     * Kills mutations on getTasksByProjectId legacy path.
     */
    @Test
    void testGetTasksByProjectIdLegacyPath() {
        Task t1 = new Task("proj-01", "Project A Task", "Desc");
        t1.setProjectId("project-a");
        Task t2 = new Task("proj-02", "Project B Task", "Desc");
        t2.setProjectId("project-b");
        service.addTask(t1);
        service.addTask(t2);

        var projectATasks = service.getTasksByProjectId("project-a");

        assertThat(projectATasks).hasSize(1);
        assertThat(projectATasks.get(0).getTaskId()).isEqualTo("proj-01");
    }

    /**
     * Kills VoidMethodCallMutator on getTasksByProjectId validation.
     */
    @Test
    void testGetTasksByProjectIdBlankThrows() {
        assertThatThrownBy(() -> service.getTasksByProjectId("  "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId must not be null or blank");
    }

    /**
     * Kills mutations on getUnassignedTasks legacy path.
     */
    @Test
    void testGetUnassignedTasksLegacyPath() {
        Task assigned = new Task("ua-01", "Assigned", "Desc");
        assigned.setProjectId("some-project");
        Task unassigned = new Task("ua-02", "Unassigned", "Desc");
        service.addTask(assigned);
        service.addTask(unassigned);

        var unassignedTasks = service.getUnassignedTasks();

        assertThat(unassignedTasks).hasSize(1);
        assertThat(unassignedTasks.get(0).getTaskId()).isEqualTo("ua-02");
    }

    /**
     * Kills mutations on getTasksByAssigneeId legacy path.
     */
    @Test
    void testGetTasksByAssigneeIdLegacyPath() {
        UUID assigneeId = UUID.randomUUID();
        Task assigned = new Task("asgn-01", "Assigned", "Desc");
        assigned.setAssigneeId(assigneeId);
        Task unassigned = new Task("asgn-02", "Unassigned", "Desc");
        service.addTask(assigned);
        service.addTask(unassigned);

        var assignedTasks = service.getTasksByAssigneeId(assigneeId);

        assertThat(assignedTasks).hasSize(1);
        assertThat(assignedTasks.get(0).getTaskId()).isEqualTo("asgn-01");
    }

    /**
     * Kills VoidMethodCallMutator on getTasksByAssigneeId validation.
     */
    @Test
    void testGetTasksByAssigneeIdNullThrows() {
        assertThatThrownBy(() -> service.getTasksByAssigneeId(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assigneeId must not be null");
    }

    /**
     * Kills mutations on getOverdueTasks legacy path.
     * Uses Clock injection to simulate "today" being 10 days in the future,
     * making our task with a dueDate of +5 days appear overdue.
     */
    @Test
    void testGetOverdueTasksLegacyPath() {
        // Create a task due in 5 days (valid at creation time)
        LocalDate dueDateInFiveDays = LocalDate.now().plusDays(5);
        service.addTask(new Task("od-overdue", "Will Be Overdue", "Desc",
                TaskStatus.IN_PROGRESS, dueDateInFiveDays));

        // Create a DONE task (should be excluded even if overdue)
        service.addTask(new Task("od-done", "Done Task", "Desc",
                TaskStatus.DONE, dueDateInFiveDays));

        // Create a future task (not overdue)
        service.addTask(new Task("od-future", "Future Task", "Desc",
                TaskStatus.TODO, LocalDate.now().plusDays(30)));

        // Create a task with no due date (should be excluded)
        service.addTask(new Task("od-nodate", "No Date", "Desc", TaskStatus.TODO, null));

        // Set clock to 10 days in the future, making "od-overdue" appear overdue
        Clock futureClock = Clock.fixed(
                Instant.now().plus(10, ChronoUnit.DAYS),
                ZoneOffset.UTC);
        service.setClock(futureClock);

        try {
            var overdueTasks = service.getOverdueTasks();

            // Should return exactly the IN_PROGRESS task (not DONE, not future, not null date)
            assertThat(overdueTasks)
                    .as("Should return only incomplete, past-due tasks")
                    .hasSize(1);
            assertThat(overdueTasks.get(0).getTaskId()).isEqualTo("od-overdue");
            assertThat(overdueTasks.get(0).getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        } finally {
            // Reset clock
            service.setClock(Clock.systemUTC());
        }
    }

    /**
     * Kills NegateConditionalsMutator on getOverdueTasks - tests all filter conditions.
     */
    @Test
    void testGetOverdueTasksFilterConditions() {
        LocalDate dueDateInFiveDays = LocalDate.now().plusDays(5);

        // Task with dueDate != null, before today, status != DONE → should be returned
        service.addTask(new Task("flt-pass", "Pass", "Desc",
                TaskStatus.TODO, dueDateInFiveDays));

        // Task with dueDate == null → should NOT be returned
        service.addTask(new Task("flt-null", "Null Date", "Desc",
                TaskStatus.TODO, null));

        // Task with status == DONE → should NOT be returned even if overdue
        service.addTask(new Task("flt-done", "Done", "Desc",
                TaskStatus.DONE, dueDateInFiveDays));

        // Set clock to make tasks appear overdue
        Clock futureClock = Clock.fixed(
                Instant.now().plus(10, ChronoUnit.DAYS),
                ZoneOffset.UTC);
        service.setClock(futureClock);

        try {
            var overdueTasks = service.getOverdueTasks();

            // Only "flt-pass" should be returned
            assertThat(overdueTasks).hasSize(1);
            assertThat(overdueTasks.get(0).getTaskId()).isEqualTo("flt-pass");
        } finally {
            service.setClock(Clock.systemUTC());
        }
    }

    /**
     * Kills mutations on getAllTasks legacy path - defensive copy.
     */
    @Test
    void testGetAllTasksLegacyReturnsDefensiveCopy() {
        service.addTask(new Task("dc-01", "Original", "Desc"));

        var allTasks = service.getAllTasks();
        allTasks.get(0).setName("Mutated");

        // Verify internal state unchanged
        assertThat(service.getTaskById("dc-01").orElseThrow().getName())
                .isEqualTo("Original");
    }

    /**
     * Kills mutations on getDatabase legacy path.
     */
    @Test
    void testGetDatabaseLegacyPath() {
        service.addTask(new Task("gdb-01", "Task 1", "Desc 1"));
        service.addTask(new Task("gdb-02", "Task 2", "Desc 2"));

        var database = service.getDatabase();

        assertThat(database).hasSize(2);
        assertThat(database).containsKeys("gdb-01", "gdb-02");
    }

    // ==================== Archive/Unarchive Legacy Path Tests ====================

    /**
     * Kills VoidMethodCallMutator on archiveTask legacy path - verifies store.save() is called.
     * The mutation removes store.save(), so we verify the task is PERSISTED by fetching it again.
     */
    @Test
    void testArchiveTaskLegacyPath() {
        service.addTask(new Task("arch-01", "To Archive", "Desc"));

        var result = service.archiveTask("arch-01");

        // Verify returned value
        assertThat(result).isPresent();
        assertThat(result.get().isArchived()).isTrue();

        // CRITICAL: Verify the change was PERSISTED by fetching from store again
        // This kills the "removed call to store.save()" mutation
        Task persistedTask = service.getTaskById("arch-01").orElseThrow();
        assertThat(persistedTask.isArchived())
                .as("Archive should be persisted to store")
                .isTrue();
    }

    /**
     * Kills mutations on archiveTask legacy path - not found case.
     */
    @Test
    void testArchiveTaskLegacyReturnsEmptyWhenNotFound() {
        var result = service.archiveTask("nonexistent");

        assertThat(result).isEmpty();
    }

    /**
     * Kills VoidMethodCallMutator on unarchiveTask legacy path - verifies store.save() is called.
     * The mutation removes store.save(), so we verify the task is PERSISTED by fetching it again.
     */
    @Test
    void testUnarchiveTaskLegacyPath() {
        service.addTask(new Task("unarch-01", "To Unarchive", "Desc"));
        service.archiveTask("unarch-01");

        // Verify task is archived before unarchiving
        assertThat(service.getTaskById("unarch-01").orElseThrow().isArchived()).isTrue();

        var result = service.unarchiveTask("unarch-01");

        // Verify returned value
        assertThat(result).isPresent();
        assertThat(result.get().isArchived()).isFalse();

        // CRITICAL: Verify the change was PERSISTED by fetching from store again
        // This kills the "removed call to store.save()" mutation
        Task persistedTask = service.getTaskById("unarch-01").orElseThrow();
        assertThat(persistedTask.isArchived())
                .as("Unarchive should be persisted to store")
                .isFalse();
    }

    /**
     * Kills mutations on unarchiveTask legacy path - not found case.
     */
    @Test
    void testUnarchiveTaskLegacyReturnsEmptyWhenNotFound() {
        var result = service.unarchiveTask("nonexistent");

        assertThat(result).isEmpty();
    }

    /**
     * Kills VoidMethodCallMutator on archiveTask validation.
     */
    @Test
    void testArchiveTaskBlankIdThrows() {
        assertThatThrownBy(() -> service.archiveTask("  "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }

    /**
     * Kills VoidMethodCallMutator on unarchiveTask validation.
     */
    @Test
    void testUnarchiveTaskBlankIdThrows() {
        assertThatThrownBy(() -> service.unarchiveTask("  "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId must not be null or blank");
    }
}
