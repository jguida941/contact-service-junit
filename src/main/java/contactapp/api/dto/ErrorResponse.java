package contactapp.api.dto;

/**
 * Standard error response format for REST API errors.
 *
 * <p>Returns a consistent JSON structure per ADR-0016:
 * {@code {"message": "error description"}}
 *
 * <p>Used by {@link contactapp.api.GlobalExceptionHandler} to wrap
 * all error responses in a uniform format.
 *
 * @param message the error message to display to the client
 */
public record ErrorResponse(String message) {
}
