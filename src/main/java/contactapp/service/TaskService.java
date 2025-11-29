package contactapp.service;

import contactapp.domain.Task;
import contactapp.domain.Validation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Service responsible for creating, updating, and deleting {@link Task} objects.
 *
 * <h2>Spring Integration</h2>
 * <p>Annotated with {@link Service} so Spring can manage the bean lifecycle.
 * The singleton pattern via {@link #getInstance()} is retained for backward
 * compatibility with existing tests and non-Spring callers.
 *
 * <h2>Thread Safety</h2>
 * <p>Uses {@link ConcurrentHashMap} for O(1) thread-safe operations.
 * Updates use {@code computeIfPresent} for atomic lookup + update.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Singleton pattern retained for backward compatibility</li>
 *   <li>{@code @Service} added for Spring DI in controllers (Phase 2)</li>
 *   <li>Defensive copies returned from {@link #getDatabase()}</li>
 *   <li>{@link #clearAllTasks()} is package-private for test isolation</li>
 * </ul>
 *
 * @see Task
 * @see Validation
 */
@Service
public final class TaskService {

    /**
     * Lazily initialized singleton instance for the process.
     * Retained for backward compatibility with existing tests.
     */
    private static TaskService instance;

    /**
     * In-memory store of tasks keyed by taskId.
     *
     * <p>Static so all access paths (Spring DI and {@link #getInstance()}) share
     * the same backing store regardless of how many instances are created.
     *
     * <p>ConcurrentHashMap allows multiple threads to read and write safely
     * with O(1) average time complexity.
     */
    private static final Map<String, Task> DATABASE = new ConcurrentHashMap<>();

    /**
     * Default constructor for Spring bean creation.
     *
     * <p>Public to allow Spring to instantiate the service via reflection.
     * For non-Spring usage, prefer {@link #getInstance()}.
     *
     * <p>Thread-safe: Registers this instance as the static singleton only if none
     * exists, preserving any data already added through {@link #getInstance()}.
     * This ensures Spring-created beans and legacy callers share the same backing store
     * regardless of initialization order.
     */
    public TaskService() {
        synchronized (TaskService.class) {
            if (instance == null) {
                instance = this;
            }
        }
    }

    /**
     * Returns the single shared TaskService instance.
     *
     * <p>Thread safety: The method is synchronized so that, if multiple threads
     * call it at the same time, only one will create the instance.
     *
     * <p>Note: When using Spring DI (e.g., in controllers), prefer constructor
     * injection over this method. This exists for backward compatibility.
     * Both access patterns share the same instance and backing store.
     *
     * @return the singleton TaskService instance
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton intentionally exposes shared instance for backward compatibility")
    public static synchronized TaskService getInstance() {
        if (instance == null) {
            new TaskService();
        }
        return instance;
    }

    /**
     * Adds a new task to the in-memory store.
     *
     * <p>Uses {@link ConcurrentHashMap#putIfAbsent} for atomic uniqueness check.
     *
     * @param task task to store (must not be null)
     * @return true if the task was inserted, false if a task with the same id already exists
     * @throws IllegalArgumentException if task is null
     */
    public boolean addTask(final Task task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        return DATABASE.putIfAbsent(task.getTaskId(), task) == null;
    }

    /**
     * Deletes a task by id.
     *
     * <p>The id is validated and trimmed before removal so callers can pass
     * whitespace and still reference the stored entry.
     *
     * @param taskId id to remove; must not be blank
     * @return true if removed, false otherwise
     * @throws IllegalArgumentException if taskId is null or blank
     */
    public boolean deleteTask(final String taskId) {
        Validation.validateNotBlank(taskId, "taskId");
        return DATABASE.remove(taskId.trim()) != null;
    }

    /**
     * Updates the name and description of an existing task.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Validates and trims {@code taskId} to match the stored key</li>
     *   <li>Uses {@link ConcurrentHashMap#computeIfPresent} for thread-safe atomic lookup and update</li>
     *   <li>Delegates validation to {@link Task#update(String, String)}</li>
     * </ul>
     *
     * @param taskId      id of the task to update
     * @param newName     new task name
     * @param description new description
     * @return true if the task existed and was updated, false if no task was found
     * @throws IllegalArgumentException if any value is invalid
     */
    public boolean updateTask(
            final String taskId,
            final String newName,
            final String description) {
        Validation.validateNotBlank(taskId, "taskId");
        final String normalizedId = taskId.trim();

        // computeIfPresent is atomic: lookup + update happen as one operation
        return DATABASE.computeIfPresent(normalizedId, (key, task) -> {
            task.update(newName, description);
            return task;
        }) != null;
    }

    /**
     * Returns an unmodifiable snapshot of the current task store.
     *
     * <p>Returns defensive copies of each Task to prevent external mutation
     * of internal state.
     *
     * @return unmodifiable map of defensive task copies
     */
    public Map<String, Task> getDatabase() {
        return DATABASE.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().copy()));
    }

    /**
     * Returns all tasks as a list of defensive copies.
     *
     * <p>Encapsulates the internal storage structure so controllers don't
     * need to access getDatabase() directly.
     *
     * @return list of task defensive copies
     */
    public List<Task> getAllTasks() {
        return DATABASE.values().stream()
                .map(Task::copy)
                .toList();
    }

    /**
     * Finds a task by ID.
     *
     * <p>The ID is validated and trimmed before lookup so callers can pass
     * values like " 123 " and still find the task stored as "123".
     *
     * @param taskId the task ID to search for
     * @return Optional containing a defensive copy of the task, or empty if not found
     * @throws IllegalArgumentException if taskId is null or blank
     */
    public Optional<Task> getTaskById(final String taskId) {
        Validation.validateNotBlank(taskId, "taskId");
        final Task task = DATABASE.get(taskId.trim());
        return task == null ? Optional.empty() : Optional.of(task.copy());
    }

    /**
     * Removes all tasks from the in-memory store.
     *
     * <p>Package-private to limit usage to test code within the same package.
     * This prevents accidental calls from production code outside the package.
     */
    void clearAllTasks() {
        DATABASE.clear();
    }
}
