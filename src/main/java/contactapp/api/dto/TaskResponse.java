package contactapp.api.dto;

import contactapp.domain.Task;
import java.util.Objects;

/**
 * Response DTO for Task data returned by the API.
 *
 * <p>Provides a clean separation between the domain object and the API contract.
 * Uses a factory method to convert from the domain object.
 *
 * @param id          the task's unique identifier
 * @param name        the task name
 * @param description the task description
 */
public record TaskResponse(
        String id,
        String name,
        String description
) {
    /**
     * Creates a TaskResponse from a Task domain object.
     *
     * @param task the domain object to convert (must not be null)
     * @return a new TaskResponse with the task's data
     * @throws NullPointerException if task is null
     */
    public static TaskResponse from(final Task task) {
        Objects.requireNonNull(task, "task must not be null");
        return new TaskResponse(
                task.getTaskId(),
                task.getName(),
                task.getDescription()
        );
    }
}
