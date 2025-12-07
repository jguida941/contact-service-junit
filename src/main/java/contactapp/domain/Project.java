package contactapp.domain;

/**
 * Project domain object.
 *
 * <p>Enforces all field constraints from the requirements:
 * <ul>
 *   <li>projectId: non-null, length 1-10, not updatable</li>
 *   <li>name: non-null, length 1-50</li>
 *   <li>description: non-null, length 0-100</li>
 *   <li>status: non-null, valid ProjectStatus enum value</li>
 * </ul>
 *
 * <p>All violations result in {@link IllegalArgumentException} being thrown
 * by the underlying {@link Validation} helper.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Class is {@code final} to prevent subclassing that could bypass validation</li>
 *   <li>ID is immutable after construction (stable map keys)</li>
 *   <li>All inputs are trimmed before storage for consistent normalization</li>
 *   <li>{@link #update} validates all fields atomically before mutation</li>
 * </ul>
 *
 * @see Validation
 * @see ProjectStatus
 */
public final class Project {
    private static final int MIN_LENGTH = 1;
    private static final int ID_MAX_LENGTH = 10;
    private static final int NAME_MAX_LENGTH = Validation.MAX_PROJECT_NAME_LENGTH;
    private static final int DESCRIPTION_MIN_LENGTH = 0;
    private static final int DESCRIPTION_MAX_LENGTH = Validation.MAX_PROJECT_DESCRIPTION_LENGTH;

    private final String projectId;
    private String name;
    private String description;
    private ProjectStatus status;

    /**
     * Creates a new Project with the given values.
     *
     * @param projectId   unique identifier (required, length 1-10, immutable)
     * @param name        project name (required, length 1-50)
     * @param description project description (required, length 0-100)
     * @param status      project status (required, valid ProjectStatus)
     * @throws IllegalArgumentException if any field violates the Project constraints
     */
    public Project(
            final String projectId,
            final String name,
            final String description,
            final ProjectStatus status) {

        // Use Validation utility for constructor field checks (validates and trims in one call)
        this.projectId = Validation.validateTrimmedLength(projectId, "Project ID", MIN_LENGTH, ID_MAX_LENGTH);

        // Reuse setter validation for the mutable fields
        setName(name);
        setDescription(description);
        setStatus(status);
    }

    // Getters
    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    // Setters
    public void setName(final String name) {
        this.name = normalizeProjectName(name);
    }

    public void setDescription(final String description) {
        this.description = normalizeProjectDescription(description);
    }

    public void setStatus(final ProjectStatus status) {
        this.status = Validation.validateNotNull(status, "status");
    }

    /**
     * Updates all mutable fields after validating every new value first.
     *
     * <p>If any value fails validation, nothing is changed, so callers never
     * see a partially updated project. (Atomic update behavior.)
     *
     * @param newName        new project name
     * @param newDescription new project description
     * @param newStatus      new project status
     * @throws IllegalArgumentException if any new value violates the Project constraints
     */
    public void update(
            final String newName,
            final String newDescription,
            final ProjectStatus newStatus) {
        // Validate all incoming values before mutating state so the update is all-or-nothing
        final String validatedName = normalizeProjectName(newName);
        final String validatedDescription = normalizeProjectDescription(newDescription);
        final ProjectStatus validatedStatus = Validation.validateNotNull(newStatus, "status");

        this.name = validatedName;
        this.description = validatedDescription;
        this.status = validatedStatus;
    }

    /**
     * Creates a defensive copy of this Project.
     *
     * <p>Validates the source state, then reuses the public constructor so
     * defensive copies and validation stay aligned.
     *
     * @return a new Project with the same field values
     * @throws IllegalArgumentException if internal state is corrupted (null fields)
     */
    public Project copy() {
        validateCopySource(this);
        return new Project(this.projectId, this.name, this.description, this.status);
    }

    /**
     * Ensures the source project has valid internal state before copying.
     */
    private static void validateCopySource(final Project source) {
        // Note: source cannot be null here because copy() passes 'this'
        if (source.projectId == null
                || source.name == null
                || source.description == null
                || source.status == null) {
            throw new IllegalArgumentException("project copy source must not be null");
        }
    }

    private static String normalizeProjectName(final String value) {
        return Validation.validateTrimmedLength(value, "name", NAME_MAX_LENGTH);
    }

    private static String normalizeProjectDescription(final String value) {
        return Validation.validateTrimmedLengthAllowBlank(
                value,
                "description",
                DESCRIPTION_MIN_LENGTH,
                DESCRIPTION_MAX_LENGTH);
    }
}
