package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.domain.Task;
import contactapp.persistence.store.TaskStore;
import contactapp.security.Role;
import contactapp.security.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceMutationTest {

    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOBiZFn0eTD1yQbFq6Z9erjRzCQXDpG7W";

    @AfterEach
    void resetSingleton() {
        ReflectionTestUtils.setField(TaskService.class, "instance", null);
        SecurityContextHolder.clearContext();
    }

    @Test
    void addTaskThrowsDuplicateWhenStoreReportsExistingId() {
        TaskStore store = mock(TaskStore.class);
        TaskService service = new TaskService(store);
        Task task = new Task("task-dup", "Persist Failure", "Testing duplicate guard");

        when(store.existsById("task-dup")).thenReturn(true);

        assertThatThrownBy(() -> service.addTask(task))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void getAllTasksAllUsersThrowsForNonAdmins() {
        // Use legacyStore=false to enable security checks (1-arg constructor sets legacyStore=true)
        final TaskService service = new TaskService(mock(TaskStore.class), false);
        authenticate(Role.USER);

        assertThatThrownBy(service::getAllTasksAllUsers)
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Only ADMIN users");
    }

    @Test
    void getAllTasksAllUsersReturnsCopiesForAdmins() {
        final TaskStore store = mock(TaskStore.class);
        final TaskService service = new TaskService(store);
        authenticate(Role.ADMIN);
        final Task stored = new Task("id-1", "Admin", "Data");
        when(store.findAll()).thenReturn(List.of(stored));

        final List<Task> results = service.getAllTasksAllUsers();

        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isNotSameAs(stored);
    }

    @Test
    void deleteTaskReturnsStoreResult() {
        final TaskStore store = mock(TaskStore.class);
        final TaskService service = new TaskService(store);
        when(store.deleteById("known")).thenReturn(true);

        assertThat(service.deleteTask("known")).isTrue();
        assertThat(service.deleteTask("missing")).isFalse();
    }

    @Test
    void updateTaskReturnsFalseWhenIdMissing() {
        final TaskStore store = mock(TaskStore.class);
        final TaskService service = new TaskService(store);
        when(store.findById("unknown")).thenReturn(Optional.empty());

        assertThat(service.updateTask("unknown", "Name", "Desc")).isFalse();
    }

    @Test
    void updateTaskMutatesExistingRecord() {
        final TaskStore store = mock(TaskStore.class);
        final TaskService service = new TaskService(store);
        final Task existing = new Task("known", "Orig", "Desc");
        when(store.findById("known")).thenReturn(Optional.of(existing));

        final boolean updated = service.updateTask("known", "Updated", "New");

        assertThat(updated).isTrue();
        final ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(store).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Updated");
        assertThat(captor.getValue().getDescription()).isEqualTo("New");
    }

    @Test
    void getTaskByIdReturnsCopyFromStore() {
        final TaskStore store = mock(TaskStore.class);
        final TaskService service = new TaskService(store);
        final Task stored = new Task("copy", "Name", "Desc");
        when(store.findById("copy")).thenReturn(Optional.of(stored));

        assertThat(service.getTaskById("copy"))
                .isPresent()
                .get()
                .isNotSameAs(stored);
    }

    private void authenticate(final Role role) {
        final User user = new User(role.name().toLowerCase(), role.name().toLowerCase() + "@example.com", PASSWORD_HASH, role);
        final var token = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
