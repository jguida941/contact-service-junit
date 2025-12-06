package contactapp.api;

import contactapp.api.dto.ErrorResponse;
import contactapp.api.dto.TaskRequest;
import contactapp.api.dto.TaskResponse;
import contactapp.api.exception.ResourceNotFoundException;
import contactapp.domain.Task;
import contactapp.service.TaskService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import static contactapp.domain.Validation.MAX_ID_LENGTH;

/**
 * REST controller for Task CRUD operations.
 *
 * <p>Provides endpoints at {@code /api/v1/tasks} per ADR-0016.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/v1/tasks - Create a new task (201 Created)</li>
 *   <li>GET /api/v1/tasks - List all tasks (200 OK)</li>
 *   <li>GET /api/v1/tasks/{id} - Get task by ID (200 OK / 404 Not Found)</li>
 *   <li>PUT /api/v1/tasks/{id} - Update task (200 OK / 404 Not Found)</li>
 *   <li>DELETE /api/v1/tasks/{id} - Delete task (204 No Content / 404 Not Found)</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * <p>Uses two layers of validation:
 * <ol>
 *   <li>Bean Validation on request DTOs ({@code @Valid})</li>
 *   <li>Domain validation via {@link contactapp.domain.Validation} in Task constructor</li>
 * </ol>
 *
 * @see TaskRequest
 * @see TaskResponse
 * @see TaskService
 */
