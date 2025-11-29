package contactapp.api;

import contactapp.api.dto.TaskRequest;
import contactapp.api.dto.TaskResponse;
import contactapp.api.exception.DuplicateResourceException;
import contactapp.api.exception.ResourceNotFoundException;
import contactapp.domain.Task;
import contactapp.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/api/v1/tasks")
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
     * @throws DuplicateResourceException if a task with the given ID already exists
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody final TaskRequest request) {
        final Task task = new Task(
                request.id(),
                request.name(),
                request.description()
        );

        if (!taskService.addTask(task)) {
            throw new DuplicateResourceException(
                    "Task with id '" + request.id() + "' already exists");
        }

        return TaskResponse.from(task);
    }

    /**
     * Returns all tasks.
     *
     * @return list of all tasks
     */
    @GetMapping
    public List<TaskResponse> getAll() {
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
    @GetMapping("/{id}")
    public TaskResponse getById(@PathVariable final String id) {
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
    @PutMapping("/{id}")
    public TaskResponse update(
            @PathVariable final String id,
            @Valid @RequestBody final TaskRequest request) {

        if (!taskService.updateTask(
                id,
                request.name(),
                request.description())) {
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
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable final String id) {
        if (!taskService.deleteTask(id)) {
            throw new ResourceNotFoundException("Task not found: " + id);
        }
    }
}
