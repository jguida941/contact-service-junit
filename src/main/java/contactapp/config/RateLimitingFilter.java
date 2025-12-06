package contactapp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that enforces rate limits on API endpoints using token bucket algorithm.
 *
 * <p>This filter protects high-risk endpoints from abuse by limiting the number of
 * requests that can be made within a time window. Different endpoints have different
 * limits based on their risk profile:
 *
 * <ul>
 *   <li><b>/api/auth/login</b>: Limited per IP address to prevent brute force attacks</li>
 *   <li><b>/api/auth/register</b>: Limited per IP address to prevent account spam</li>
 *   <li><b>/api/v1/**</b>: Limited per authenticated user to prevent DoS</li>
 * </ul>
 *
 * <h2>Token Bucket Algorithm</h2>
 * <p>Each client (identified by IP or username) gets a bucket with a fixed capacity.
 * The bucket starts full and refills at a constant rate. Each request consumes one token.
 * If the bucket is empty, the request is rejected with HTTP 429 (Too Many Requests).
 *
 * <h2>Response Headers</h2>
 * <p>When rate limit is exceeded, the filter returns:
 * <ul>
 *   <li><b>Status</b>: 429 Too Many Requests</li>
 *   <li><b>Retry-After</b>: Seconds until next token is available</li>
 *   <li><b>Content-Type</b>: application/json</li>
 *   <li><b>Body</b>: {"error": "Rate limit exceeded", "retryAfter": N}</li>
 * </ul>
 *
 * <h2>Key Derivation Strategy</h2>
 * <ul>
 *   <li>Auth endpoints: IP address (protect against distributed attacks)</li>
 *   <li>API endpoints: Username (protect per-user resources)</li>
 * </ul>
 *
 * @see RateLimitConfig
 */
@Component
@Order(RateLimitingFilter.FILTER_ORDER)
public class RateLimitingFilter extends OncePerRequestFilter {

    /** Filter order: after CorrelationIdFilter (1), before RequestLoggingFilter (10). */
    public static final int FILTER_ORDER = 5;

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    /** Allowed characters when logging user-provided values. */
    private static final Pattern SAFE_LOG_PATTERN = Pattern.compile("^[A-Za-z0-9 .:@/_-]+$");

    /** Maximum length for sanitized log values to prevent log flooding. */
    private static final int MAX_LOG_LENGTH = 120;

    private static final String LOGIN_KEY_PREFIX = "LOGIN:";
    private static final String REGISTER_KEY_PREFIX = "REGISTER:";
    private static final String API_KEY_PREFIX = "API:";

    /**
     * Maximum number of unique IPs/users to track. Prevents memory exhaustion
     * from distributed attacks. Oldest entries are evicted when limit is reached.
     */
    private static final int MAX_BUCKET_ENTRIES = 10_000;

    /**
     * Time after which unused bucket entries are evicted. Allows memory to be
     * reclaimed for inactive clients while preserving limits for active ones.
     */
    private static final Duration BUCKET_EXPIRY = Duration.ofMinutes(10);

    private final RateLimitConfig rateLimitConfig;

    /**
     * Bounded cache for IP-based rate limit buckets (login/register endpoints).
     * Uses Caffeine for automatic eviction by size and time to prevent DoS.
     */
    private final Cache<String, Bucket> ipBuckets = Caffeine.newBuilder()
            .maximumSize(MAX_BUCKET_ENTRIES)
            .expireAfterAccess(BUCKET_EXPIRY)
            .build();

