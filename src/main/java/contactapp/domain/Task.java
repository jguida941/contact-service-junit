package contactapp.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Task domain object.
 *
 * <p>Enforces the Task requirements:
 * <ul>
 *   <li>taskId: required, length 1-10, immutable after construction</li>
 *   <li>name: required, length 1-20</li>
 *   <li>description: required, length 1-50</li>
 *   <li>status: required, valid TaskStatus enum value (defaults to TODO)</li>
 *   <li>dueDate: optional, nullable LocalDate</li>
 *   <li>createdAt: required, set on creation</li>
 *   <li>updatedAt: required, set on update</li>
 * </ul>
 *
 * <p>All validation is delegated to {@link Validation} so the constructor,
 * setters, and {@link #update(String, String, TaskStatus, LocalDate)} stay in sync.
 *
 * <p>All fields are stored in trimmed form.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Class is {@code final} to prevent subclassing that could bypass validation</li>
 *   <li>ID is immutable after construction (stable map keys)</li>
 *   <li>{@link #update} validates all fields atomically before mutation</li>
 * </ul>
 *
 * @see Validation
 * @see TaskStatus
 */
public final class Task {

    private static final int MIN_LENGTH = 1;
    private static final int ID_MAX_LENGTH = 10;
    private static final int NAME_MAX_LENGTH = 20;
    private static final int DESCRIPTION_MAX_LENGTH = 50;

    private final String taskId;
    private String name;
    private String description;
    private TaskStatus status;
    private LocalDate dueDate;
    private String projectId;
    private UUID assigneeId;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Creates a new Task with the given values.
     *
     * @param taskId      unique identifier (required, length 1-10)
     * @param name        task name (required, length 1-20)
     * @param description task description (required, length 1-50)
     * @param status      task status (required, valid TaskStatus, defaults to TODO if null)
     * @param dueDate     due date (optional, nullable)
     * @throws IllegalArgumentException if any argument violates the constraints
     */
    public Task(
            final String taskId,
            final String name,
            final String description,
            final TaskStatus status,
            final LocalDate dueDate) {
        // Use Validation utility (validates and trims in one call)
        this.taskId = Validation.validateTrimmedLength(taskId, "Task ID", MIN_LENGTH, ID_MAX_LENGTH);

        setName(name);
        setDescription(description);
        setStatus(status);
        setDueDate(dueDate);

        final Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates a new Task with the given values and default status (TODO).
     *
     * @param taskId      unique identifier (required, length 1-10)
     * @param name        task name (required, length 1-20)
     * @param description task description (required, length 1-50)
     * @throws IllegalArgumentException if any argument violates the constraints
     */
    public Task(
            final String taskId,
            final String name,
            final String description) {
        this(taskId, name, description, TaskStatus.TODO, null);
    }

    /**
     * Reconstitutes a Task from persistence with existing timestamps.
     *
     * <p>Use this constructor when loading from database to preserve the original
     * creation and update timestamps. For new tasks, use the other constructors
     * which generate fresh timestamps.
     *
     * @param taskId      unique identifier (required, length 1-10)
     * @param name        task name (required, length 1-20)
     * @param description task description (required, length 1-50)
     * @param status      task status (required, defaults to TODO if null)
     * @param dueDate     due date (optional, nullable)
     * @param projectId   associated project ID (optional, nullable)
     * @param assigneeId  assigned user ID (optional, nullable)
     * @param createdAt   timestamp when originally created (required)
     * @param updatedAt   timestamp when last updated (required)
     * @throws IllegalArgumentException if any required argument is null or violates constraints
     */
    public Task(
            final String taskId,
            final String name,
            final String description,
            final TaskStatus status,
            final LocalDate dueDate,
            final String projectId,
            final UUID assigneeId,
            final Instant createdAt,
            final Instant updatedAt) {
        this.taskId = Validation.validateTrimmedLength(taskId, "Task ID", MIN_LENGTH, ID_MAX_LENGTH);

        setName(name);
        setDescription(description);
        setStatus(status);
        setDueDate(dueDate);
        this.projectId = projectId != null ? projectId.trim() : null;
        this.assigneeId = assigneeId;

        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getProjectId() {
        return projectId;
    }

    /**
     * Sets the associated project ID.
     *
     * @param projectId the project ID to associate with (nullable, can unlink from project)
     */
    public void setProjectId(final String projectId) {
        this.projectId = projectId != null ? projectId.trim() : null;
        this.updatedAt = Instant.now();
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    /**
     * Sets the assignee user ID.
     *
     * @param assigneeId the user ID to assign the task to (nullable, can unassign)
     */
    public void setAssigneeId(final UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the task name after validating and trimming it.
     *
     * @param name the new task name
     * @throws IllegalArgumentException if the name is null, blank,
     *                                  or longer than 20 characters
     */
    public void setName(final String name) {
        this.name = normalizeTaskName(name);
    }

    /**
     * Sets the task description after validating and trimming it.
     *
     * @param description the new task description
     * @throws IllegalArgumentException if the description is null, blank,
     *                                  or longer than 50 characters
     */
    public void setDescription(final String description) {
        this.description = normalizeTaskDescription(description);
    }

    /**
     * Sets the task status.
     *
     * @param status the new task status (defaults to TODO if null)
     */
    public void setStatus(final TaskStatus status) {
        this.status = (status == null) ? TaskStatus.TODO : status;
    }

    /**
     * Sets the task due date.
     *
     * <p>Note: The "due date must not be in the past" business rule is enforced
     * at the service layer where the Clock is properly injected. This setter
     * only stores the value.
     *
     * @param dueDate the new due date (nullable)
     * @see contactapp.service.TaskService
     */
    public void setDueDate(final LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    /**
     * Updates the mutable fields atomically after validating all values.
     *
     * <p>If any value is invalid, this method throws and leaves the
     * existing state unchanged.
     *
     * @param newName        new task name
     * @param newDescription new task description
     * @param newStatus      new task status (defaults to TODO if null)
     * @param newDueDate     new due date (nullable)
     * @throws IllegalArgumentException if any value violates the constraints
     */
    public void update(
            final String newName,
            final String newDescription,
            final TaskStatus newStatus,
            final LocalDate newDueDate) {
        update(newName, newDescription, newStatus, newDueDate, this.projectId, this.assigneeId);
    }

    /**
     * Updates the mutable fields atomically after validating all values.
     *
     * <p>If any value is invalid, this method throws and leaves the
     * existing state unchanged.
     *
     * @param newName        new task name
     * @param newDescription new task description
     * @param newStatus      new task status (defaults to TODO if null)
     * @param newDueDate     new due date (nullable)
     * @param newProjectId   new project ID (nullable, can unlink from project)
     * @throws IllegalArgumentException if any value violates the constraints
     */
    public void update(
            final String newName,
            final String newDescription,
            final TaskStatus newStatus,
            final LocalDate newDueDate,
            final String newProjectId) {
        update(newName, newDescription, newStatus, newDueDate, newProjectId, this.assigneeId);
    }

    /**
     * Updates the mutable fields atomically after validating all values.
     *
     * <p>If any value is invalid, this method throws and leaves the
     * existing state unchanged.
     *
     * @param newName        new task name
     * @param newDescription new task description
     * @param newStatus      new task status (defaults to TODO if null)
     * @param newDueDate     new due date (nullable)
     * @param newProjectId   new project ID (nullable, can unlink from project)
     * @param newAssigneeId  new assignee user ID (nullable, can unassign)
     * @throws IllegalArgumentException if any value violates the constraints
     */
    public void update(
            final String newName,
            final String newDescription,
            final TaskStatus newStatus,
            final LocalDate newDueDate,
            final String newProjectId,
            final UUID newAssigneeId) {
        // Validate all incoming values before mutating state so the update is all-or-nothing
        final String validatedName = normalizeTaskName(newName);
        final String validatedDescription = normalizeTaskDescription(newDescription);
        final TaskStatus validatedStatus = (newStatus == null) ? TaskStatus.TODO : newStatus;

        this.name = validatedName;
        this.description = validatedDescription;
        this.status = validatedStatus;
        // Note: "due date must not be in the past" is validated at service layer with injected Clock
        this.dueDate = newDueDate;
        this.projectId = newProjectId != null ? newProjectId.trim() : null;
        this.assigneeId = newAssigneeId;
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the mutable fields atomically after validating both values.
     *
     * <p>If either value is invalid, this method throws and leaves the
     * existing name and description unchanged.
     *
     * <p>This is a convenience method that preserves backward compatibility
     * by keeping the existing status and dueDate unchanged.
     *
     * @param newName        new task name
     * @param newDescription new task description
     * @throws IllegalArgumentException if either value violates the constraints
     */
    public void update(final String newName, final String newDescription) {
        update(newName, newDescription, this.status, this.dueDate);
    }

    private static String normalizeTaskName(final String value) {
        return Validation.validateTrimmedLength(value, "name", NAME_MAX_LENGTH);
    }

    private static String normalizeTaskDescription(final String value) {
        return Validation.validateTrimmedLength(value, "description", DESCRIPTION_MAX_LENGTH);
    }

     /**
     * Returns whether this task is archived.
     *
     * @return true if archived, false otherwise
     */
    public boolean isArchived() {
        return archived;
    }

    /**
     * Sets the archived status of this task.
     *
     * @param archived true to archive, false to unarchive
     */
    public void setArchived(final boolean archived) {
        this.archived = archived;
        this.updatedAt = Instant.now();
    }

    /**
     * Returns whether this task is overdue.
     *
     * <p>A task is considered overdue if it has a due date and that date is before today.
     * Tasks with no due date (null) are never considered overdue.
     *
     * @return true if the task has a due date that is in the past, false otherwise
     */
    public boolean isOverdue() {
        if (dueDate == null) {
            return false;
        }
        return dueDate.isBefore(LocalDate.now());
    }

    /**
     * Creates a defensive copy of this Task.
     *
     * <p>Uses reconstitution to preserve existing tasks that may have past
     * due dates (tasks naturally become "overdue" over time).
     *
     * @return a new Task with the same field values
     * @throws IllegalArgumentException if internal state is corrupted (null fields)
     */
    public Task copy() {
        validateCopySource(this);
        return reconstitute(
                this.taskId,
                this.name,
                this.description,
                this.status,
                this.dueDate,
                this.projectId,
                this.assigneeId,
                this.createdAt,
                this.updatedAt,
                this.archived);
    }

    /**
     * Reconstitutes a Task from persistence without applying the "not in past" rule.
     *
     * <p>This factory method is used when loading existing tasks from the database.
     * The "dueDate must not be in the past" rule only applies to NEW tasks;
     * existing tasks naturally become "overdue" over time and should still be readable,
     * updatable, and deletable.
     *
     * @param taskId      unique identifier (required, length 1-10)
     * @param name        task name (required, length 1-20)
     * @param description task description (required, length 1-50)
     * @param status      task status (required, defaults to TODO if null)
     * @param dueDate     due date (optional, nullable, may be in the past for existing records)
     * @param projectId   associated project ID (optional, nullable)
     * @param assigneeId  assigned user ID (optional, nullable)
     * @param createdAt   timestamp when originally created (required)
     * @param updatedAt   timestamp when last updated (required)
     * @return the reconstituted Task
     * @throws IllegalArgumentException if required fields are null or violate length constraints
     */
    public static Task reconstitute(
            final String taskId,
            final String name,
            final String description,
            final TaskStatus status,
            final LocalDate dueDate,
            final String projectId,
            final UUID assigneeId,
            final Instant createdAt,
            final Instant updatedAt) {
        return reconstitute(
                taskId,
                name,
                description,
                status,
                dueDate,
                projectId,
                assigneeId,
                createdAt,
                updatedAt,
                false);
    }

    /**
     * Reconstitutes a Task from persistence without applying the "not in past" rule.
     *
     * <p>This factory method is used when loading existing tasks from the database.
     * The "dueDate must not be in the past" rule only applies to NEW tasks;
     * existing tasks naturally become "overdue" over time and should still be readable,
     * updatable, and deletable.
     *
     * @param taskId      unique identifier (required, length 1-10)
     * @param name        task name (required, length 1-20)
     * @param description task description (required, length 1-50)
     * @param status      task status (required, defaults to TODO if null)
     * @param dueDate     due date (optional, nullable, may be in the past for existing records)
     * @param projectId   associated project ID (optional, nullable)
     * @param assigneeId  assigned user ID (optional, nullable)
     * @param createdAt   timestamp when originally created (required)
     * @param updatedAt   timestamp when last updated (required)
     * @param archived    whether the task is archived
     * @return the reconstituted Task
     * @throws IllegalArgumentException if required fields are null or violate length constraints
     */
    public static Task reconstitute(
            final String taskId,
            final String name,
            final String description,
            final TaskStatus status,
            final LocalDate dueDate,
            final String projectId,
            final UUID assigneeId,
            final Instant createdAt,
            final Instant updatedAt,
            final boolean archived) {
        // Validate ID, name, description, and timestamps, but NOT the past-date rule for dueDate
        final String validatedId = Validation.validateTrimmedLength(taskId, "Task ID", MIN_LENGTH, ID_MAX_LENGTH);
        final String validatedName = normalizeTaskName(name);
        final String validatedDesc = normalizeTaskDescription(description);
        final TaskStatus validatedStatus = (status == null) ? TaskStatus.TODO : status;

        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }

        // Use private constructor that bypasses past-date validation
        return new Task(
                validatedId,
                validatedName,
                validatedDesc,
                validatedStatus,
                dueDate,
                projectId != null ? projectId.trim() : null,
                assigneeId,
                createdAt,
                updatedAt,
                archived);
    }

    /**
     * Private constructor for reconstitution that bypasses date validation.
     *
     * <p>This constructor is only called from {@link #reconstitute}, which handles
     * loading existing tasks from the database. Since tasks may naturally have
     * past due dates over time, we skip validation here.
     *
     * @param taskId              validated task ID
     * @param name                validated task name
     * @param description         validated task description
     * @param status              validated task status
     * @param dueDate             due date (optional, may be in past)
     * @param projectId           optional project ID
     * @param assigneeId          optional assignee user ID
     * @param createdAt           creation timestamp
     * @param updatedAt           update timestamp
     * @param archived            whether the task is archived
     */
    private Task(
            final String taskId,
            final String name,
            final String description,
            final TaskStatus status,
            final LocalDate dueDate,
            final String projectId,
            final UUID assigneeId,
            final Instant createdAt,
            final Instant updatedAt,
            final boolean archived) {
        this.taskId = taskId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate; // Pre-validated by caller (reconstitute)
        this.projectId = projectId;
        this.assigneeId = assigneeId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archived = archived;
    }

    /**
     * Ensures the source task has valid internal state before copying.
     */
    private static void validateCopySource(final Task source) {
        // Note: source cannot be null here because copy() passes 'this'
        if (source.taskId == null
                || source.name == null
                || source.description == null
                || source.status == null
                || source.createdAt == null
                || source.updatedAt == null) {
            throw new IllegalArgumentException("task copy source must not be null");
        }
    }
}
