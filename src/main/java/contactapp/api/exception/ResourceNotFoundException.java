package contactapp.api.exception;

/**
 * Thrown when a requested resource (Contact, Task, Appointment) is not found.
 *
 * <p>This exception is caught by {@link contactapp.api.GlobalExceptionHandler}
 * and converted to an HTTP 404 Not Found response with a JSON error body.
 *
 * @see contactapp.api.GlobalExceptionHandler
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates a new ResourceNotFoundException with the given message.
     *
     * @param message descriptive message indicating which resource was not found
     */
    public ResourceNotFoundException(final String message) {
        super(message);
    }
}
