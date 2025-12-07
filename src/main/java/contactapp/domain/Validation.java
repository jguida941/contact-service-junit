package contactapp.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Date;
import java.util.regex.Pattern;

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
    // See docs/cs320-requirements/contact-requirements/requirements.md for the original constraints.

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

    /** Maximum length for Project name field. */
    public static final int MAX_PROJECT_NAME_LENGTH = 50;

    /** Maximum length for Project description field. */
    public static final int MAX_PROJECT_DESCRIPTION_LENGTH = 100;

    /** Required length for phone numbers (digits only). */
    public static final int PHONE_LENGTH = 10;

    // ==================== User Field Constants (Phase 5: Security) ====================

    /** Maximum length for username field. Allows SSO-style names while fitting UI layouts. */
    public static final int MAX_USERNAME_LENGTH = 50;

    /** Maximum length for email field. Covers long corporate aliases comfortably. */
    public static final int MAX_EMAIL_LENGTH = 100;

    /**
     * Maximum length for password hash field.
     * Bcrypt hashes are 60 chars; 255 provides headroom for future algorithms (Argon2, PBKDF2).
     */
    public static final int MAX_PASSWORD_LENGTH = 255;
    /** Minimum length for raw passwords provided during registration. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** Maximum length for role enum storage. Covers USER/ADMIN and future roles. */
    public static final int MAX_ROLE_LENGTH = 20;

    /** Maximum length for status enum storage. Covers TaskStatus/ProjectStatus values. */
    public static final int MAX_STATUS_LENGTH = 20;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$");

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
     * Validates that a String is a syntactically valid email address
     * within the configured length bounds.
     *
     * @param email the email to check
     * @param label logical field name for error messages
     */
    public static void validateEmail(final String email, final String label) {
        validateLength(email, label, 1, MAX_EMAIL_LENGTH);
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException(label + " must be a valid email address");
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
     * Validates that a String meets the length bounds and returns the trimmed value.
     *
     * <p>Use this helper when you need both validation and normalized storage.</p>
     */
    public static String validateTrimmedLength(
            final String input,
            final String label,
            final int minLength,
            final int maxLength
    ) {
        return validateTrimmedLengthInternal(input, label, minLength, maxLength, false);
    }

    /**
     * Validates length bounds but allows empty strings when {@code minLength == 0}.
     */
    public static String validateTrimmedLengthAllowBlank(
            final String input,
            final String label,
            final int minLength,
            final int maxLength
    ) {
        return validateTrimmedLengthInternal(input, label, minLength, maxLength, true);
    }

    /**
     * Convenience overload for the common case where minLength=1.
     */
    public static String validateTrimmedLength(
            final String input,
            final String label,
            final int maxLength
    ) {
        return validateTrimmedLengthInternal(input, label, 1, maxLength, false);
    }

    private static String validateTrimmedLengthInternal(
            final String input,
            final String label,
            final int minLength,
            final int maxLength,
            final boolean allowBlankWhenMinZero
    ) {
        if (input == null) {
            throw new IllegalArgumentException(label + " must not be null or blank");
        }
        final String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            if (allowBlankWhenMinZero && minLength == 0) {
                return trimmed;
            }
            throw new IllegalArgumentException(label + " must not be null or blank");
        }
        final int length = trimmed.length();
        if (length < minLength || length > maxLength) {
            final String message = String.format(
                    "%s length must be between %d and %d",
                    label, minLength, maxLength);
            throw new IllegalArgumentException(message);
        }
        return trimmed;
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

    /**
     * Validates that a LocalDate is not null and not in the past (inclusive of today).
     *
     * <p>Uses the system's default timezone to determine "today", ensuring users
     * can enter dates that are valid in their local timezone. This is the correct
     * behavior for date-only fields like task due dates where the user enters
     * a date in their local context.
     *
     * @param date  the date to validate
     * @param label logical field name
     * @return the same date for fluent usage
     * @see <a href="docs/adrs/ADR-0053-timezone-safe-date-parsing.md">ADR-0053</a>
     */
    public static LocalDate validateDateNotPast(final LocalDate date, final String label) {
        return validateDateNotPast(date, label, Clock.systemDefaultZone());
    }

    /**
     * Validates that a LocalDate is not null and not in the past using the supplied clock.
     */
    public static LocalDate validateDateNotPast(final LocalDate date, final String label, final Clock clock) {
        if (date == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        final LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) {
            throw new IllegalArgumentException(label + " must not be in the past");
        }
        return date;
    }

    /**
     * Validates an optional LocalDate (skips validation when null).
     */
    public static LocalDate validateOptionalDateNotPast(final LocalDate date, final String label) {
        if (date == null) {
            return null;
        }
        return validateDateNotPast(date, label);
    }

    /**
     * Validates an optional LocalDate using the supplied clock (skips validation when null).
     *
     * <p>This is the production-grade version that accepts an injected Clock for proper
     * timezone handling. Use this in service layers where Clock is dependency-injected.
     *
     * @param date  the date to validate (nullable)
     * @param label logical field name for error messages
     * @param clock the clock to use for determining "today"
     * @return the same date, or null if input was null
     * @throws IllegalArgumentException if date is in the past according to the clock's timezone
     */
    public static LocalDate validateOptionalDateNotPast(
            final LocalDate date, final String label, final Clock clock) {
        if (date == null) {
            return null;
        }
        return validateDateNotPast(date, label, clock);
    }

    /**
     * Validates that an enum value is not null.
     *
     * <p>This helper consolidates the null checks for enum fields like
     * {@code ProjectStatus} and {@code Role}, ensuring consistent error messages.
     *
     * @param <T>       the enum type
     * @param enumValue the enum value to check
     * @param label     logical name of the field, used in exception messages
     * @return the validated enum value (for fluent chaining)
     * @throws IllegalArgumentException if the enum value is null
     */
    public static <T extends Enum<T>> T validateNotNull(final T enumValue, final String label) {
        if (enumValue == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        return enumValue;
    }
}
