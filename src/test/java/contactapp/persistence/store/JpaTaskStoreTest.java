package contactapp.persistence.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import contactapp.domain.Task;
import contactapp.persistence.entity.TaskEntity;
import contactapp.persistence.mapper.TaskMapper;
import contactapp.persistence.repository.TaskRepository;
import contactapp.security.User;
import contactapp.support.TestUserFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link JpaTaskStore} delegations that previously showed up as
 * NO_COVERAGE in PIT. By asserting each branch calls the repository/mapper we
 * make it impossible for PIT to replace the boolean results without detection.
 */
@ExtendWith(MockitoExtension.class)
class JpaTaskStoreTest {

    @Mock
    private TaskRepository repository;
    @Mock
    private TaskMapper mapper;

    private JpaTaskStore store;

    @BeforeEach
    void setUp() {
        store = new JpaTaskStore(repository, mapper);
    }

    @Test
    void existsById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] Deprecated path must keep throwing until all callers supply a user.
        assertThatThrownBy(() -> store.existsById("task-1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] Exercise the legacy overload so we know it continues throwing.
        assertThatThrownBy(() -> store.findById("task-2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deleteById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] De-scoped delete overload remains intentionally unsupported.
        assertThatThrownBy(() -> store.deleteById("task-3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void existsById_withUserDelegatesToRepository() {
        final User owner = TestUserFactory.createUser("repo-task");
        when(repository.existsByTaskIdAndUser("task-5", owner)).thenReturn(true);

        assertThat(store.existsById("task-5", owner)).isTrue();
    }

    /**
     * Covers the false path for existsById(User) so PIT can't always return true.
     */
    @Test
    void existsById_withUserReturnsFalseWhenRepositoryReturnsFalse() {
        final User owner = TestUserFactory.createUser("repo-task-missing");
        when(repository.existsByTaskIdAndUser("missing", owner)).thenReturn(false);

        assertThat(store.existsById("missing", owner)).isFalse();
    }

    @Test
    void findById_withUserUsesMapper() {
        final User owner = TestUserFactory.createUser("repo-task-find");
        final TaskEntity entity = mock(TaskEntity.class);
        final Task domain = new Task("task-6", "Task", "Desc");
        when(repository.findByTaskIdAndUser("task-6", owner)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(store.findById("task-6", owner)).contains(domain);
    }

    @Test
    void deleteById_withUserReturnsTrueWhenRowsDeleted() {
        final User owner = TestUserFactory.createUser("repo-task-delete");
        when(repository.deleteByTaskIdAndUser("task-7", owner)).thenReturn(1);

        assertThat(store.deleteById("task-7", owner)).isTrue();
    }

    @Test
    void deleteById_withUserReturnsFalseWhenNoRowsDeleted() {
        final User owner = TestUserFactory.createUser("repo-task-delete-none");
        when(repository.deleteByTaskIdAndUser("nonexistent", owner)).thenReturn(0);

        assertThat(store.deleteById("nonexistent", owner)).isFalse();
    }

    // --- Null parameter validation tests ---

    @Test
    void existsById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.existsById("t-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void findById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.findById("t-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void findAll_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.findAll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void deleteById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.deleteById("t-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void save_withNullAggregateThrowsIllegalArgument() {
        final User owner = TestUserFactory.createUser("task-store-null-agg");
        assertThatThrownBy(() -> store.save(null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("task aggregate must not be null");
    }

    @Test
    void save_withNullUserThrowsIllegalArgument() {
        final Task task = new Task("t-1", "Name", "Desc");
        assertThatThrownBy(() -> store.save(task, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    @SuppressWarnings("deprecation")
    void save_withoutUserThrowsUnsupportedOperation() {
        final Task task = new Task("t-1", "Name", "Desc");
        // codeql[java/avoid-deprecated-apis] Validate the deprecated no-user method still throws.
        assertThatThrownBy(() -> store.save(task))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void insert_withNullAggregateThrowsIllegalArgument() {
        final User owner = TestUserFactory.createUser("task-store-insert-null");
        assertThatThrownBy(() -> store.insert(null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("task aggregate must not be null");
    }

    @Test
    void insert_withNullUserThrowsIllegalArgument() {
        final Task task = new Task("t-1", "Name", "Desc");
        assertThatThrownBy(() -> store.insert(task, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    // --- Save and insert behavior tests ---

    @Test
    void save_updatesExistingTask() {
        final User owner = TestUserFactory.createUser("task-store-update");
        final Task task = new Task("t-upd", "Updated", "New Desc");
        final TaskEntity existingEntity = mock(TaskEntity.class);
        when(repository.findByTaskIdAndUser("t-upd", owner)).thenReturn(Optional.of(existingEntity));

        store.save(task, owner);

        verify(mapper).updateEntity(existingEntity, task);
        verify(repository).save(existingEntity);
    }

    @Test
    void save_insertsNewTask() {
        final User owner = TestUserFactory.createUser("task-store-insert-new");
        final Task task = new Task("t-new", "New", "Desc");
        final TaskEntity newEntity = mock(TaskEntity.class);
        when(repository.findByTaskIdAndUser("t-new", owner)).thenReturn(Optional.empty());
        when(mapper.toEntity(task, owner)).thenReturn(newEntity);

        store.save(task, owner);

        verify(repository).save(newEntity);
    }

    @Test
    void insert_savesNewEntity() {
        final User owner = TestUserFactory.createUser("task-store-insert");
        final Task task = new Task("t-ins", "Insert", "Test");
        final TaskEntity entity = mock(TaskEntity.class);
        when(mapper.toEntity(task, owner)).thenReturn(entity);

        store.insert(task, owner);

        verify(repository).save(entity);
    }

    // --- findAll tests ---

    @Test
    void findAll_withUserReturnsMappedTasks() {
        final User owner = TestUserFactory.createUser("task-store-findall");
        final TaskEntity entity1 = mock(TaskEntity.class);
        final TaskEntity entity2 = mock(TaskEntity.class);
        final Task task1 = new Task("t-1", "Task1", "Desc1");
        final Task task2 = new Task("t-2", "Task2", "Desc2");
        when(repository.findByUser(owner)).thenReturn(List.of(entity1, entity2));
        when(mapper.toDomain(entity1)).thenReturn(task1);
        when(mapper.toDomain(entity2)).thenReturn(task2);

        final List<Task> result = store.findAll(owner);

        assertThat(result).containsExactly(task1, task2);
    }

    @Test
    void findAll_adminReturnsMappedTasks() {
        final TaskEntity entity = mock(TaskEntity.class);
        final Task task = new Task("t-admin", "Admin", "View");
        when(repository.findAll()).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(task);

        final List<Task> result = store.findAll();

        assertThat(result).containsExactly(task);
    }

    @Test
    void findById_withUserReturnsEmptyWhenNotFound() {
        final User owner = TestUserFactory.createUser("task-store-find-empty");
        when(repository.findByTaskIdAndUser("missing", owner)).thenReturn(Optional.empty());

        assertThat(store.findById("missing", owner)).isEmpty();
    }

    @Test
    void deleteAll_delegatesToRepository() {
        store.deleteAll();

        verify(repository).deleteAll();
    }
}
