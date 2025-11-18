package contactapp;

/**
 * Utility methods for validating contact fields.
 * <p>
 * All methods throw {@link IllegalArgumentException} when a value
 * violates the contact requirements. This keeps validation logic
 * in one place so both the constructor and setters can reuse it.
 */
public final class Validation {

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
    public static void validateNotBlank(String input, String label) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(label + " must not be null or blank");
        }
    }

    /**
     * Validates that a String has a length within the given bounds.
     * Also ensures the value is not null or blank.
     *
     * @param input      the value to check
     * @param label      logical name of the field, used in exception messages
     * @param minLength  inclusive minimum length
     * @param maxLength  inclusive maximum length
     * @throws IllegalArgumentException if input is null/blank or outside [minLength, maxLength]
     */
    public static void validateLength(String input, String label, int minLength, int maxLength) {
        validateNotBlank(input, label);
        int length = input.length();
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(
                    label + " length must be between " + minLength + " and " + maxLength);
        }
    }

    /**
     * Validates that a String consists only of digits and is exactly 10 characters long.
     * Also ensures the value is not null or blank.
     *
     * @param input the value to check
     * @param label logical name of the field, used in exception messages
     * @throws IllegalArgumentException if input is null/blank,
     *                                  contains non digits, or is not length 10
     */
    public static void validateNumeric10(String input, String label) {
        validateNotBlank(input, label);
        if (!input.matches("\\d+")) {
            throw new IllegalArgumentException(label + " must only contain digits 0-9");
        }
        if (input.length() != 10) {
            throw new IllegalArgumentException(label + " must be exactly 10 digits");
        }
    }
}