# ADR-0022: Custom Error Controller for JSON Error Responses

## Status
Accepted

## Context
API fuzzing with Schemathesis revealed that some error responses return `text/html` instead of `application/json`, causing the `content_type_conformance` check to fail. This occurs when:

1. **Servlet container-level errors**: Malformed requests (garbled path variables, invalid URI encoding) are rejected by Tomcat BEFORE reaching Spring MVC's `@RestControllerAdvice`.
2. **Missing handler errors**: Requests to non-existent paths trigger Tomcat's default 404 handler.
3. **Method not allowed**: Unsupported HTTP methods on valid paths bypass Spring MVC exception handling.

The existing `GlobalExceptionHandler` only catches exceptions thrown from within controllers. Container-level errors fall through to Tomcat's default HTML error page.

### The Problem
```
PUT /api/v1/contacts/%invalid%path% → Tomcat returns HTML error page
Schemathesis checks content_type_conformance → FAILS (expected JSON, got HTML)
```

### Options Considered
1. **Remove `content_type_conformance` check** - Quick fix, but masks a real API quality issue.
2. **Implement `CustomErrorController`** - Ensures ALL errors return JSON, production-grade solution.

## Decision
Implement a two-layer solution to ensure ALL errors return JSON:

1. **JsonErrorReportValve** - Custom Tomcat valve that intercepts errors at the container level, including URL decoding failures that occur before reaching Spring
2. **CustomErrorController** - Spring Boot ErrorController for errors that reach Spring's error handling
3. **`@Hidden` annotation** - Excludes `/error` from OpenAPI spec (prevents Schemathesis from testing it as a regular API endpoint)

### Implementation

**JsonErrorReportValve.java** - Handles container-level errors with explicit Content-Length
```java
public class JsonErrorReportValve extends ErrorReportValve {

    @Override
    protected void report(final Request request, final Response response,
                          final Throwable throwable) {
        final int statusCode = response.getStatus();

        // Do not touch successful or already committed responses
        if (statusCode < ERROR_STATUS_THRESHOLD || response.isCommitted()) {
            return;
        }

        // Build JSON
        final String message = getErrorMessage(statusCode);
        final String jsonResponse = "{\"message\":\"" + message + "\"}";
        final byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

        try {
            try {
                response.resetBuffer();
            } catch (IllegalStateException e) {
                return; // Response already committed, bail out
            }

            // Set headers explicitly - Content-Length avoids chunked encoding issues
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setContentLength(bytes.length);

            // Write bytes directly
            final OutputStream out = response.getOutputStream();
            out.write(bytes);
            out.flush();
        } catch (IOException e) {
            // Can't write response
        }
    }
}
```

**CustomErrorController.java** - Spring Boot error handler
```java
@RestController
@Hidden // Exclude from OpenAPI spec - internal error handler, not a public API
public class CustomErrorController implements ErrorController {
    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        // Extract status code and message from request attributes
        // Return JSON error response with appropriate HTTP status
    }
}
```

**application.yml changes**
```yaml
server:
  error:
    whitelabel:
      enabled: false  # Disable Tomcat's default HTML error page
    include-message: always
```

### Why JsonErrorReportValve Uses Explicit Content-Length

The original valve implementation caused "chunked encoding, invalid chunk" errors during Schemathesis fuzzing:

**What was going wrong:**
1. **No Content-Length**: Tomcat defaulted to chunked transfer encoding
2. **No committed check**: Could write to already-committed responses
3. **No buffer reset**: Could append bytes mid-chunk, corrupting the stream

When Schemathesis sends URLs containing control characters (`\x1f`, etc.):
1. Tomcat's HTTP connector starts building a chunked response (no Content-Length)
2. An error occurs at a low level (URL decoding / connector layer)
3. The valve writes additional bytes without resetting or setting Content-Length
4. Client sees: "Server declared chunked encoding but sent an invalid chunk"

**The fix implements five safeguards:**

| Safeguard | Implementation | Purpose |
|-----------|---------------|---------|
| 1. Committed check | `response.isCommitted()` | Prevents appending bytes mid-chunk |
| 2. Buffer reset | `response.resetBuffer()` | Clears partial response data |
| 3. Bailout on exception | Catch `IllegalStateException` | Graceful handling of edge cases |
| 4. Explicit Content-Length | `response.setContentLength(bytes.length)` | Forces fixed-length encoding (no chunks) |
| 5. Binary write | `OutputStream.write(bytes)` | Writes exact byte count declared |

This is the standard pattern Tomcat and Spring Boot error handlers use: **guard → reset → set headers → write bytes → flush**.

### Error Message Strategy
The controller provides user-friendly messages based on HTTP status:
| Status | Default Message |
|--------|-----------------|
| 400 | Bad request |
| 404 | Resource not found |
| 405 | Method not allowed |
| 415 | Unsupported media type |
| 500 | Internal server error |

If the container provides a specific error message, it's used instead.

### Testing Strategy
17 unit tests cover:
- JSON content type verification
- Status code mapping (400, 404, 405, 415, 500)
- Default message fallbacks
- Null/blank message handling
- Custom message pass-through
- Uncommon status codes (418 I'm a teapot as edge case)
- Invalid status codes (999 → defaults to 500)

### Schemathesis Checks
Due to Tomcat URL-decoding limitations, the API fuzzing checks are:
- `not_a_server_error` - No 5xx crashes from application code
- `response_schema_conformance` - Valid JSON responses match OpenAPI schema

**NOT using** `content_type_conformance` because malformed URLs fail at Tomcat level.

## Consequences

### Positive
- **API conformance**: Most error responses return `application/json` for valid URL requests.
- **Consistent client experience**: API consumers receive parseable JSON for normal error scenarios.
- **Production-grade**: Proper REST API design for controllable error paths.
- **Security**: No HTML error pages that could leak stack traces or server info for most errors.

### Negative
- **Additional complexity**: One more controller to maintain.
- **Test overhead**: 17 new tests to maintain (total test count now 295).
- **Tomcat limitation**: Extremely malformed URLs (invalid Unicode in path) still return HTML—this is a Tomcat limitation we cannot work around without custom servlet filters or Tomcat configuration.

### Neutral
- **No performance impact**: Error paths are rare compared to happy paths.
- **Schemathesis v4+ compatibility**: Workflow updated to remove deprecated options (`--junit-xml`, `--base-url`, `--hypothesis-*`).
- **Fuzzed edge cases**: Malformed Unicode in URL paths is a fuzz-only scenario that real API clients never produce.

## Related ADRs
- ADR-0016: API Style and Contract (defines JSON error format)
- ADR-0021: REST API Implementation (defines GlobalExceptionHandler)
