package contactapp.persistence.repository;

import contactapp.domain.TaskStatus;
import contactapp.persistence.entity.TaskEntity;
import contactapp.security.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link TaskEntity}.
 *
 * <p>Supports per-user data isolation by filtering tasks by user_id.
 */
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    /**
     * Finds all tasks belonging to a specific user.
     *
     * @param user the user who owns the tasks
     * @return list of tasks for the given user
     */
    List<TaskEntity> findByUser(User user);

    /**
     * Finds a task by ID and user.
     *
     * @param taskId the task ID
     * @param user the user who owns the task
     * @return optional containing the task if found
     */
    Optional<TaskEntity> findByTaskIdAndUser(String taskId, User user);

    /**
     * Finds a task by ID without scoping by user (legacy).
     *
     * @deprecated Use {@link #findByTaskIdAndUser(String, User)} to enforce tenant isolation
     */
    @Deprecated(forRemoval = true)
    Optional<TaskEntity> findByTaskId(String taskId);

    /**
     * Checks if a task exists by ID and user.
     *
     * @param taskId the task ID
     * @param user the user who owns the task
     * @return true if the task exists for the given user
     */
    boolean existsByTaskIdAndUser(String taskId, User user);

    /**
     * Checks if any task exists with the given ID across all users.
     *
     * @deprecated Use {@link #existsByTaskIdAndUser(String, User)} to enforce tenant isolation
     */
    @Deprecated(forRemoval = true)
    boolean existsByTaskId(String taskId);

    /**
     * Deletes a task by ID and user.
     *
     * @param taskId the task ID
     * @param user the user who owns the task
     * @return number of deleted records (0 or 1)
     */
    @Modifying
    @Transactional
    int deleteByTaskIdAndUser(String taskId, User user);

    /**
     * Finds all tasks for a user with a specific status.
     *
     * @param user the user who owns the tasks
     * @param status the task status to filter by
     * @return list of tasks matching the status
     */
    List<TaskEntity> findByUserAndStatus(User user, TaskStatus status);

    /**
     * Finds all tasks for a user with due date before the specified date.
     *
     * @param user the user who owns the tasks
     * @param date the date threshold
     * @return list of tasks with due date before the specified date
     */
    List<TaskEntity> findByUserAndDueDateBefore(User user, LocalDate date);

    /**
     * Finds all tasks for a user with due date between two dates.
     *
     * @param user the user who owns the tasks
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of tasks with due date in the specified range
     */
    List<TaskEntity> findByUserAndDueDateBetween(User user, LocalDate startDate, LocalDate endDate);

    /**
     * Finds all tasks for a user that have no project assigned.
     *
     * @param user the user who owns the tasks
     * @return list of tasks with null projectId (unassigned to any project)
     */
    List<TaskEntity> findByUserAndProjectIdIsNull(User user);
}
