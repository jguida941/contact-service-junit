package contactapp.service;

import contactapp.security.TestUserSetup;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;

/**
 * Centralized test cleanup utility that ensures complete state isolation between tests.
 *
 * This utility addresses the singleton pattern + Spring context sharing issue by:
 * 1. Resetting static singleton instances via reflection
 * 2. Clearing all service data via Spring-managed beans
 * 3. Clearing security contexts
 * 4. Clearing Hibernate L1 cache (EntityManager) to prevent stale entities
 * 5. Providing a consistent cleanup order across all tests
 *
 * <p>The EntityManager flush/clear is critical because when Spring context is cached
 * across test classes (same {@code @SpringBootTest} config), Hibernate's L1 cache
 * can retain entity references even after {@code deleteAll()} operations, causing
 * duplicate key errors when tests try to re-insert entities with the same IDs.
 */
@Component
public class TestCleanupUtility {

    @Autowired(required = false)
    private ContactService contactService;

    @Autowired(required = false)
    private TaskService taskService;

    @Autowired(required = false)
    private AppointmentService appointmentService;

    @Autowired(required = false)
    private ProjectService projectService;

    @Autowired(required = false)
    private TestUserSetup testUserSetup;

    @Autowired(required = false)
    private EntityManager entityManager;

    /**
     * Performs complete cleanup in the correct order:
     * 1. Clear security contexts first
     * 2. Reset singletons before clearing data (prevents migration)
     * 3. Clear all service data (tasks/contacts/appointments reference users)
     * 4. Clean up test users last (FK constraints cascade delete)
     * 5. Clear Hibernate L1 cache to prevent stale entity issues
     *
     * Call this in @BeforeEach to ensure test isolation.
     *
     * <p>Uses REQUIRES_NEW propagation to force immediate commit so changes
     * are visible to the test that runs after @BeforeEach completes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanAll() {
        // Step 1: Clear security contexts
        clearSecurityContexts();

        // Step 2: Reset singletons BEFORE clearing data
        // This prevents registerInstance() from migrating old data
        resetAllSingletons();

        // Step 3: Clear all service data FIRST (tasks/contacts/appointments reference users)
        clearAllServiceData();

        // Step 3.5: FORCE deletion to happen NOW before touching users
        // Without this flush, Hibernate might reorder SQL and delete users before tasks (FK violation)
        if (entityManager != null) {
            entityManager.flush();
        }

        // Step 4: Clean up test users LAST (FK cascade will handle orphans)
        if (testUserSetup != null) {
            testUserSetup.cleanup();
        }

        // Step 5: Clear Hibernate EntityManager cache to prevent stale entities
        // This is critical when Spring context is shared across test classes
        if (entityManager != null) {
            entityManager.flush();  // Ensure all pending changes are written to DB
            entityManager.clear();  // Clear L1 cache to prevent duplicate key errors
        }
    }

    /**
     * Clears both standard and test security contexts.
     */
    private void clearSecurityContexts() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    /**
     * Resets all static singleton instances to null via reflection.
     * This prevents data migration when Spring beans are reused.
     */
    private void resetAllSingletons() {
        resetSingleton(ContactService.class);
        resetSingleton(TaskService.class);
        resetSingleton(AppointmentService.class);
        resetSingleton(ProjectService.class);
    }

    /**
     * Uses reflection to set the static 'instance' field to null.
     */
    private void resetSingleton(final Class<?> serviceClass) {
        try {
            final Field instanceField = serviceClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // If the field doesn't exist or can't be accessed, it's likely
            // the singleton pattern was removed (which is fine)
            System.err.println("Warning: Could not reset singleton for " + serviceClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Clears all data from all services using their package-private clear methods.
     */
    private void clearAllServiceData() {
        try {
            if (contactService != null) {
                contactService.clearAllContacts();
            }
            if (taskService != null) {
                taskService.clearAllTasks();
            }
            if (appointmentService != null) {
                appointmentService.clearAllAppointments();
            }
            if (projectService != null) {
                projectService.clearAllProjects();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear service data", e);
        }
    }

    /**
     * Setup helper that creates a fresh test user after cleanup.
     * Call this after cleanAll() in your @BeforeEach.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setupFreshTestUser() {
        if (testUserSetup != null) {
            testUserSetup.setupTestUser();
        }
    }

    /**
     * Complete cleanup + setup in one call.
     * Equivalent to: cleanAll() + setupFreshTestUser()
     */
    public void resetTestEnvironment() {
        cleanAll();
        setupFreshTestUser();
    }
}
