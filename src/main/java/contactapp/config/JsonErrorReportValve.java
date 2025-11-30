package contactapp.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;

/**
 * Custom Tomcat valve that ensures ALL error responses return JSON.
 *
 * <p>This valve intercepts errors at the Tomcat container level, including:
 * <ul>
 *   <li>URL decoding failures (malformed percent-encoded sequences)</li>
 *   <li>Invalid URI syntax errors</li>
 *   <li>Any other errors that occur before reaching Spring MVC</li>
 * </ul>
 *
 * <p>Unlike Spring's {@code @RestControllerAdvice} or {@code ErrorController},
 * this valve operates at the servlet container level and can intercept errors
 * that never reach the Spring application context.
 *
 * @see TomcatConfig for registration of this valve
 */
public class JsonErrorReportValve extends ErrorReportValve {

    /** Minimum HTTP status code considered an error. */
    private static final int ERROR_STATUS_THRESHOLD = HttpServletResponse.SC_BAD_REQUEST;

    @Override
    protected void report(final Request request, final Response response,
                          final Throwable throwable) {

        final int statusCode = response.getStatus();

        // Don't process successful responses
        if (statusCode < ERROR_STATUS_THRESHOLD) {
            return;
        }

        // Set JSON content type
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Build JSON error response
        final String message = getErrorMessage(statusCode);
        final String jsonResponse = "{\"message\":\"" + message + "\"}";

        try {
            try {
                response.resetBuffer();
            } catch (IllegalStateException e) {
                // Response already committed, can't reset
            }

            final Writer writer = response.getReporter();
            if (writer != null) {
                writer.write(jsonResponse);
                writer.flush();
            }
        } catch (IOException e) {
            // Can't write response, nothing we can do
        }
    }

    /**
     * Returns a user-friendly error message for the given HTTP status code.
     *
     * @param statusCode the HTTP status code
     * @return a generic error message
     */
    @SuppressWarnings("checkstyle:MagicNumber") // HTTP status codes are standard, not magic
    private String getErrorMessage(final int statusCode) {
        return switch (statusCode) {
            case HttpServletResponse.SC_BAD_REQUEST -> "Bad request";
            case HttpServletResponse.SC_UNAUTHORIZED -> "Unauthorized";
            case HttpServletResponse.SC_FORBIDDEN -> "Forbidden";
            case HttpServletResponse.SC_NOT_FOUND -> "Resource not found";
            case HttpServletResponse.SC_METHOD_NOT_ALLOWED -> "Method not allowed";
            case HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE -> "Unsupported media type";
            case HttpServletResponse.SC_INTERNAL_SERVER_ERROR -> "Internal server error";
            default -> "Error";
        };
    }
}
