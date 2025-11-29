package contactapp.api;

import contactapp.api.dto.ErrorResponse;
import contactapp.api.exception.DuplicateResourceException;
import contactapp.api.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for consistent API error responses.
 *
 * <p>Converts exceptions to HTTP responses with JSON body format:
 * {@code {"message": "error description"}} per ADR-0016.
 *
 * <h2>Exception Mapping</h2>
 * <ul>
 *   <li>{@link IllegalArgumentException} → 400 Bad Request (domain validation)</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (Bean Validation)</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 Bad Request (malformed JSON)</li>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found</li>
 *   <li>{@link DuplicateResourceException} → 409 Conflict</li>
 * </ul>
 *
 * @see ErrorResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles domain validation errors from Validation.java.
     *
     * <p>Domain objects throw IllegalArgumentException when field constraints
     * are violated. The exception message is passed through as-is since
     * Validation.java produces user-friendly messages.
     *
     * @param ex the IllegalArgumentException thrown by domain validation
     * @return 400 Bad Request with the validation error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(final IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Handles Bean Validation errors from @Valid annotations.
     *
     * <p>Extracts the first field error message to keep the response simple.
     * Format: "fieldName: validation message"
     *
     * @param ex the validation exception containing field errors
     * @return 400 Bad Request with the first validation error
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(final MethodArgumentNotValidException ex) {
        final String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    /**
     * Handles malformed JSON in request body.
     *
     * @param ex the exception thrown when JSON cannot be parsed
     * @return 400 Bad Request with a generic error message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(final HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid JSON in request body"));
    }

    /**
     * Handles resource not found errors.
     *
     * @param ex the exception indicating the resource was not found
     * @return 404 Not Found with the error message
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(final ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Handles duplicate resource (ID conflict) errors.
     *
     * @param ex the exception indicating a duplicate ID
     * @return 409 Conflict with the error message
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(final DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }
}
