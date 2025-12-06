package contactapp.persistence.mapper;

import contactapp.domain.Task;
import contactapp.persistence.entity.TaskEntity;
import contactapp.security.User;
import org.springframework.stereotype.Component;

/**
 * Maps between {@link Task} domain objects and {@link TaskEntity}.
 */
@Component
public class TaskMapper {

    /**
     * Converts a domain Task to an entity, associating it with the given user.
     *
     * @param domain the domain task
     * @param user the user who owns this task
     * @return the entity representation
     */
    public TaskEntity toEntity(final Task domain, final User user) {
        if (domain == null) {
            return null;
        }
        final TaskEntity entity = new TaskEntity(
                domain.getTaskId(),
                domain.getName(),
                domain.getDescription(),
                domain.getStatus(),
                domain.getDueDate(),
                user);
        entity.setProjectId(domain.getProjectId());
        entity.setAssigneeId(domain.getAssigneeId());
        entity.setArchived(domain.isArchived());
        return entity;
    }

    /**
     * Converts a domain Task to an entity.
     *
     * @param domain the domain task
     * @return the entity representation
     * @deprecated Use {@link #toEntity(Task, User)} instead
     */
    @Deprecated
    public TaskEntity toEntity(final Task domain) {
        if (domain == null) {
            return null;
        }
        throw new UnsupportedOperationException(
                "Use toEntity(Task, User) instead. This method requires a User parameter.");
    }

    /**
     * Converts an entity to a domain Task using reconstitution.
     *
     * <p>Uses {@link Task#reconstitute} to bypass the "not in past" validation for dueDate,
     * allowing tasks with overdue dates to be loaded from the database successfully.
     *
     * @param entity the entity to convert
     * @return the domain task, or null if entity is null
     */
    public Task toDomain(final TaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return Task.reconstitute(
                entity.getTaskId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getDueDate(),
                entity.getProjectId(),
                entity.getAssigneeId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.isArchived());
    }

    /**
     * Updates an existing entity with the latest mutable values from the domain aggregate.
     *
     * @param target entity to mutate
     * @param source validated domain task
     */
    public void updateEntity(final TaskEntity target, final Task source) {
        if (target == null) {
            throw new IllegalArgumentException("target entity must not be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("source task must not be null");
        }
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setStatus(source.getStatus());
        target.setDueDate(source.getDueDate());
        target.setProjectId(source.getProjectId());
        target.setAssigneeId(source.getAssigneeId());
        target.setArchived(source.isArchived());
    }
}
