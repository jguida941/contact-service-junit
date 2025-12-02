package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.domain.Project;
import contactapp.domain.ProjectStatus;
import contactapp.persistence.store.InMemoryProjectStore;
import contactapp.persistence.store.ProjectStore;
import contactapp.security.TestUserSetup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import contactapp.support.PostgresContainerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors {@link ContactServiceLegacyTest}/{@link TaskServiceLegacyTest} for {@link ProjectService}
 * so the singleton bridge + legacy fallback logic remain covered by PIT.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Isolated
class ProjectServiceLegacyTest extends PostgresContainerSupport {

    @Autowired
    private TestUserSetup testUserSetup;

    @Autowired
    private ProjectService springService;

    @BeforeEach
    void resetSingleton() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        testUserSetup.cleanup();
        testUserSetup.setupTestUser();
        springService.clearAllProjects();
        setInstance(null);
    }

    @AfterEach
    void cleanSingleton() throws Exception {
        setInstance(null);
    }

    @Test
    void coldStartReturnsInMemoryStore() {
        ProjectService legacy = ProjectService.getInstance();
        assertThat(legacy).isNotNull();

        Project project = new Project("legacy1", "Legacy", "Fallback", ProjectStatus.ACTIVE);
        assertThat(legacy.addProject(project)).isTrue();
        assertThat(legacy.getProjectById("legacy1")).isPresent();
    }

    @Test
    void springBeanRegistrationMigratesLegacyProjects() throws Exception {
        ProjectService legacy = createLegacyService();
        legacy.addProject(new Project("proj-1", "Temp", "Cutover", ProjectStatus.ACTIVE));

        CapturingProjectStore store = new CapturingProjectStore();
        Constructor<ProjectService> ctor =
                ProjectService.class.getDeclaredConstructor(ProjectStore.class, boolean.class);
        ctor.setAccessible(true);
        ProjectService springBean = ctor.newInstance(store, false);

        assertThat(store.findById("proj-1")).isPresent();
        assertThat(ProjectService.getInstance()).isSameAs(springBean);
    }

    @Test
    void legacyInsertReturnsTrueAndDuplicateThrows() throws Exception {
        ProjectService legacy = createLegacyService();
        Project project = new Project("dup-pro1", "Legacy", "Fallback", ProjectStatus.ACTIVE);

        assertThat(legacy.addProject(project)).isTrue();
        assertThatThrownBy(() -> legacy.addProject(project))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("dup-pro1");
    }

    private static ProjectService createLegacyService() throws Exception {
        Constructor<ProjectService> constructor =
                ProjectService.class.getDeclaredConstructor(ProjectStore.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new InMemoryProjectStore(), true);
    }

    private static void setInstance(final ProjectService newInstance) throws Exception {
        Field instanceField = ProjectService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, newInstance);
    }

    private static final class CapturingProjectStore implements ProjectStore {
        private final java.util.LinkedHashMap<String, Project> data = new java.util.LinkedHashMap<>();

        @Override
        public boolean existsById(final String id) {
            return data.containsKey(id);
        }

        @Override
        public void save(final Project aggregate) {
            data.put(aggregate.getProjectId(), aggregate.copy());
        }

        @Override
        public java.util.Optional<Project> findById(final String id) {
            return java.util.Optional.ofNullable(data.get(id)).map(Project::copy);
        }

        @Override
        public java.util.List<Project> findAll() {
            return data.values().stream().map(Project::copy).toList();
        }

        @Override
        public boolean deleteById(final String id) {
            return data.remove(id) != null;
        }

        @Override
        public void deleteAll() {
            data.clear();
        }
    }
}
