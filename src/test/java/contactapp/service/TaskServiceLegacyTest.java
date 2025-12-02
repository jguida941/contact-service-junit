package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.domain.Task;
import contactapp.persistence.store.InMemoryTaskStore;
import contactapp.persistence.store.TaskStore;
import contactapp.security.TestUserSetup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ensures {@link TaskService#getInstance()} still works outside the Spring context.
 *
 * <p>Note: These tests run with Spring context to get the TestUserSetup bean.
 */
@SpringBootTest
@ActiveProfiles("test")
@Isolated
class TaskServiceLegacyTest {

    @Autowired
    private TestUserSetup testUserSetup;

    @Autowired
    private TaskService springService;

    @BeforeEach
    void resetSingleton() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        testUserSetup.cleanup();
        testUserSetup.setupTestUser();
        springService.clearAllTasks();
        setInstance(null);
    }

    @AfterEach
    void cleanSingleton() throws Exception {
        setInstance(null);
    }

    @Test
    void coldStartReturnsInMemoryStore() {
        TaskService legacy = TaskService.getInstance();
        assertThat(legacy).isNotNull();

        Task task = new Task("901", "Legacy Task", "Legacy Description");
        assertThat(legacy.addTask(task)).isTrue();
        assertThat(legacy.getTaskById("901")).isPresent();
    }

    @Test
    void repeatedCallsReturnSameLegacyInstance() {
        TaskService first = TaskService.getInstance();
        TaskService second = TaskService.getInstance();
        assertThat(first).isSameAs(second);
    }

    /**
     * Ensures {@link TaskService#registerInstance(TaskService)} migrates the legacy
     * in-memory tasks into the JPA-backed store when the Spring bean initializes later.
     */
    @Test
    void springBeanRegistrationMigratesLegacyTasks() throws Exception {
        TaskService legacy = createLegacyService();
        legacy.addTask(new Task("T-77", "Legacy Task", "Cutover fixture"));

        CapturingTaskStore store = new CapturingTaskStore();
        TaskService springBean = new TaskService(store);

        assertThat(store.findById("T-77")).isPresent();
        assertThat(TaskService.getInstance()).isSameAs(springBean);
    }

    /**
     * Mirrors the duplicate-id behavior for the legacy in-memory store so PIT cannot change the boolean return or
     * skip the DuplicateResourceException branch.
     */
    @Test
    void legacyInsertReturnsTrueAndDuplicateThrows() throws Exception {
        TaskService legacy = createLegacyService();
        Task task = new Task("LEG-T", "Legacy Task", "Fallback branch");

        assertThat(legacy.addTask(task)).isTrue();
        assertThat(legacy.getTaskById("LEG-T")).isPresent();
        assertThatThrownBy(() -> legacy.addTask(task))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("LEG-T");
    }

    @Test
    void legacyAllUsersListingDoesNotRequireSecurityContext() throws Exception {
        TaskService legacy = createLegacyService();
        legacy.addTask(new Task("LEG-1", "Legacy One", "First entry"));
        legacy.addTask(new Task("LEG-2", "Legacy Two", "Second entry"));

        assertThat(legacy.getAllTasksAllUsers())
                .extracting(Task::getTaskId)
                .containsExactlyInAnyOrder("LEG-1", "LEG-2");
    }

    private static void setInstance(final TaskService newInstance) throws Exception {
        Field instanceField = TaskService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, newInstance);
    }

    /**
     * Builds a synthetic legacy instance so tests can verify migration without
     * depending on the real Spring context.
     */
    private static TaskService createLegacyService() throws Exception {
        Constructor<TaskService> constructor = TaskService.class
                .getDeclaredConstructor(TaskStore.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new InMemoryTaskStore(), true);
    }

    private static final class CapturingTaskStore implements TaskStore {
        private final Map<String, Task> database = new LinkedHashMap<>();

        @Override
        public boolean existsById(final String id) {
            return database.containsKey(id);
        }

        @Override
        public void save(final Task aggregate) {
            database.put(aggregate.getTaskId(), aggregate.copy());
        }

        @Override
        public Optional<Task> findById(final String id) {
            return Optional.ofNullable(database.get(id)).map(Task::copy);
        }

        @Override
        public List<Task> findAll() {
            final List<Task> tasks = new ArrayList<>();
            database.values().forEach(task -> tasks.add(task.copy()));
            return tasks;
        }

        @Override
        public boolean deleteById(final String id) {
            return database.remove(id) != null;
        }

        @Override
        public void deleteAll() {
            database.clear();
        }
    }
}
