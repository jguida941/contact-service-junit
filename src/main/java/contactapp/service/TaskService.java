package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.config.ApplicationContextProvider;
import contactapp.domain.Task;
import contactapp.domain.Validation;
import contactapp.persistence.store.InMemoryTaskStore;
import contactapp.persistence.store.JpaTaskStore;
import contactapp.persistence.store.TaskStore;
import contactapp.security.Role;
import contactapp.security.User;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for creating, updating, and deleting {@link Task} objects.
 *
 * <p>Persistence is delegated to {@link TaskStore} so Spring-managed JPA repositories
 * and legacy {@link #getInstance()} callers share the same behavior while
 * {@link #clearAllTasks()} stays package-private for test isolation.
 *
 * <h2>Why Not Final?</h2>
 * <p>This class was previously {@code final}. The modifier was removed because
 * Spring's {@code @Transactional} annotation uses CGLIB proxy subclassing,
 * which requires non-final classes for method interception.
 */
@Service
@Transactional
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Constructor calls registerInstance() for singleton pattern compatibility; "
                + "this is intentional for backward compatibility with legacy non-Spring callers")
public class TaskService {

    private static TaskService instance;

    private final TaskStore store;
    private final boolean legacyStore;
    private Clock clock;  // Non-final for test support (setClock)

    @org.springframework.beans.factory.annotation.Autowired
    public TaskService(final TaskStore store, final Clock clock) {
        this(store, clock, false);
    }

    /**
     * Package-private constructor for tests that don't need a custom clock.
     *
     * <p>Uses systemDefaultZone() as the clock. For tests that need clock control,
     * use the two-arg constructor or the factory method withClock().
     *
     * @param store the task store
     */
    TaskService(final TaskStore store) {
        this(store, Clock.systemDefaultZone(), true);
    }

    /**
     * Package-private constructor for legacy tests that need to control the legacyStore flag.
     *
     * <p>Uses systemDefaultZone() as the clock. This maintains backward compatibility
     * with tests that use reflection to call the (store, boolean) constructor.
     *
     * @param store the task store
     * @param legacyStore whether this is a legacy in-memory store
     */
    TaskService(final TaskStore store, final boolean legacyStore) {
        this(store, Clock.systemDefaultZone(), legacyStore);
    }

    private TaskService(final TaskStore store, final Clock clock, final boolean legacyStore) {
        this.store = store;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.legacyStore = legacyStore;
        registerInstance(this);
    }

    private static synchronized void registerInstance(final TaskService candidate) {
        if (instance != null && instance.legacyStore && !candidate.legacyStore) {
            instance.getAllTasks().forEach(candidate::addTask);
        }
        instance = candidate;
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
     * <p>If called before Spring context initializes, lazily creates a service
     * backed by {@link InMemoryTaskStore}. This preserves backward
     * compatibility for legacy non-Spring callers.
     *
     * @return the singleton TaskService instance
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton intentionally exposes shared instance for backward compatibility")
    public static synchronized TaskService getInstance() {
        if (instance != null) {
            return instance;
        }
        final ApplicationContext context = ApplicationContextProvider.getContext();
        if (context != null) {
            return context.getBean(TaskService.class);
        }
        return new TaskService(new InMemoryTaskStore(), Clock.systemDefaultZone(), true);
    }

    /**
     * Gets the currently authenticated user from the Spring Security context.
     *
     * @return the authenticated user
     * @throws IllegalStateException if no user is authenticated
     */
    private User getCurrentUser() {
        final var context = SecurityContextHolder.getContext();
        final Authentication authentication = context != null ? context.getAuthentication() : null;
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        final Object principal = authentication.getPrincipal();
        if (!(principal instanceof User)) {
            throw new IllegalStateException("Authenticated principal is not a User");
        }
        return (User) principal;
    }

    /**
     * Checks if the current user has ADMIN role.
     *
     * @param user the user to check
     * @return true if the user is an ADMIN
     */
    private boolean isAdmin(final User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    /**
     * Adds a new task to the persistent store for the authenticated user.
     *
     * <p>Duplicate IDs bubble up as {@link DuplicateResourceException} so the REST layer can
     * consistently translate them into HTTP 409 responses. This relies on database uniqueness
     * constraints instead of manual {@code existsById} checks to avoid TOCTOU races.
     *
     * <p>The "due date must not be in the past" business rule is validated here using the
     * injected Clock, ensuring consistent timezone handling across the application.
     *
     * @param task the task to add; must not be null
     * @return true if the task was added
     * @throws IllegalArgumentException if task is null or dueDate is in the past
     * @throws DuplicateResourceException if a task with the same ID already exists
     */
    public boolean addTask(final Task task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        final String taskId = task.getTaskId();
        if (taskId == null) {
            throw new IllegalArgumentException("taskId must not be null");
        }

        // Validate dueDate using the injected clock for proper timezone handling
        Validation.validateOptionalDateNotPast(task.getDueDate(), "Due Date", clock);

        // Use user-aware store methods if available
        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            try {
                jpaStore.insert(task, currentUser);
                return true;
            } catch (DataIntegrityViolationException e) {
                throw new DuplicateResourceException(
                        "Task with id '" + taskId + "' already exists", e);
            }
        }

        // Fallback for legacy in-memory store
        final boolean saved = attemptLegacySave(task);
        return saved;
    }

    private boolean attemptLegacySave(final Task task) {
        if (store.existsById(task.getTaskId())) {
            throw new DuplicateResourceException(
                    "Task with id '" + task.getTaskId() + "' already exists");
        }
        store.save(task);
        return true;
    }

    /**
     * Deletes a task by id for the authenticated user.
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
        final String trimmedId = taskId.trim();

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.deleteById(trimmedId, currentUser);
        }

        return store.deleteById(trimmedId);
    }

    /**
     * Updates the name and description of an existing task for the authenticated user.
     *
     * @param taskId the id of the task to update
     * @param newName new task name
     * @param description new description
     * @return true if the task exists and was updated, false if no task with that id exists
     * @throws IllegalArgumentException if any new field value is invalid
     */
    public boolean updateTask(
            final String taskId,
            final String newName,
            final String description) {
        Validation.validateNotBlank(taskId, "taskId");
        final String normalizedId = taskId.trim();

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();

            final Optional<Task> task = jpaStore.findById(normalizedId, currentUser);
            if (task.isEmpty()) {
                return false;
            }
            final Task existing = task.get();
            existing.update(newName, description);
            jpaStore.save(existing, currentUser);
            return true;
        }

        // Fallback for legacy in-memory store
        final Optional<Task> task = store.findById(normalizedId);
        if (task.isEmpty()) {
            return false;
        }
        final Task existing = task.get();
        existing.update(newName, description);
        store.save(existing);
        return true;
    }

    /**
     * Updates all fields of an existing task for the authenticated user.
     *
     * @param taskId the id of the task to update
     * @param newName new task name
     * @param description new description
     * @param status new status (defaults to TODO if null)
     * @param dueDate new due date (nullable)
     * @return true if the task exists and was updated, false if no task with that id exists
     * @throws IllegalArgumentException if any new field value is invalid
     */
    public boolean updateTask(
            final String taskId,
            final String newName,
            final String description,
            final contactapp.domain.TaskStatus status,
            final java.time.LocalDate dueDate) {
        return updateTask(taskId, newName, description, status, dueDate, null);
    }

    /**
     * Updates all fields of an existing task for the authenticated user, including project link.
     *
     * @param taskId the id of the task to update
     * @param newName new task name
     * @param description new description
     * @param status new status (defaults to TODO if null)
     * @param dueDate new due date (nullable)
     * @param projectId new project ID (nullable, can unlink from project)
     * @return true if the task exists and was updated, false if no task with that id exists
     * @throws IllegalArgumentException if any new field value is invalid
     */
    public boolean updateTask(
            final String taskId,
            final String newName,
            final String description,
            final contactapp.domain.TaskStatus status,
            final java.time.LocalDate dueDate,
            final String projectId) {
        return updateTask(taskId, newName, description, status, dueDate, projectId, null);
    }

    /**
     * Updates all fields of an existing task for the authenticated user, including project link and assignee.
     *
     * <p>The "due date must not be in the past" business rule is validated here using the
     * injected Clock, ensuring consistent timezone handling across the application.
     *
     * @param taskId the id of the task to update
     * @param newName new task name
     * @param description new description
     * @param status new status (defaults to TODO if null)
     * @param dueDate new due date (nullable)
     * @param projectId new project ID (nullable, can unlink from project)
     * @param assigneeId new assignee user ID (nullable, can unassign)
     * @return true if the task exists and was updated, false if no task with that id exists
     * @throws IllegalArgumentException if any new field value is invalid or dueDate is in the past
     */
    public boolean updateTask(
            final String taskId,
            final String newName,
            final String description,
            final contactapp.domain.TaskStatus status,
            final java.time.LocalDate dueDate,
            final String projectId,
            final UUID assigneeId) {
        Validation.validateNotBlank(taskId, "taskId");
        final String normalizedId = taskId.trim();

        // Validate dueDate using the injected clock for proper timezone handling
        Validation.validateOptionalDateNotPast(dueDate, "Due Date", clock);

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();

            final Optional<Task> task = jpaStore.findById(normalizedId, currentUser);
            if (task.isEmpty()) {
                return false;
            }
            final Task existing = task.get();
            existing.update(newName, description, status, dueDate, projectId, assigneeId);
            jpaStore.save(existing, currentUser);
            return true;
        }

        // Fallback for legacy in-memory store
        final Optional<Task> task = store.findById(normalizedId);
        if (task.isEmpty()) {
            return false;
        }
        final Task existing = task.get();
        existing.update(newName, description, status, dueDate, projectId, assigneeId);
        store.save(existing);
        return true;
    }

    /**
     * Returns an unmodifiable snapshot of the current task store.
     *
     * <p>Returns defensive copies of each Task to prevent external mutation
     * of internal state.
     *
     * @return unmodifiable map of defensive task copies
     */
    @Transactional(readOnly = true)
    public Map<String, Task> getDatabase() {
        return store.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Task::getTaskId,
                        Task::copy));
    }

    /**
     * Returns all tasks as a list of defensive copies for the authenticated user.
     *
     * <p>For authenticated users: returns only their tasks.
     * For ADMIN users: returns all tasks (when called from controllers with ?all=true).
     *
     * <p>Encapsulates the internal storage structure so controllers don't
     * need to access getDatabase() directly.
     *
     * @return list of task defensive copies
     */
    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findAll(currentUser).stream()
                    .map(Task::copy)
                    .toList();
        }

        return store.findAll().stream()
                .map(Task::copy)
                .toList();
    }

    /**
     * Returns all tasks across all users (ADMIN only).
     *
     * <p>This method should only be called by controllers when ?all=true
     * is specified and the authenticated user is an ADMIN. When the service is
     * still running in its legacy in-memory mode (before Spring starts), the
     * method provides the legacy behavior of returning the entire store without
     * requiring a security context.
     *
     * @return list of all task defensive copies
     * @throws AccessDeniedException if current user is not an ADMIN
     */
    @Transactional(readOnly = true)
    public List<Task> getAllTasksAllUsers() {
        if (legacyStore) {
            return store.findAll().stream()
                    .map(Task::copy)
                    .toList();
        }

        final User currentUser = getCurrentUser();
        if (!isAdmin(currentUser)) {
            throw new AccessDeniedException("Only ADMIN users can access all tasks");
        }

        return store.findAll().stream()
                .map(Task::copy)
                .toList();
    }

    /**
     * Finds a task by ID for the authenticated user.
     *
     * <p>The ID is validated and trimmed before lookup so callers can pass
     * values like " 123 " and still find the task stored as "123".
     *
     * @param taskId the task ID to search for
     * @return Optional containing a defensive copy of the task, or empty if not found
     * @throws IllegalArgumentException if taskId is null or blank
     */
    @Transactional(readOnly = true)
    public Optional<Task> getTaskById(final String taskId) {
        Validation.validateNotBlank(taskId, "taskId");
        final String trimmedId = taskId.trim();

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findById(trimmedId, currentUser).map(Task::copy);
        }

        return store.findById(trimmedId).map(Task::copy);
    }

    /**
     * Returns all tasks with the specified status for the authenticated user.
     *
     * @param status the status to filter by
     * @return list of tasks with the specified status
     * @throws IllegalArgumentException if status is null
     */
    @Transactional(readOnly = true)
    public List<Task> getTasksByStatus(final contactapp.domain.TaskStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findAll(currentUser).stream()
                    .filter(task -> status.equals(task.getStatus()))
                    .map(Task::copy)
                    .toList();
        }

        return store.findAll().stream()
                .filter(task -> status.equals(task.getStatus()))
                .map(Task::copy)
                .toList();
    }

    /**
     * Returns all tasks with a due date before the specified date for the authenticated user.
     *
     * @param date the date to compare against
     * @return list of tasks due before the specified date
     * @throws IllegalArgumentException if date is null
     */
    @Transactional(readOnly = true)
    public List<Task> getTasksDueBefore(final java.time.LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findAll(currentUser).stream()
                    .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(date))
                    .map(Task::copy)
                    .toList();
        }

        return store.findAll().stream()
                .filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(date))
                .map(Task::copy)
                .toList();
    }

    /**
     * Returns all tasks associated with the specified project for the authenticated user.
     *
     * @param projectId the project ID to filter by
     * @return list of tasks for the specified project
     * @throws IllegalArgumentException if projectId is null or blank
     */
    @Transactional(readOnly = true)
    public List<Task> getTasksByProjectId(final String projectId) {
        Validation.validateNotBlank(projectId, "projectId");
        final String trimmedProjectId = projectId.trim();

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findAll(currentUser).stream()
                    .filter(task -> trimmedProjectId.equals(task.getProjectId()))
                    .map(Task::copy)
                    .toList();
        }

        return store.findAll().stream()
                .filter(task -> trimmedProjectId.equals(task.getProjectId()))
                .map(Task::copy)
                .toList();
    }

    /**
     * Returns all tasks that have no project assigned for the authenticated user.
     *
     * <p>This corresponds to the query parameter {@code ?projectId=none} per ADR-0045.
     *
     * @return list of tasks with null projectId (unassigned to any project)
     */
    @Transactional(readOnly = true)
    public List<Task> getUnassignedTasks() {
        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findByProjectIdIsNull(currentUser).stream()
                    .map(Task::copy)
                    .toList();
        }

        return store.findAll().stream()
                .filter(task -> task.getProjectId() == null)
                .map(Task::copy)
                .toList();
    }

    /**
     * Returns all tasks assigned to the specified user for the authenticated user.
     *
     * @param assigneeId the user ID to filter by
     * @return list of tasks assigned to the specified user
     * @throws IllegalArgumentException if assigneeId is null
     */
    @Transactional(readOnly = true)
    public List<Task> getTasksByAssigneeId(final UUID assigneeId) {
        if (assigneeId == null) {
            throw new IllegalArgumentException("assigneeId must not be null");
        }

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findAll(currentUser).stream()
                    .filter(task -> assigneeId.equals(task.getAssigneeId()))
                    .map(Task::copy)
                    .toList();
        }

        return store.findAll().stream()
                .filter(task -> assigneeId.equals(task.getAssigneeId()))
                .map(Task::copy)
                .toList();
    }

    /**
     * Returns all overdue tasks for the authenticated user.
     *
     * <p>A task is considered overdue if its due date is before today and
     * its status is not DONE.
     *
     * @return list of overdue tasks
     */
    @Transactional(readOnly = true)
    public List<Task> getOverdueTasks() {
        final java.time.LocalDate today = java.time.LocalDate.now(clock);

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findAll(currentUser).stream()
                    .filter(task -> task.getDueDate() != null
                            && task.getDueDate().isBefore(today)
                            && task.getStatus() != contactapp.domain.TaskStatus.DONE)
                    .map(Task::copy)
                    .toList();
        }

        return store.findAll().stream()
                .filter(task -> task.getDueDate() != null
                        && task.getDueDate().isBefore(today)
                        && task.getStatus() != contactapp.domain.TaskStatus.DONE)
                .map(Task::copy)
                .toList();
    }

    /**
     * Archives a task by ID for the authenticated user.
     *
     * @param taskId the ID of the task to archive
     * @return Optional containing the archived task, or empty if not found
     * @throws IllegalArgumentException if taskId is null or blank
     */
    public Optional<Task> archiveTask(final String taskId) {
        Validation.validateNotBlank(taskId, "taskId");
        final String trimmedId = taskId.trim();

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            final Optional<Task> task = jpaStore.findById(trimmedId, currentUser);
            if (task.isEmpty()) {
                return Optional.empty();
            }
            final Task existing = task.get();
            existing.setArchived(true);
            jpaStore.save(existing, currentUser);
            return Optional.of(existing.copy());
        }

        final Optional<Task> task = store.findById(trimmedId);
        if (task.isEmpty()) {
            return Optional.empty();
        }
        final Task existing = task.get();
        existing.setArchived(true);
        store.save(existing);
        return Optional.of(existing.copy());
    }

    /**
     * Unarchives a task by ID for the authenticated user.
     *
     * @param taskId the ID of the task to unarchive
     * @return Optional containing the unarchived task, or empty if not found
     * @throws IllegalArgumentException if taskId is null or blank
     */
    public Optional<Task> unarchiveTask(final String taskId) {
        Validation.validateNotBlank(taskId, "taskId");
        final String trimmedId = taskId.trim();

        if (store instanceof JpaTaskStore) {
            final JpaTaskStore jpaStore = (JpaTaskStore) store;
            final User currentUser = getCurrentUser();
            final Optional<Task> task = jpaStore.findById(trimmedId, currentUser);
            if (task.isEmpty()) {
                return Optional.empty();
            }
            final Task existing = task.get();
            existing.setArchived(false);
            jpaStore.save(existing, currentUser);
            return Optional.of(existing.copy());
        }

        final Optional<Task> task = store.findById(trimmedId);
        if (task.isEmpty()) {
            return Optional.empty();
        }
        final Task existing = task.get();
        existing.setArchived(false);
        store.save(existing);
        return Optional.of(existing.copy());
    }

    void clearAllTasks() {
        store.deleteAll();
    }

    /**
     * Returns the clock used for date-based computations.
     *
     * <p>Exposed for tests to verify clock configuration and for service methods
     * that need to validate dates against the configured timezone.
     *
     * @return the configured clock
     */
    Clock getClock() {
        return clock;
    }

    /**
     * Overrides the clock used for date-based computations.
     *
     * <p>This method is package-private and intended for tests that need to simulate
     * time progression (e.g., testing overdue task detection by adding tasks, then
     * advancing the clock to make them appear overdue).
     *
     * <p>In production, prefer constructor injection of Clock for proper DI.
     *
     * @param clock the clock to use for date-based computations; null defaults to systemDefaultZone
     */
    void setClock(final Clock clock) {
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    /**
     * Factory method for tests to create a TaskService with a custom clock.
     *
     * @param store the task store
     * @param clock the clock to use for date-based computations
     * @return a TaskService configured with the specified clock
     */
    static TaskService withClock(final TaskStore store, final Clock clock) {
        return new TaskService(store, clock, true);
    }
}
