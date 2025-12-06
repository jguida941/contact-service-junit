package contactapp.domain;

import java.util.Date;

/**
 * Appointment domain object.
 *
 * <p>Enforces:
 * <ul>
 *   <li>appointmentId: required, max length 10, immutable after construction</li>
 *   <li>appointmentDate: required, not null, not in the past (java.util.Date)</li>
 *   <li>description: required, max length 50</li>
 * </ul>
 *
 * <p>String inputs are trimmed; dates are defensively copied on set/get to prevent
 * external mutation.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Class is {@code final} to prevent subclassing that could bypass validation</li>
 *   <li>ID is immutable after construction (stable map keys)</li>
 *   <li>Dates use defensive copies to prevent external mutation</li>
 *   <li>{@link #update} validates both fields atomically before mutation</li>
 * </ul>
 *
 * @see Validation
 */
public final class Appointment {
    private static final int MIN_LENGTH = 1;
    private static final int ID_MAX_LENGTH = 10;
    private static final int DESCRIPTION_MAX_LENGTH = 50;

    private final String appointmentId;
    private Date appointmentDate;
    private String description;
    private String projectId;
    private String taskId;
    private boolean archived;


    /**
     * Creates a new appointment with validated fields.
     *
     * @param appointmentId   unique identifier, length 1-10, required
     * @param appointmentDate required date, must not be null or in the past
     * @param description     required description, length 1-50
     * @throws IllegalArgumentException if any field violates the constraints
     */
    public Appointment(final String appointmentId, final Date appointmentDate, final String description) {
        // Use Validation utility for constructor field checks (validates and trims in one call)
        this.appointmentId = Validation.validateTrimmedLength(
                appointmentId, "appointmentId", MIN_LENGTH, ID_MAX_LENGTH);

        setAppointmentDate(appointmentDate);
        setDescription(description);
    }

    /**
     * Reconstitutes an Appointment from persistence with project/task associations.
     *
     * <p>Use this constructor when loading from database to preserve associations.
     * For new appointments, use the simpler constructor which omits these optional fields.
     *
     * @param appointmentId   unique identifier (required, length 1-10)
     * @param appointmentDate required date, must not be null or in the past
     * @param description     required description (length 1-50)
     * @param projectId       associated project ID (optional, nullable)
     * @param taskId          associated task ID (optional, nullable)
     * @throws IllegalArgumentException if any required argument is null or violates constraints
     */
    public Appointment(
            final String appointmentId,
            final Date appointmentDate,
            final String description,
            final String projectId,
            final String taskId) {
        this.appointmentId = Validation.validateTrimmedLength(
                appointmentId, "appointmentId", MIN_LENGTH, ID_MAX_LENGTH);

        setAppointmentDate(appointmentDate);
        setDescription(description);
        this.projectId = projectId != null ? projectId.trim() : null;
        this.taskId = taskId != null ? taskId.trim() : null;
    }

    /**
     * Atomically updates the mutable fields after validation.
     *
     * <p>If either value is invalid, this method throws and leaves the
     * existing values unchanged.
     *
     * @param newDate        new appointment date (not null, not in the past)
     * @param newDescription new description (length 1-50)
     * @throws IllegalArgumentException if either value violates the constraints
     */
    public void update(final Date newDate, final String newDescription) {
        update(newDate, newDescription, this.projectId, this.taskId);
    }

    /**
     * Atomically updates the mutable fields after validation.
     *
     * <p>If any value is invalid, this method throws and leaves the
     * existing values unchanged.
     *
     * @param newDate        new appointment date (not null, not in the past)
     * @param newDescription new description (length 1-50)
     * @param newProjectId   new project ID (nullable, can unlink from project)
     * @param newTaskId      new task ID (nullable, can unlink from task)
     * @throws IllegalArgumentException if any value violates the constraints
     */
    public void update(
            final Date newDate,
            final String newDescription,
            final String newProjectId,
            final String newTaskId) {
        // Validate both inputs before mutating state to keep the update atomic
        Validation.validateDateNotPast(newDate, "appointmentDate");
        final String validatedDescription = Validation.validateTrimmedLength(
                newDescription, "description", DESCRIPTION_MAX_LENGTH);

        this.appointmentDate = new Date(newDate.getTime());
        this.description = validatedDescription;
        this.projectId = newProjectId != null ? newProjectId.trim() : null;
        this.taskId = newTaskId != null ? newTaskId.trim() : null;
    }

    /**
     * Sets the appointment description after validation and trimming.
     *
     * <p>Independent updates are allowed; use {@link #update(Date, String)}
     * to change both fields together.
     *
     * @param description the new description
     * @throws IllegalArgumentException if description is null, blank, or too long
     */
    public void setDescription(final String description) {
        this.description = Validation.validateTrimmedLength(description, "description", DESCRIPTION_MAX_LENGTH);
    }

