package contactapp.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA (Single Page Application) forwarding controller.
 *
 * <p>In production mode, the React frontend is bundled into the JAR and served as static
 * resources from {@code /static/}. However, React Router uses client-side routing, meaning
 * routes like {@code /login}, {@code /contacts}, etc. exist only in JavaScript.
 *
 * <p>When a user directly navigates to {@code http://localhost:8080/login} (e.g., bookmark,
 * refresh, or shared link), Spring MVC doesn't find a static resource at that path and would
 * return a 404. This controller intercepts those requests and forwards them to {@code index.html},
 * allowing React Router to handle the route client-side.
 *
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li><strong>Whitelist approach:</strong> Only explicitly listed routes are forwarded.
 *       This prevents path traversal attacks that could occur with catch-all regex patterns.</li>
 *   <li><strong>GET only:</strong> Only GET requests are forwarded. POST/PUT/DELETE to unknown
 *       routes will still return appropriate errors.</li>
 *   <li><strong>No sensitive paths:</strong> API ({@code /api/**}), actuator ({@code /actuator/**}),
 *       and documentation routes are handled by their respective controllers, not forwarded.</li>
 *   <li><strong>Static resources preserved:</strong> Requests with file extensions
 *       ({@code .js}, {@code .css}, etc.) are served by Spring's default resource handler.</li>
 * </ul>
 *
 * <h2>Adding New Routes</h2>
 * When adding new pages to the React frontend:
 * <ol>
 *   <li>Add the route to React Router in the frontend</li>
 *   <li>Add the corresponding {@code @GetMapping} here for deep-link support</li>
 * </ol>
 *
 * @see contactapp.security.SecurityConfig#SPA_GET_MATCHER for security rule alignment
 */
@Controller
public class SpaController {

    /**
     * The forward target - serves the React app's entry point.
     * Spring's "forward:" prefix performs an internal server-side forward,
     * preserving the original URL in the browser.
     */
    private static final String FORWARD_INDEX = "forward:/index.html";

    // ==================== Authentication Routes ====================

    @GetMapping("/login")
    public String login() {
        return FORWARD_INDEX;
    }

    @GetMapping("/register")
    public String register() {
        return FORWARD_INDEX;
    }

    // ==================== Main Application Routes ====================

    @GetMapping("/contacts")
    public String contacts() {
        return FORWARD_INDEX;
    }

    @GetMapping("/contacts/{id}")
    public String contactDetail() {
        return FORWARD_INDEX;
    }

    @GetMapping("/tasks")
    public String tasks() {
        return FORWARD_INDEX;
    }

    @GetMapping("/tasks/{id}")
    public String taskDetail() {
        return FORWARD_INDEX;
    }

    @GetMapping("/appointments")
    public String appointments() {
        return FORWARD_INDEX;
    }

    @GetMapping("/appointments/{id}")
    public String appointmentDetail() {
        return FORWARD_INDEX;
    }

    @GetMapping("/projects")
    public String projects() {
        return FORWARD_INDEX;
    }

    @GetMapping("/projects/{id}")
    public String projectDetail() {
        return FORWARD_INDEX;
    }

    // ==================== Admin Routes ====================

    @GetMapping("/admin")
    public String admin() {
        return FORWARD_INDEX;
    }

    @GetMapping("/admin/{section}")
    public String adminSection() {
        return FORWARD_INDEX;
    }

    @GetMapping("/admin/{section}/{subsection}")
    public String adminSubsection() {
        return FORWARD_INDEX;
    }

    // ==================== User Routes ====================

    @GetMapping("/profile")
    public String profile() {
        return FORWARD_INDEX;
    }

    @GetMapping("/settings")
    public String settings() {
        return FORWARD_INDEX;
    }

    // ==================== Error/Fallback Routes ====================

    @GetMapping("/not-found")
    public String notFound() {
        return FORWARD_INDEX;
    }

    @GetMapping("/unauthorized")
    public String unauthorized() {
        return FORWARD_INDEX;
    }
}
