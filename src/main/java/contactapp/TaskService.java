package contactapp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for creating, updating, and deleting {@link Task} objects.
 *
 * Design:
 *  - Process-wide singleton, obtained via {@link #getInstance()}.
 *  - Uses a thread safe {@link ConcurrentHashMap} keyed by taskId.
 *  - All callers interact with tasks through this service, not the map directly.
 */
public final class TaskService {

    /**
     * Lazily initialized singleton instance for the process.
     */
    private static TaskService instance;

    /**
     * In memory store of tasks keyed by taskId.
     * ConcurrentHashMap allows multiple threads to read and write safely.
     */
    private final Map<String, Task> database = new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent direct instantiation.
     * Callers must use {@link #getInstance()} to obtain the shared service.
     */
    private TaskService() {
        // no-op constructor, storage already initialized above
    }

    /**
     * Returns the single shared TaskService instance.
     *
     * Thread safety:
     *  - The method is synchronized so that, if multiple threads call it
     *    at the same time, only one will create the instance.
     *  - After initialization, all callers receive the same object.
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton intentionally exposes the shared instance")
    public static synchronized TaskService getInstance() {
        if (instance == null) {
            instance = new TaskService();
        }
        return instance;
    }

    /**
     * Adds a new task to the in memory store.
     *
     * Behavior:
     *  - Throws IllegalArgumentException if task is null.
     *  - Stores the task under its {@code taskId} if that id is not already present.
     *  - Does not overwrite an existing entry with the same id.
     *
     * @param task task to store (must not be null)
     * @return true if the task was inserted, false if a task with the same id already exists
     */
    public boolean addTask(final Task task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        return database.putIfAbsent(task.getTaskId(), task) == null;
    }

    /**
     * Deletes a task by id.
     *
     * The id is validated and trimmed before removal so callers can pass whitespace
     * and still reference the stored entry.
     *
     * @param taskId id to remove; must not be blank
     * @return true if removed, false otherwise
     */
    public boolean deleteTask(final String taskId) {
        Validation.validateNotBlank(taskId, "taskId");
        return database.remove(taskId.trim()) != null;
    }

    /**
     * Updates the name and description of an existing task.
     *
     * Behavior:
     *  - Validates and trims {@code taskId} to match the stored key.
     *  - Uses {@link ConcurrentHashMap#computeIfPresent} for thread-safe atomic lookup and update.
     *  - If no task exists, returns false and makes no changes.
     *  - If the task exists, calls {@link Task#update(String, String)} so
     *    that validation rules are applied at the entity level.
     *
     * @param taskId      id of the task to update
     * @param newName     new task name (validation is handled by {@link Task#update})
     * @param description new description (validation is handled by {@link Task#update})
     * @return true if the task existed and was updated, false if no task was found with that id
     */
    public boolean updateTask(
            final String taskId,
            final String newName,
            final String description) {
        Validation.validateNotBlank(taskId, "taskId");
        final String normalizedId = taskId.trim();

        // computeIfPresent is atomic: lookup + update happen as one operation
        return database.computeIfPresent(normalizedId, (key, task) -> {
            task.update(newName, description);
            return task;
        }) != null;
    }

    /**
     * Returns an unmodifiable snapshot of the current task store.
     *
     * Implementation detail:
     *  - Returns defensive copies of each Task to prevent external mutation
     *    of internal state. Modifications to the returned tasks do not affect
     *    the tasks stored in the service.
     *
     * Intended use is primarily in tests or read-only diagnostics.
     *
     * @return unmodifiable map of defensive task copies
     */
    public Map<String, Task> getDatabase() {
        return database.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().copy()));
    }

    /**
     * Removes all tasks from the in-memory store.
     *
     * <p>Package-private to limit usage to test code within the same package.
     * This prevents accidental calls from production code outside the package.
     */
    void clearAllTasks() {
        database.clear();
    }
}
