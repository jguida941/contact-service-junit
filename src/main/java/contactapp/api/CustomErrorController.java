package contactapp.api;

import contactapp.api.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Custom error controller that ensures ALL errors return JSON responses.
 *
 * <p>This controller intercepts errors that occur before reaching Spring MVC's
 * exception handling (e.g., Tomcat-level errors from malformed requests, invalid
 * path variables, or servlet container rejections). Without this controller,
 * such errors would return Tomcat's default HTML error page.
 *
 * <h2>Why This Exists</h2>
 * <p>Spring's {@code @RestControllerAdvice} only catches exceptions thrown from
 * within controllers. Errors rejected at the servlet container level (e.g., fuzzed
 * path variables that fail URI parsing) bypass Spring MVC entirely and trigger
 * Tomcat's default error handling, which returns HTML.
 *
 * <p>This controller implements Spring Boot's {@link ErrorController} interface
 * to intercept the {@code /error} path and return a consistent JSON response,
 * ensuring API consumers always receive {@code application/json} content type.
 *
 * <h2>Integration with Schemathesis</h2>
 * <p>API fuzzing tools like Schemathesis test with malformed inputs that may
 * trigger container-level errors. This controller (combined with
 * {@link contactapp.config.JsonErrorReportValve}) ensures most error responses
 * conform to the OpenAPI spec's documented content type ({@code application/json}).
 * Note: Extremely malformed URLs that fail at Tomcat's connector level (before
 * reaching the valve) may still return HTML; see ADR-0022.
 *
 * @see GlobalExceptionHandler for Spring MVC-level exception handling
 * @see ErrorResponse for the JSON error format
 */
@RestController
@Hidden // Exclude from OpenAPI spec - this is an internal error handler, not a public API
public class CustomErrorController implements ErrorController {

    /** Default HTTP status code when container doesn't provide one. */
    private static final int DEFAULT_ERROR_STATUS = 500;

    /**
     * Handles all errors forwarded to /error by the servlet container.
     *
     * <p>Extracts the HTTP status code from the request attributes and returns
     * an appropriate JSON error response. Common scenarios include:
     * <ul>
     *   <li>400 Bad Request - Malformed request syntax, invalid path variables</li>
     *   <li>404 Not Found - No handler found for the request path</li>
     *   <li>405 Method Not Allowed - HTTP method not supported for the endpoint</li>
     *   <li>500 Internal Server Error - Unexpected server-side failures</li>
     * </ul>
     *
     * @param request the HTTP request containing error attributes
     * @return JSON error response with appropriate HTTP status
     */
    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ErrorResponse> handleError(final HttpServletRequest request) {
        // Extract status code from request attributes (set by servlet container)
        final Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        final int statusCode = (statusObj instanceof Integer) ? (Integer) statusObj : DEFAULT_ERROR_STATUS;
        final HttpStatus status = HttpStatus.resolve(statusCode);

        // Extract error message if available
        final Object messageObj = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        final String message = buildErrorMessage(status, messageObj);

        return ResponseEntity
                .status(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(message));
    }

    /**
     * Builds a user-friendly error message based on the HTTP status.
     *
     * <p>Provides generic messages to avoid leaking internal details while
     * still being informative enough for API consumers to understand the error.
     *
     * @param status the HTTP status code
     * @param messageObj the error message from the request (may be null or empty)
     * @return a user-friendly error message
     */
    private String buildErrorMessage(final HttpStatus status, final Object messageObj) {
        // Use container message if available and non-empty
        if (messageObj instanceof String && !((String) messageObj).isBlank()) {
            return (String) messageObj;
        }

        // Provide generic messages based on status code
        if (status == null) {
            return "An unexpected error occurred";
        }

        return switch (status) {
            case BAD_REQUEST -> "Bad request";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Method not allowed";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported media type";
            case INTERNAL_SERVER_ERROR -> "Internal server error";
            default -> status.getReasonPhrase();
        };
    }
}