    /**
     * Bounded cache for user-based rate limit buckets (authenticated API endpoints).
     * Uses Caffeine for automatic eviction by size and time to prevent DoS.
     */
    private final Cache<String, Bucket> userBuckets = Caffeine.newBuilder()
            .maximumSize(MAX_BUCKET_ENTRIES)
            .expireAfterAccess(BUCKET_EXPIRY)
            .build();

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring-managed singleton; config is read-only after startup")
    public RateLimitingFilter(final RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    protected void doFilterInternal(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain
    ) throws ServletException, IOException {
        final String path = request.getRequestURI();

        // Determine rate limit configuration and key based on path
        Bucket bucket = null;
        String rateLimitKey = null;

        if (path.startsWith("/api/auth/login")) {
            final String clientIp = sanitizeClientIp(RequestUtils.getClientIp(request));
            rateLimitKey = LOGIN_KEY_PREFIX + clientIp;
            bucket = ipBuckets.get(rateLimitKey, k ->
                    createBucket(rateLimitConfig.getLogin()));
            logSafeValue("Rate limiting login request from IP: {}", clientIp);
        } else if (path.startsWith("/api/auth/register")) {
            final String clientIp = sanitizeClientIp(RequestUtils.getClientIp(request));
            rateLimitKey = REGISTER_KEY_PREFIX + clientIp;
            bucket = ipBuckets.get(rateLimitKey, k ->
                    createBucket(rateLimitConfig.getRegister()));
            logSafeValue("Rate limiting register request from IP: {}", clientIp);
        } else if (path.startsWith("/api/v1/")) {
            // For authenticated endpoints, use prefixed username as key
            final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                final String username = authentication.getName();
                rateLimitKey = API_KEY_PREFIX + username;
                bucket = userBuckets.get(rateLimitKey, k ->
                        createBucket(rateLimitConfig.getApi()));
                logSafeValue("Rate limiting API request from user: {}", username);
            }
        }

        // If no rate limit applies to this path, pass through
        if (bucket == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Try to consume a token from the bucket
        if (bucket.tryConsume(1)) {
            // Token available, allow request
            filterChain.doFilter(request, response);
        } else {
            // No tokens available, reject with 429
            final long waitForRefill = calculateWaitTime(bucket);
            handleRateLimitExceeded(response, waitForRefill);
            logRateLimitExceeded(rateLimitKey, path);
        }
    }

    /**
     * Creates a new token bucket with the specified rate limit configuration.
     *
     * <p>The bucket uses greedy refill strategy: all tokens become available
     * instantly after the refill period elapses. This is simpler than interval
     * refill and sufficient for our use case.
     *
     * @param limit the rate limit configuration
     * @return a new bucket configured with the specified limits
     */
    private Bucket createBucket(final RateLimitConfig.EndpointLimit limit) {
        final long capacity = limit.getRequests();
        final Duration refillPeriod = Duration.ofSeconds(limit.getDurationSeconds());
        final Bandwidth bandwidth = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, refillPeriod)
                .build();
        return Bucket.builder()
                .addLimit(bandwidth)
                .build();
    }

    /**
     * Calculates the wait time in seconds until the next token becomes available.
     *
     * <p>This uses bucket4j's estimateAbilityToConsume to determine when the next
     * token will be available. The result is used in the Retry-After header.
     *
     * @param bucket the token bucket
     * @return wait time in seconds (minimum 1)
     */
    private long calculateWaitTime(final Bucket bucket) {
        // Try to get the probe result to estimate wait time
        final var probe = bucket.estimateAbilityToConsume(1);
        if (probe.getRemainingTokens() >= 1) {
            return 1; // Should not happen, but fallback to 1 second
        }
        // Calculate seconds to wait (nanoseconds to seconds, rounded up)
        final long waitNanos = probe.getNanosToWaitForRefill();
        final long waitSeconds = (waitNanos / 1_000_000_000L) + 1;
        return Math.max(1, waitSeconds);
    }

    /**
     * Sends 429 Too Many Requests response with Retry-After header.
     *
     * <p>Response includes:
     * <ul>
     *   <li>HTTP 429 status code</li>
     *   <li>Retry-After header with seconds to wait</li>
     *   <li>JSON body with error message and retry time</li>
     * </ul>
     *
     * @param response the HTTP response
     * @param retryAfterSeconds seconds until next token is available
     * @throws IOException if writing response fails
     */
    private void handleRateLimitExceeded(final HttpServletResponse response, final long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        final String jsonResponse = String.format(
                "{\"error\":\"Rate limit exceeded. Please try again in %d seconds.\",\"retryAfter\":%d}",
                retryAfterSeconds,
                retryAfterSeconds
        );
        response.getWriter().write(jsonResponse);
    }

