package contactapp.domain;

import java.time.Clock;
import java.util.Date;

/**
 * Utility methods for validating domain fields (Contact, Task, etc.).
 *
 * <p>All methods throw {@link IllegalArgumentException} when a value violates
 * the requirements, keeping validation logic centralized so constructors
 * and setters stay consistent.
 *
 * <h2>Why centralized validation?</h2>
 * <ul>
 *   <li>Single source of truth for all validation rules</li>
 *   <li>Consistent error messages across all domain objects</li>
 *   <li>Easy to maintain and extend for new domains</li>
 *   <li>Tests can assert exact exception messages</li>
 * </ul>
 *
 * @see Contact
 * @see Task
 * @see Appointment
 */
public final class Validation {

    // ==================== Field Length Constants ====================
    // NOTE: These bounds follow the CS320 Milestone 1 assignment spec, not real-world limits.
    // See docs/requirements/contact-requirements/requirements.md for the original constraints.

    /** Maximum length for ID fields (Contact, Task, Appointment). */
    public static final int MAX_ID_LENGTH = 10;

    /** Maximum length for Contact firstName and lastName fields. */
    public static final int MAX_NAME_LENGTH = 10;

    /** Maximum length for Contact address field. */
    public static final int MAX_ADDRESS_LENGTH = 30;

    /** Maximum length for Task name field. */
    public static final int MAX_TASK_NAME_LENGTH = 20;

    /** Maximum length for description fields (Task, Appointment). */
    public static final int MAX_DESCRIPTION_LENGTH = 50;

    /** Required length for phone numbers (digits only). */
    public static final int PHONE_LENGTH = 10;

    private Validation() {
        // Utility class, not meant to be instantiated
    }

    /**
     * Validates that a String is not null, empty, or only whitespace.
     *
     * @param input the value to check
     * @param label logical name of the field, used in exception messages
     * @throws IllegalArgumentException if input is null, empty, or blank
     */
    public static void validateNotBlank(final String input, final String label) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(label + " must not be null or blank");
        }
    }

    /**
     * Validates that a String has a length within the given bounds.
     * Also ensures the value is not null or blank.
     *
     * @param input     the value to check
     * @param label     logical name of the field, used in exception messages
     * @param minLength inclusive minimum length
     * @param maxLength inclusive maximum length
     * @throws IllegalArgumentException if input is null/blank or outside [minLength, maxLength]
     */
    public static void validateLength(
            final String input,
            final String label,
            final int minLength,
            final int maxLength) {
        validateNotBlank(input, label);
        final int length = input.trim().length();
        if (length < minLength || length > maxLength) {
            final String message = String.format(
                    "%s length must be between %d and %d",
                    label, minLength, maxLength);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Validates that a String consists only of digits and has the specified length.
     * Also ensures the value is not null or blank.
     *
     * @param input          the value to check
     * @param label          logical name of the field, used in exception messages
     * @param requiredLength exact length required (e.g., 10 for phone numbers)
     * @throws IllegalArgumentException if input is null/blank, contains non-digits, or wrong length
     */
    public static void validateDigits(final String input, final String label, final int requiredLength) {
        validateNotBlank(input, label);
        if (!input.matches("\\d+")) {
            throw new IllegalArgumentException(label + " must only contain digits 0-9");
        }
        if (input.length() != requiredLength) {
            throw new IllegalArgumentException(label + " must be exactly " + requiredLength + " digits");
        }
    }

    /**
     * Validates that a date is not null and not in the past.
     *
     * <p>A date equal to "now" (within the same millisecond) is considered valid
     * to avoid flaky behavior at the boundary.
     *
     * <p><strong>Timezone note:</strong> This method uses {@link Clock#systemUTC()} by default,
     * so "now" is evaluated in UTC. If the caller's local timezone differs significantly,
     * an appointment scheduled for "now" in local time could appear to be in the past
     * (or future) when compared against UTC. Callers should ensure dates are constructed
     * with UTC awareness or use the overload that accepts a custom {@link Clock}.
     *
     * @param date  the date to check
     * @param label logical name of the field, used in exception messages
     * @throws IllegalArgumentException if the date is null or strictly earlier than now
     */
    public static void validateDateNotPast(final Date date, final String label) {
        validateDateNotPast(date, label, Clock.systemUTC());
    }

    /**
     * Validates that a date is not null and not in the past, using the provided clock.
     *
     * <p>This overload allows tests to inject a fixed clock for deterministic testing
     * of boundary conditions.
     *
     * @param date  the date to check
     * @param label logical name of the field, used in exception messages
     * @param clock the clock to use for determining "now"
     * @throws IllegalArgumentException if the date is null or strictly earlier than now
     */
    public static void validateDateNotPast(final Date date, final String label, final Clock clock) {
        if (date == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        // Use millisecond comparison; strictly less than now is "in the past"
        if (date.getTime() < clock.millis()) {
            throw new IllegalArgumentException(label + " must not be in the past");
        }
    }
}
