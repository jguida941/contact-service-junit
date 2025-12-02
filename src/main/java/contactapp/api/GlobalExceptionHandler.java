package contactapp.api;

import contactapp.api.dto.ErrorResponse;
import contactapp.api.exception.DuplicateResourceException;
import contactapp.api.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
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
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (Bean Validation on @RequestBody)</li>
 *   <li>{@link ConstraintViolationException} → 400 Bad Request (Bean Validation on @PathVariable)</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 Bad Request (malformed JSON)</li>
 *   <li>{@link AuthenticationException} → 401 Unauthorized (invalid credentials)</li>
 *   <li>{@link AccessDeniedException} → 403 Forbidden (insufficient permissions)</li>
 *   <li>{@link ResourceNotFoundException} → 404 Not Found</li>
 *   <li>{@link DuplicateResourceException} → 409 Conflict</li>
 *   <li>{@link ObjectOptimisticLockingFailureException} → 409 Conflict (concurrent update)</li>
 * </ul>
 *
 * @see ErrorResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
     * Handles Bean Validation errors from @Validated method parameters.
     *
     * <p>This handles constraint violations on @PathVariable and @RequestParam
     * parameters when the controller is annotated with @Validated.
     * Extracts the first violation message for a simple response.
     *
     * @param ex the constraint violation exception
     * @return 400 Bad Request with the first validation error
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(final ConstraintViolationException ex) {
        final String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    /**
     * Handles malformed JSON in request body.
     *
     * <p>Returns a generic error message to avoid leaking internal parsing details.
     * The exception parameter is required by Spring's @ExceptionHandler contract
     * but intentionally unused to prevent information disclosure.
     *
     * @param ex the exception thrown when JSON cannot be parsed (unused intentionally)
     * @return 400 Bad Request with a generic error message
     */
    @SuppressWarnings("java:S1172") // Parameter required by Spring @ExceptionHandler contract
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(final HttpMessageNotReadableException ex) {
        // Intentionally return generic message - do not expose parsing details to clients
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

    /**
     * Handles optimistic locking failures when concurrent updates occur.
     *
     * Returns a generic error message to avoid leaking entity names or version values.
     *
     * @param ex the optimistic locking exception (logged at debug, not returned to client)
     * @return 409 Conflict with a friendly message
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(final ObjectOptimisticLockingFailureException ex) {
        // Log for debugging but avoid echoing entity/version info to the client
        LOG.debug("Optimistic locking failure: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Resource was modified by another request. Refresh and try again."));
    }

    /**
     * Handles authentication failures (bad credentials, locked accounts, etc.).
     *
     * <p>Returns a generic "Invalid credentials" message for security reasons;
     * we don't reveal whether the username exists or the password was wrong.
     *
     * @param ex the authentication exception (BadCredentialsException, etc.)
     * @return 401 Unauthorized with a generic error message
     */
    @SuppressWarnings("java:S1172") // Parameter required by Spring @ExceptionHandler contract
    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(final AuthenticationException ex) {
        // Intentionally return generic message - do not reveal specifics to clients
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Invalid credentials"));
    }

    /**
     * Handles authorization failures (insufficient permissions).
     *
     * <p>Returns 403 Forbidden when a user attempts to access a resource
     * they don't have permission for (e.g., non-ADMIN accessing ?all=true endpoints).
     *
     * @param ex the access denied exception
     * @return 403 Forbidden with the error message
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(final AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getMessage()));
    }
}
