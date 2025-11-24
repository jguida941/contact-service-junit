package contactapp;

import java.util.Date;

/**
 * Appointment domain object.
 *
 * Enforces:
 *  - appointmentId: required, max length 10, immutable after construction
 *  - appointmentDate: required, not null, not in the past (java.util.Date)
 *  - description: required, max length 50
 *
 * String inputs are trimmed; dates are defensively copied on set/get to prevent
 * external mutation.
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
     */
    public Appointment(final String appointmentId, final Date appointmentDate, final String description) {
        Validation.validateNotBlank(appointmentId, "appointmentId");
        final String trimmedId = appointmentId.trim();
        Validation.validateLength(trimmedId, "appointmentId", MIN_LENGTH, ID_MAX_LENGTH);
        this.appointmentId = trimmedId;

        setAppointmentDate(appointmentDate);
        setDescription(description);
    }

    /**
     * Atomically updates the mutable fields after validation.
     *
     * @param newDate        new appointment date (not null, not in the past)
     * @param newDescription new description (length 1-50)
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
     * Independent updates are allowed; use {@link #update(Date, String)} to change both fields together.
     */
    public void setDescription(final String description) {
        Validation.validateLength(description, "description", MIN_LENGTH, DESCRIPTION_MAX_LENGTH);
        this.description = description.trim();
    }

    /**
     * Sets the appointment date after ensuring it is not null or in the past.
     * Stores a defensive copy to prevent external mutation.
     */
    private void setAppointmentDate(final Date appointmentDate) {
        Validation.validateDateNotPast(appointmentDate, "appointmentDate");
        this.appointmentDate = new Date(appointmentDate.getTime());
    }

    /**
     * Returns the immutable appointment id.
     */
    public String getAppointmentId() {
        return appointmentId;
    }

    /**
     * Returns a defensive copy of the appointment date.
     */
    public Date getAppointmentDate() {
        return new Date(appointmentDate.getTime());
    }

    /**
     * Returns the appointment description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Creates a defensive copy of this Appointment.
     */
    Appointment copy() {
        // Validate the source state, then reuse the public constructor so defensive copies and validation stay aligned.
        validateCopySource(this);
        return new Appointment(this.appointmentId, new Date(this.appointmentDate.getTime()), this.description);
    }

    private static void validateCopySource(final Appointment source) {
        if (source == null
                || source.appointmentId == null
                || source.appointmentDate == null
                || source.description == null) {
            throw new IllegalArgumentException("appointment copy source must not be null");
        }
    }

}
