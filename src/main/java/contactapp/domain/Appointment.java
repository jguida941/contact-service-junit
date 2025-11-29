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


    /**
     * Creates a new appointment with validated fields.
     *
     * @param appointmentId   unique identifier, length 1-10, required
     * @param appointmentDate required date, must not be null or in the past
     * @param description     required description, length 1-50
     * @throws IllegalArgumentException if any field violates the constraints
     */
    public Appointment(final String appointmentId, final Date appointmentDate, final String description) {
        // Use Validation utility for constructor field checks (matches Contact/Task pattern)
        Validation.validateLength(appointmentId, "appointmentId", MIN_LENGTH, ID_MAX_LENGTH);
        this.appointmentId = appointmentId.trim();

        setAppointmentDate(appointmentDate);
        setDescription(description);
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
        // Validate both inputs before mutating state to keep the update atomic
        Validation.validateDateNotPast(newDate, "appointmentDate");
        Validation.validateLength(newDescription, "description", MIN_LENGTH, DESCRIPTION_MAX_LENGTH);

        final Date copiedDate = new Date(newDate.getTime());
        final String trimmedDescription = newDescription.trim();

        this.appointmentDate = copiedDate;
        this.description = trimmedDescription;
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
        Validation.validateLength(description, "description", MIN_LENGTH, DESCRIPTION_MAX_LENGTH);
        this.description = description.trim();
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

    /**
     * Creates a defensive copy of this Appointment.
     *
     * <p>Validates the source state, then reuses the public constructor so
     * defensive copies and validation stay aligned.
     *
     * @return a new Appointment with the same field values
     * @throws IllegalArgumentException if internal state is corrupted
     */
    public Appointment copy() {
        validateCopySource(this);
        return new Appointment(this.appointmentId, new Date(this.appointmentDate.getTime()), this.description);
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
