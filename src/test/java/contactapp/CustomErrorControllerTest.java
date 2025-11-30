package contactapp;

import contactapp.api.CustomErrorController;
import contactapp.api.dto.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomErrorController}.
 *
 * <p>Tests the error controller that ensures most errors return JSON responses,
 * including those rejected at the servlet container level before reaching
 * Spring MVC's exception handling.
 *
 * <p>This controller (combined with JsonErrorReportValve) ensures API consumers
 * receive parseable JSON for normal error scenarios. Note: Extremely malformed
 * URLs that fail at Tomcat's connector level may still return HTML; see ADR-0022.
 */
class CustomErrorControllerTest {

    private CustomErrorController controller;
    private HttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        controller = new CustomErrorController();
        mockRequest = mock(HttpServletRequest.class);
    }

    @Test
    void handleError_returnsJsonContentType() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(400);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
    }

    @Test
    void handleError_badRequest_returnsStatus400() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(400);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad request", response.getBody().message());
    }

    @Test
    void handleError_notFound_returnsStatus404() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Resource not found", response.getBody().message());
    }

    @Test
    void handleError_methodNotAllowed_returnsStatus405() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(405);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Method not allowed", response.getBody().message());
    }

    @Test
    void handleError_unsupportedMediaType_returnsStatus415() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(415);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Unsupported media type", response.getBody().message());
    }

    @Test
    void handleError_internalServerError_returnsStatus500() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(500);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal server error", response.getBody().message());
    }

    @Test
    void handleError_nullStatusCode_defaults500() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(null);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        // When status is null, HttpStatus.resolve() returns null, which triggers default 500
        // but the message comes from the switch default case for INTERNAL_SERVER_ERROR
        assertEquals("Internal server error", response.getBody().message());
    }

    @Test
    void handleError_unknownStatusCode_usesReasonPhrase() {
        // 418 I'm a teapot - uncommon status with no custom message mapping
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(418);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.I_AM_A_TEAPOT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("I'm a teapot", response.getBody().message());
    }

    @Test
    void handleError_invalidStatusCode_defaultsTo500() {
        // 999 is not a valid HTTP status code - HttpStatus.resolve() returns null
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(999);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        // Falls back to 500 Internal Server Error when status code is unresolvable
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }

    @Test
    void handleError_withCustomMessage_usesProvidedMessage() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(400);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("Invalid path variable");

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid path variable", response.getBody().message());
    }

    @Test
    void handleError_withBlankMessage_usesDefaultMessage() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(400);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("   ");

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Bad request", response.getBody().message());
    }

    @Test
    void handleError_withEmptyMessage_usesDefaultMessage() {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(404);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn("");

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Resource not found", response.getBody().message());
    }

    @ParameterizedTest
    @CsvSource({
            "400, Bad request",
            "404, Resource not found",
            "405, Method not allowed",
            "415, Unsupported media type",
            "500, Internal server error"
    })
    void handleError_statusCodeMapping(final int statusCode, final String expectedMessage) {
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(statusCode);
        when(mockRequest.getAttribute(RequestDispatcher.ERROR_MESSAGE)).thenReturn(null);

        final ResponseEntity<ErrorResponse> response = controller.handleError(mockRequest);

        assertNotNull(response.getBody());
        assertEquals(expectedMessage, response.getBody().message());
    }
}
