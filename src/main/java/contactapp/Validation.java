package contactapp;

/**
 * Utility methods for validating contact fields.
 */
public final class Validation {
    private Validation() {
        // Utility class
    }
    // Checks that input is not null or blank
    public static void validateNotBlank(String input, String label) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException(label + " must not be null or blank");
        }
    }

    // Checks that input length is within specified bounds
    public static void validateLength(String input, String label, int minLength, int maxLength) {
        validateNotBlank(input, label);
        int length = input.length();
        if (length < minLength || length > maxLength) {
            throw new IllegalArgumentException(
                    label + " length must be between " + minLength + " and " + maxLength);
        }
    }

    // Checks that input is numeric and exactly 10 digits
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
