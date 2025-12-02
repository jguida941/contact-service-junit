package contactapp;

import contactapp.api.TaskController;
import contactapp.api.dto.TaskResponse;
import contactapp.domain.Task;
import contactapp.service.TaskService;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests that exercise {@link TaskController#getAll} so
 * PIT observes both the admin and non-admin branches.
 */
class TaskControllerUnitTest {

    private final TaskService taskService = mock(TaskService.class);
    private final TaskController controller = new TaskController(taskService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAll_withAllFlagThrowsForNonAdmins() {
        authenticate(false);

        assertThatThrownBy(() -> controller.getAll(true, null, null, false, null, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only administrators");
    }

    @Test
    void getAll_withAllFlagReturnsResultsForAdmins() {
        authenticate(true);
        when(taskService.getAllTasksAllUsers()).thenReturn(List.of(
                new Task("t-1", "Admin Task", "Visible to admins")));

        final List<TaskResponse> responses = controller.getAll(true, null, null, false, null, null);

        verify(taskService).getAllTasksAllUsers();
        assertThat(responses).singleElement().satisfies(response ->
                assertThat(response.id()).isEqualTo("t-1"));
    }

    @Test
    void getAll_filtersByDueBefore() {
        final LocalDate cutoff = LocalDate.now().plusDays(7);
        when(taskService.getTasksDueBefore(cutoff)).thenReturn(List.of(
                new Task("due-1", "Due Soon", "Desc")));

        // Cover the dueBefore branch and mapping
        final List<TaskResponse> responses = controller.getAll(false, null, cutoff, false, null, null);

        verify(taskService).getTasksDueBefore(cutoff);
        assertThat(responses).singleElement().extracting(TaskResponse::id).isEqualTo("due-1");
    }

    @Test
    void getAll_filtersByOverdue() {
        when(taskService.getOverdueTasks()).thenReturn(List.of(
                new Task("ov-1", "Overdue", "Desc")));

        // Cover the overdue=true branch
        final List<TaskResponse> responses = controller.getAll(false, null, null, true, null, null);

        verify(taskService).getOverdueTasks();
        assertThat(responses).singleElement().extracting(TaskResponse::id).isEqualTo("ov-1");
    }

    @Test
    void getAll_filtersByProjectId() {
        when(taskService.getTasksByProjectId("proj-1")).thenReturn(List.of(
                new Task("p-1", "Proj Task", "Desc")));

        // Cover the projectId filter branch
        final List<TaskResponse> responses = controller.getAll(false, null, null, false, "proj-1", null);

        verify(taskService).getTasksByProjectId("proj-1");
        assertThat(responses).singleElement().extracting(TaskResponse::id).isEqualTo("p-1");
    }

    @Test
    void getAll_filtersByAssigneeId() {
        when(taskService.getTasksByAssigneeId(42L)).thenReturn(List.of(
                new Task("a-1", "Assigned", "Desc")));

        // Cover the assigneeId filter branch
        final List<TaskResponse> responses = controller.getAll(false, null, null, false, null, 42L);

        verify(taskService).getTasksByAssigneeId(42L);
        assertThat(responses).singleElement().extracting(TaskResponse::id).isEqualTo("a-1");
    }

    @Test
    void getAll_defaultsToCurrentUsersTasksWhenNoFilters() {
        when(taskService.getAllTasks()).thenReturn(List.of(
                new Task("def-1", "Default", "Desc")));

        // Cover default branch (no filters, all=false)
        final List<TaskResponse> responses = controller.getAll(false, null, null, false, null, null);

        verify(taskService).getAllTasks();
        assertThat(responses).singleElement().extracting(TaskResponse::id).isEqualTo("def-1");
    }

    private void authenticate(final boolean admin) {
        final var authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        final var authentication = new UsernamePasswordAuthenticationToken(
                "user", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
