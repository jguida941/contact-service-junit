package contactapp.service;

import contactapp.domain.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link TaskService} behavior mirrors the requirements.
 *
 * <p>Covers:
 * <ul>
 *   <li>Singleton access</li>
 *   <li>Add/delete/update behaviors (including duplicate/missing/blank ID branches)</li>
 *   <li>ID trimming, null guards, and clear-all reset hook</li>
 * </ul>
 *
 * <p>Tests are in the same package as TaskService to access package-private methods.
 */
public class TaskServiceTest {

    /**
     * Clears singleton storage so each test starts with an empty map.
     */
    @BeforeEach
    void reset() {
        TaskService.getInstance().clearAllTasks();
    }

    /**
     * Ensures {@link TaskService#getInstance()} returns the same singleton every time.
     */
    @Test
    void testSingletonInstance() {
        TaskService first = TaskService.getInstance();
        TaskService second = TaskService.getInstance();

        assertThat(first).isSameAs(second);
    }

    /**
     * Confirms {@link TaskService#addTask(Task)} inserts new tasks.
     */
    @Test
    void testAddTask() {
        TaskService service = TaskService.getInstance();
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
        TaskService service = TaskService.getInstance();
        Task original = new Task("100", "Write docs", "Document Task service");
        Task duplicate = new Task("100", "Other", "Another desc");

        assertThat(service.addTask(original)).isTrue();
        assertThat(service.addTask(duplicate)).isFalse();
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
        TaskService service = TaskService.getInstance();

        assertThatThrownBy(() -> service.addTask(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("task must not be null");
    }

    /**
     * Confirms delete removes stored tasks and returns true.
     */
    @Test
    void testDeleteTask() {
        TaskService service = TaskService.getInstance();
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
        TaskService service = TaskService.getInstance();

        assertThatThrownBy(() -> service.deleteTask(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId must not be null or blank");
    }

    /**
     * Verifies delete returns {@code false} when the ID does not exist.
     */
    @Test
    void testDeleteMissingTaskReturnsFalse() {
        TaskService service = TaskService.getInstance();

        assertThat(service.deleteTask("missing")).isFalse();
    }

    /**
     * Proves {@link TaskService#clearAllTasks()} empties the backing store (PIT guard).
     */
    @Test
    void testClearAllTasksRemovesEntries() {
        TaskService service = TaskService.getInstance();
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
        TaskService service = TaskService.getInstance();
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
        TaskService service = TaskService.getInstance();
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
        TaskService service = TaskService.getInstance();

        assertThatThrownBy(() -> service.updateTask(" ", "Name", "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId must not be null or blank");
    }

    /**
     * Verifies {@link TaskService#updateTask} returns {@code false} when the ID is missing.
     */
    @Test
    void testUpdateMissingTaskReturnsFalse() {
        TaskService service = TaskService.getInstance();

        assertThat(service.updateTask("missing", "Name", "Desc")).isFalse();
    }

    /**
     * Confirms invalid update inputs throw and leave the stored Task unchanged.
     */
    @Test
    void testUpdateTaskInvalidValuesLeaveStateUnchanged() {
        TaskService service = TaskService.getInstance();
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
        TaskService service = TaskService.getInstance();
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

    /**
     * Covers the cold-start branch in getInstance() where instance is null.
     *
     * <p>Uses reflection to reset the static instance field, then verifies
     * getInstance() creates a new instance. This ensures full branch coverage
     * of the lazy initialization pattern.
     */
    @Test
    void testGetInstanceColdStart() throws Exception {
        // Reset static instance to null via reflection
        Field instanceField = TaskService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Now getInstance() should create a new instance
        TaskService service = TaskService.getInstance();
        assertThat(service).isNotNull();

        // Verify the instance was properly registered
        TaskService second = TaskService.getInstance();
        assertThat(second).isSameAs(service);
    }

    // ==================== getTaskById Tests ====================

    /**
     * Verifies getTaskById returns the task when it exists.
     */
    @Test
    void testGetTaskByIdReturnsTask() {
        TaskService service = TaskService.getInstance();
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
        TaskService service = TaskService.getInstance();

        var result = service.getTaskById("nonexistent");

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getTaskById throws when ID is blank.
     */
    @Test
    void testGetTaskByIdBlankIdThrows() {
        TaskService service = TaskService.getInstance();

        assertThatThrownBy(() -> service.getTaskById(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("taskId must not be null or blank");
    }

    /**
     * Verifies getTaskById trims the ID before lookup.
     */
    @Test
    void testGetTaskByIdTrimsId() {
        TaskService service = TaskService.getInstance();
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
        TaskService service = TaskService.getInstance();

        var result = service.getAllTasks();

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getAllTasks returns all tasks.
     */
    @Test
    void testGetAllTasksReturnsAllTasks() {
        TaskService service = TaskService.getInstance();
        service.addTask(new Task("701", "First Task", "First Description"));
        service.addTask(new Task("702", "Second Task", "Second Description"));

        var result = service.getAllTasks();

        assertThat(result).hasSize(2);
    }
}