    /**
     * Sets the appointment date after ensuring it is not null or in the past.
     *
     * <p>Stores a defensive copy to prevent external mutation.
     */
    private void setAppointmentDate(final Date appointmentDate) {
        Validation.validateDateNotPast(appointmentDate, "appointmentDate");
        this.appointmentDate = new Date(appointmentDate.getTime());
    }

    /**
     * Returns the immutable appointment id.
     *
     * @return the appointment identifier
     */
    public String getAppointmentId() {
        return appointmentId;
    }

    /**
     * Returns a defensive copy of the appointment date.
     *
     * <p>Defensive copy prevents callers from mutating the internal date.
     *
     * @return a copy of the appointment date
     */
    public Date getAppointmentDate() {
        return new Date(appointmentDate.getTime());
    }

    /**
     * Returns the appointment description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
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
    }

    public String getTaskId() {
        return taskId;
    }

    /**
     * Sets the associated task ID.
     *
     * @param taskId the task ID to associate with (nullable, can unlink from task)
     */
    public void setTaskId(final String taskId) {
        this.taskId = taskId != null ? taskId.trim() : null;
    }

    /**
     * Returns whether this appointment is archived.
     *
     * @return true if archived, false otherwise
     */
    public boolean isArchived() {
        return archived;
    }

    /**
     * Sets the archived status of this appointment.
     *
     * @param archived true to archive, false to unarchive
     */
    public void setArchived(final boolean archived) {
        this.archived = archived;
    }

    /**
     * Returns whether this appointment's date is in the past.
     *
     * <p>Useful for UI to display visual indicators for past appointments.
     *
     * @return true if the appointment date is before the current time
     */
    public boolean isPast() {
        return appointmentDate.before(new Date());
    }

    /**
     * Creates a defensive copy of this Appointment.
     *
     * <p>Uses reconstitution to preserve existing appointments that may have past
     * dates (appointments naturally become "past" over time).
     *
     * @return a new Appointment with the same field values
     * @throws IllegalArgumentException if internal state is corrupted
     */
    public Appointment copy() {
        validateCopySource(this);
        return reconstitute(
                this.appointmentId,
                new Date(this.appointmentDate.getTime()),
                this.description,
                this.projectId,
                this.taskId,
                this.archived);
    }

    /**
     * Reconstitutes an Appointment from persistence without applying the "not in past" rule.
     *
     * <p>This factory method is used when loading existing appointments from the database.
     * The "appointmentDate must not be in the past" rule only applies to NEW appointments;
     * existing appointments naturally become "past" over time and should still be readable,
     * archivable, and deletable.
     *
     * @param appointmentId   unique identifier (required, length 1-10)
     * @param appointmentDate the appointment date (required, may be in the past for existing records)
     * @param description     required description (length 1-50)
     * @param projectId       associated project ID (optional, nullable)
     * @param taskId          associated task ID (optional, nullable)
     * @param archived        whether the appointment is archived
     * @return the reconstituted Appointment
     * @throws IllegalArgumentException if required fields are null or violate length constraints
     */
    public static Appointment reconstitute(
            final String appointmentId,
            final Date appointmentDate,
            final String description,
            final String projectId,
            final String taskId,
            final boolean archived) {
        // Validate ID and description lengths, but NOT the past-date rule
        final String validatedId = Validation.validateTrimmedLength(
                appointmentId, "appointmentId", MIN_LENGTH, ID_MAX_LENGTH);
        if (appointmentDate == null) {
            throw new IllegalArgumentException("appointmentDate must not be null");
        }
        final String validatedDesc = Validation.validateTrimmedLength(
                description, "description", MIN_LENGTH, DESCRIPTION_MAX_LENGTH);

        // Use private constructor that bypasses past-date validation
        return new Appointment(
                validatedId,
                new Date(appointmentDate.getTime()),
                validatedDesc,
                projectId != null ? projectId.trim() : null,
                taskId != null ? taskId.trim() : null,
                archived);
    }

    /**
     * Private constructor for reconstitution that bypasses date validation.
     *
     * <p>This constructor is only called from {@link #reconstitute}, which handles
     * loading existing appointments from the database. Since appointments naturally
     * become "past" over time, we skip the "not in past" validation here.
     *
     * @param appointmentId   validated ID
     * @param appointmentDate date (defensive copy already made by caller)
     * @param description     validated description
     * @param projectId       optional project ID
     * @param taskId          optional task ID
     * @param archived        archived status
     */
    private Appointment(
            final String appointmentId,
            final Date appointmentDate,
            final String description,
            final String projectId,
            final String taskId,
            final boolean archived) {
        this.appointmentId = appointmentId;
        this.appointmentDate = appointmentDate;
        this.description = description;
        this.projectId = projectId;
        this.taskId = taskId;
        this.archived = archived;
    }

    /**
     * Ensures the source appointment has valid internal state before copying.
     */
    private static void validateCopySource(final Appointment source) {
        // Note: source cannot be null here because copy() passes 'this'
        if (source.appointmentId == null
                || source.appointmentDate == null
                || source.description == null) {
            throw new IllegalArgumentException("appointment copy source must not be null");
        }
    }
}
