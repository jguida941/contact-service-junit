package contactapp.api.exception;

/**
 * Thrown when attempting to create a resource with an ID that already exists.
 *
 * <p>This exception is caught by {@link contactapp.api.GlobalExceptionHandler}
 * and converted to an HTTP 409 Conflict response with a JSON error body.
 *
 * @see contactapp.api.GlobalExceptionHandler
 */
public class DuplicateResourceException extends RuntimeException {

    /**
     * Creates a new DuplicateResourceException with the given message.
     *
     * @param message descriptive message indicating which resource ID is duplicated
     */
    public DuplicateResourceException(final String message) {
        super(message);
    }
}
