package contactapp;

import contactapp.api.GlobalExceptionHandler;
import contactapp.api.dto.ErrorResponse;
import contactapp.api.exception.DuplicateResourceException;
import contactapp.api.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
