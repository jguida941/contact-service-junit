package contactapp;

import contactapp.api.GlobalExceptionHandler;
import contactapp.api.dto.ErrorResponse;
import contactapp.api.exception.DuplicateResourceException;
import contactapp.api.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Tests each exception handler method directly to ensure correct
 * HTTP status codes and error message propagation.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleIllegalArgument_returnsStatusBadRequest() {
        final IllegalArgumentException ex =
                new IllegalArgumentException("firstName must not be null or blank");

        final ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("firstName must not be null or blank", response.getBody().message());
    }

    @Test
    void handleIllegalArgument_preservesExceptionMessage() {
        final String customMessage = "Custom validation error message";
        final IllegalArgumentException ex = new IllegalArgumentException(customMessage);

        final ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex);

        assertNotNull(response.getBody());
        assertEquals(customMessage, response.getBody().message());
    }

    @Test
    void handleNotFound_returnsStatus404() {
        final ResourceNotFoundException ex =
                new ResourceNotFoundException("Contact not found: 123");

        final ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Contact not found: 123", response.getBody().message());
    }

    @Test
    void handleDuplicate_returnsStatus409() {
        final DuplicateResourceException ex =
                new DuplicateResourceException("Contact with id '123' already exists");

        final ResponseEntity<ErrorResponse> response = handler.handleDuplicate(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Contact with id '123' already exists", response.getBody().message());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleConstraintViolation_returnsStatusBadRequest() {
        // Mock a ConstraintViolation for path variable validation
        final ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        final Path path = mock(Path.class);
        when(path.toString()).thenReturn("getById.id");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("size must be between 0 and 10");

        final ConstraintViolationException ex =
                new ConstraintViolationException("Validation failed", Set.of(violation));

        final ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        // Field name is extracted from path (last segment "id") and humanized to "ID"
        assertEquals("ID: size must be between 0 and 10", response.getBody().message());
    }

    @Test
    void handleAccessDenied_returnsForbidden() {
        final AccessDeniedException ex = new AccessDeniedException("Only ADMIN users can access all contacts");

        final ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Only ADMIN users can access all contacts", response.getBody().message());
    }

    @Test
    void handleOptimisticLock_returnsConflict() {
        final ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("contactapp.persistence.entity.ContactEntity", "123");

        final ResponseEntity<ErrorResponse> response = handler.handleOptimisticLock(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Resource was modified by another request. Refresh and try again.", response.getBody().message());
    }
}
