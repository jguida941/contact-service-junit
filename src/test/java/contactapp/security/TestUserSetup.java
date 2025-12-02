package contactapp.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test utility for setting up authenticated users in tests.
 *
 * <p>This class handles both database persistence and SecurityContext setup,
 * ensuring that tests have a valid user that satisfies FK constraints when
 * creating contacts, tasks, or appointments.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Autowired
 * private TestUserSetup testUserSetup;
 *
 * @BeforeEach
 * void setUp() {
 *     testUserSetup.setupTestUser();
 * }
 * }</pre>
 */
@Component
public class TestUserSetup {

    private static final String TEST_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMye3.Jv8Q5J5YJO1gqRbC1I/pL.sZ5g5jC";

    private final UserRepository userRepository;

    public TestUserSetup(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Creates a test user in the database and sets up the SecurityContext.
     *
     * <p>If a user with username "testuser" already exists, it's reused.
     * The SecurityContext is populated with this user as the principal.
     *
     * @return the persisted test user
     */
    @Transactional
    public User setupTestUser() {
        return setupTestUser("testuser", "testuser@example.com", Role.USER);
    }

    /**
     * Creates a test user with custom attributes.
     *
     * @param username the username
     * @param email the email
     * @param role the role
     * @return the persisted test user
     */
    @Transactional
    public User setupTestUser(final String username, final String email, final Role role) {
        // Reset any stale authentication and ensure a fresh user row exists for FK targets
        SecurityContextHolder.clearContext();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || (user.getId() != null && !userRepository.existsById(user.getId()))) {
            user = new User(username, email, TEST_PASSWORD_HASH, role);
            user = userRepository.save(user);
        }

        // Set up SecurityContext with this user
        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        final var authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.getAuthorities()
        );
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // Keep MockMvc's TestSecurityContextHolder in sync so controller requests
        // and direct service calls share the same authenticated principal.
        TestSecurityContextHolder.setContext(context);

        return user;
    }

    /**
     * Clears all test users and the SecurityContext.
     */
    @Transactional
    public void cleanup() {
        SecurityContextHolder.clearContext();
        userRepository.deleteAll();
    }
}