    /**
     * Clears all rate limit buckets.
     *
     * <p>Useful for testing and administrative operations. In production,
     * buckets naturally expire based on Caffeine's eviction policy.
     */
    public void clearBuckets() {
        ipBuckets.invalidateAll();
        userBuckets.invalidateAll();
        logger.info("Cleared all rate limit buckets");
    }

    /**
     * Gets the approximate number of IP-based buckets currently tracked.
     *
     * @return approximate number of IP buckets
     */
    public long getIpBucketCount() {
        return ipBuckets.estimatedSize();
    }

    /**
     * Gets the approximate number of user-based buckets currently tracked.
     *
     * @return approximate number of user buckets
     */
    public long getUserBucketCount() {
        return userBuckets.estimatedSize();
    }

    /**
     * Logs a value at DEBUG level after applying safety validation.
     *
     * @param message the log message format (must contain exactly one {} placeholder)
     * @param value the potentially untrusted input
     */
    private void logSafeValue(final String message, final String value) {
        logger.debug(message, getSafeLogValue(value));
    }

    /**
     * Validates and sanitizes a potentially untrusted value for logging.
     *
     * <p>Rejects control characters and anything outside the whitelist pattern,
     * trims surrounding whitespace, and caps the length to avoid log flooding.
     *
     * @param value the raw value to sanitize
     * @return a safe value for logging or a placeholder describing why it was rejected
     */
    private String getSafeLogValue(final String value) {
        if (value == null) {
            return "[null]";
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "[empty]";
        }
        if (!SAFE_LOG_PATTERN.matcher(trimmed).matches()) {
            return "[unsafe-value]";
        }
        if (trimmed.length() > MAX_LOG_LENGTH) {
            return trimmed.substring(0, MAX_LOG_LENGTH) + "...";
        }
        return trimmed;
    }

    /**
     * Sanitizes a client IP for use as a rate limit key and debug log value.
     *
     * <p>Handles X-Forwarded-For format (comma-separated IP chain) by extracting
     * the first IP (original client). Strips CR/LF to prevent log injection.
     *
     * @param clientIp raw IP string, possibly from X-Forwarded-For header
     * @return the first valid IP, sanitized for logging
     */
    private String sanitizeClientIp(final String clientIp) {
        if (clientIp == null) {
            return "[null]";
        }
        // Strip CR/LF first to prevent log injection
        final String noNewlines = clientIp.replace("\r", "").replace("\n", "");
        // X-Forwarded-For format: "client, proxy1, proxy2" - take first IP
        final String firstIp = noNewlines.split(",")[0].trim();
        return firstIp.isEmpty() ? "[empty]" : firstIp;
    }

    /**
     * Logs rate limit exceeded warning with fully sanitized key and path.
     *
     * <p>Sanitization is inlined so CodeQL can trace the data flow (CWE-117):
     * CR/LF stripping, whitelist pattern validation, and length limit.
     *
     * @param key the rate limit key (IP or username)
     * @param path the request path
     */
    private void logRateLimitExceeded(final String key, final String path) {
        // Inline sanitization for CodeQL CWE-117 traceability
        final String safeKey = sanitizeForLog(key);
        final String safePath = sanitizeForLog(path);
        logger.warn("Rate limit exceeded for key: {} on path: {}", safeKey, safePath);
    }

    /**
     * Sanitizes a value for logging with explicit newline removal.
     * Inlined logic ensures CodeQL can trace data flow (CWE-117).
     */
    private String sanitizeForLog(final String value) {
        if (value == null) {
            return "[null]";
        }
        // Explicit CR/LF removal for CodeQL CWE-117 compliance
        final String noNewlines = value.replace("\r", "").replace("\n", "");
        final String trimmed = noNewlines.trim();
        if (trimmed.isEmpty()) {
            return "[empty]";
        }
        if (!SAFE_LOG_PATTERN.matcher(trimmed).matches()) {
            return "[unsafe-value]";
        }
        if (trimmed.length() > MAX_LOG_LENGTH) {
            return trimmed.substring(0, MAX_LOG_LENGTH) + "...";
        }
        return trimmed;
    }

}
