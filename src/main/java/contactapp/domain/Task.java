package contactapp.domain;

/**
 * Task domain object.
 *
 * <p>Enforces the Task requirements:
 * <ul>
 *   <li>taskId: required, length 1-10, immutable after construction</li>
 *   <li>name: required, length 1-20</li>
 *   <li>description: required, length 1-50</li>
 * </ul>
 *
 * <p>All validation is delegated to {@link Validation} so the constructor,
 * setters, and {@link #update(String, String)} stay in sync.
 *
 * <p>All fields are stored in trimmed form.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Class is {@code final} to prevent subclassing that could bypass validation</li>
 *   <li>ID is immutable after construction (stable map keys)</li>
 *   <li>{@link #update} validates both fields atomically before mutation</li>
 * </ul>
 *
 * @see Validation
 */
public final class Task {

    private static final int MIN_LENGTH = 1;
    private static final int ID_MAX_LENGTH = 10;
    private static final int NAME_MAX_LENGTH = 20;
    private static final int DESCRIPTION_MAX_LENGTH = 50;

    private final String taskId;
    private String name;
    private String description;

    /**
     * Creates a new Task with the given values.
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
        Validation.validateLength(taskId, "taskId", MIN_LENGTH, ID_MAX_LENGTH);
        this.taskId = taskId.trim();

        setName(name);
        setDescription(description);
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

    /**
     * Sets the task name after validating and trimming it.
     *
     * @param name the new task name
     * @throws IllegalArgumentException if the name is null, blank,
     *                                  or longer than 20 characters
     */
    public void setName(final String name) {
        this.name = validateAndTrim(name, "name", NAME_MAX_LENGTH);
    }

    /**
     * Sets the task description after validating and trimming it.
     *
     * @param description the new task description
     * @throws IllegalArgumentException if the description is null, blank,
     *                                  or longer than 50 characters
     */
    public void setDescription(final String description) {
        this.description = validateAndTrim(description, "description", DESCRIPTION_MAX_LENGTH);
    }

    /**
     * Updates the mutable fields atomically after validating both values.
     *
     * <p>If either value is invalid, this method throws and leaves the
     * existing name and description unchanged.
     *
     * @param newName        new task name
     * @param newDescription new task description
     * @throws IllegalArgumentException if either value violates the constraints
     */
    public void update(final String newName, final String newDescription) {
        final String validatedName = validateAndTrim(newName, "name", NAME_MAX_LENGTH);
        final String validatedDescription = validateAndTrim(newDescription, "description", DESCRIPTION_MAX_LENGTH);

        this.name = validatedName;
        this.description = validatedDescription;
    }

    /**
     * Validates that {@code value} is non-null, non-blank, and within
     * the allowed length range, then returns its trimmed form.
     *
     * <p>This relies on {@link Validation#validateLength(String, String, int, int)}
     * to enforce the null/blank/length rules so every call site stays consistent.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private static String validateAndTrim(
            final String value,
            final String label,
            final int maxLength) {
        Validation.validateLength(value, label, MIN_LENGTH, maxLength);
        return value.trim();
    }

    /**
     * Creates a defensive copy of this Task.
     *
     * <p>Validates the source state, then reuses the public constructor so
     * defensive copies and validation stay aligned.
     *
     * @return a new Task with the same field values
     * @throws IllegalArgumentException if internal state is corrupted (null fields)
     */
    public Task copy() {
        validateCopySource(this);
        return new Task(this.taskId, this.name, this.description);
    }

    /**
     * Ensures the source task has valid internal state before copying.
     */
    private static void validateCopySource(final Task source) {
        // Note: source cannot be null here because copy() passes 'this'
        if (source.taskId == null
                || source.name == null
                || source.description == null) {
            throw new IllegalArgumentException("task copy source must not be null");
        }
    }
}
