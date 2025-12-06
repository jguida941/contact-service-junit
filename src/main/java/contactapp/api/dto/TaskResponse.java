package contactapp.api.dto;

import contactapp.domain.Task;
import contactapp.domain.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Response DTO for Task data returned by the API.
 *
 * <p>Provides a clean separation between the domain object and the API contract.
 * Uses a factory method to convert from the domain object.
 *
 * @param id          the task's unique identifier
 * @param name        the task name
 * @param description the task description
 * @param status      the task status
 * @param dueDate     the task due date (nullable)
 * @param projectId   the associated project ID (nullable)
 * @param assigneeId  the assignee user ID (nullable)
 * @param createdAt   timestamp when the task was created
 * @param updatedAt   timestamp when the task was last updated
 * @param isOverdue   true if the task has a due date that is in the past
 * @param archived    true if the task is archived
 */
public record TaskResponse(
        String id,
        String name,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        String projectId,
        UUID assigneeId,
        Instant createdAt,
        Instant updatedAt,
        boolean isOverdue,
        boolean archived
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
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getProjectId(),
                task.getAssigneeId(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.isOverdue(),
                task.isArchived()
        );
    }
}