@RestController
@RequestMapping(value = "/api/v1/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tasks", description = "Task CRUD operations")
@SecurityRequirement(name = "bearerAuth")
@Validated
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed singleton service is intentionally stored without copy"
)
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class TaskController {

    private final TaskService taskService;

    /**
     * Creates a new TaskController with the given service.
     *
     * @param taskService the service for task operations
     */
    public TaskController(final TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Creates a new task.
     *
     * @param request the task data
     * @return the created task
     * @throws contactapp.api.exception.DuplicateResourceException if a task with the given ID already exists
     */
    @Operation(summary = "Create a new task")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Task with this ID already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody final TaskRequest request) {
        final Task task = new Task(
                request.id(),
                request.name(),
                request.description(),
                request.status(),
                request.dueDate()
        );
        task.setProjectId(request.projectId());
        task.setAssigneeId(request.assigneeId());

        taskService.addTask(task);

        return TaskResponse.from(task);
    }

    /**
     * Returns all tasks for the authenticated user.
     *
     * <p>For regular users, returns only their tasks.
     * For ADMIN users with {@code ?all=true}, returns all tasks across all users.
     *
     * <p>Supports filtering by:
     * <ul>
     *   <li>{@code ?status=TODO|IN_PROGRESS|DONE} - filter by status</li>
     *   <li>{@code ?dueBefore=2024-12-31} - filter by due date</li>
     *   <li>{@code ?overdue=true} - show only overdue tasks</li>
     *   <li>{@code ?projectId=proj-1} - filter by associated project
     *       (use {@code ?projectId=none} for unassigned tasks)</li>
     *   <li>{@code ?assigneeId=550e8400-e29b-41d4-a716-446655440000} - filter by assignee user ID (UUID format)</li>
     * </ul>
     *
     * @param all if true and user is ADMIN, returns all tasks across all users
     * @param status filter by task status
     * @param dueBefore filter tasks due before this date
     * @param overdue if true, show only overdue tasks
     * @param projectId filter by associated project ID
     * @param assigneeId filter by assignee user ID
     * @return list of tasks
     */
    @Operation(summary = "Get all tasks",
            description = "Returns tasks for the authenticated user. "
                    + "ADMIN users can pass ?all=true to see all tasks across all users. "
                    + "Supports filtering by status, dueBefore date, overdue flag, projectId, and assigneeId.")
    @ApiResponse(responseCode = "200", description = "List of tasks")
    @GetMapping
    public List<TaskResponse> getAll(
            @Parameter(description = "If true and user is ADMIN, returns all tasks")
            @RequestParam(required = false, defaultValue = "false") final boolean all,
            @Parameter(description = "Filter by task status")
            @RequestParam(required = false) final contactapp.domain.TaskStatus status,
            @Parameter(description = "Filter tasks due before this date (ISO 8601 format: YYYY-MM-DD)")
            @RequestParam(required = false) final java.time.LocalDate dueBefore,
            @Parameter(description = "If true, show only overdue tasks")
            @RequestParam(required = false, defaultValue = "false") final boolean overdue,
            @Parameter(description = "Filter by associated project ID")
            @RequestParam(required = false) @Size(max = MAX_ID_LENGTH) final String projectId,
            @Parameter(description = "Filter by assignee user ID")
            @RequestParam(required = false) final UUID assigneeId) {

        // Handle filtering
        if (status != null) {
            return taskService.getTasksByStatus(status).stream()
                    .map(TaskResponse::from)
                    .toList();
        }

        if (dueBefore != null) {
            return taskService.getTasksDueBefore(dueBefore).stream()
                    .map(TaskResponse::from)
                    .toList();
        }

        if (overdue) {
            return taskService.getOverdueTasks().stream()
                    .map(TaskResponse::from)
                    .toList();
        }

        if (projectId != null) {
            if ("none".equalsIgnoreCase(projectId)) {
                return taskService.getUnassignedTasks().stream()
                        .map(TaskResponse::from)
                        .toList();
            }
            return taskService.getTasksByProjectId(projectId).stream()
                    .map(TaskResponse::from)
                    .toList();
        }

        if (assigneeId != null) {
            return taskService.getTasksByAssigneeId(assigneeId).stream()
                    .map(TaskResponse::from)
                    .toList();
        }

        // No filters - return all tasks
        if (all) {
            // Verify caller has ADMIN role before returning all users' data
            final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            final boolean isAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (!isAdmin) {
                throw new AccessDeniedException("Only administrators can access all users' tasks");
            }
            return taskService.getAllTasksAllUsers().stream()
                    .map(TaskResponse::from)
                    .toList();
        }
        return taskService.getAllTasks().stream()
                .map(TaskResponse::from)
                .toList();
    }

    /**
     * Returns a task by ID.
     *
     * @param id the task ID
     * @return the task
     * @throws ResourceNotFoundException if no task with the given ID exists
     */
    @Operation(summary = "Get task by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public TaskResponse getById(
            @Parameter(
                    description = "Task ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        return taskService.getTaskById(id)
                .map(TaskResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task not found: " + id));
    }

    /**
     * Updates an existing task.
     *
     * @param id      the task ID (from path)
     * @param request the updated task data
     * @return the updated task
     * @throws ResourceNotFoundException if no task with the given ID exists
     */
    @Operation(summary = "Update an existing task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TaskResponse update(
            @Parameter(
                    description = "Task ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id,
            @Valid @RequestBody final TaskRequest request) {

        if (!taskService.updateTask(
                id,
                request.name(),
                request.description(),
                request.status(),
                request.dueDate(),
                request.projectId(),
                request.assigneeId())) {
            throw new ResourceNotFoundException("Task not found: " + id);
        }

        return getById(id);
    }

    /**
     * Deletes a task by ID.
     *
     * @param id the task ID
     * @throws ResourceNotFoundException if no task with the given ID exists
     */
    @Operation(summary = "Delete a task")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(
                    description = "Task ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        if (!taskService.deleteTask(id)) {
            throw new ResourceNotFoundException("Task not found: " + id);
        }
    }

    /**
     * Archives a task by ID.
     *
     * @param id the task ID
     * @return the archived task
     * @throws ResourceNotFoundException if no task with the given ID exists
     */
    @Operation(summary = "Archive a task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task archived",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/archive")
    public TaskResponse archive(
            @Parameter(
                    description = "Task ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        return taskService.archiveTask(id)
                .map(TaskResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
    }

    /**
     * Unarchives a task by ID.
     *
     * @param id the task ID
     * @return the unarchived task
     * @throws ResourceNotFoundException if no task with the given ID exists
     */
    @Operation(summary = "Unarchive a task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task unarchived",
                    content = @Content(schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/unarchive")
    public TaskResponse unarchive(
            @Parameter(
                    description = "Task ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        return taskService.unarchiveTask(id)
                .map(TaskResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
    }
}
