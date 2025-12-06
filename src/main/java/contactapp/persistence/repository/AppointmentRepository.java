package contactapp.persistence.repository;

import contactapp.persistence.entity.AppointmentEntity;
import contactapp.security.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link AppointmentEntity}.
 *
 * <p>Supports per-user data isolation by filtering appointments by user_id.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, Long> {

    /**
     * Finds all appointments belonging to a specific user.
     *
     * @param user the user who owns the appointments
     * @return list of appointments for the given user
     */
    List<AppointmentEntity> findByUser(User user);

    /**
     * Finds an appointment by ID and user.
     *
     * @param appointmentId the appointment ID
     * @param user the user who owns the appointment
     * @return optional containing the appointment if found
     */
    Optional<AppointmentEntity> findByAppointmentIdAndUser(String appointmentId, User user);

    /**
     * Finds an appointment by ID without user scoping (legacy support).
     *
     * @deprecated Use {@link #findByAppointmentIdAndUser(String, User)} for tenant isolation
     */
    @Deprecated(forRemoval = true)
    Optional<AppointmentEntity> findByAppointmentId(String appointmentId);

    /**
     * Checks if an appointment exists by ID and user.
     *
     * @param appointmentId the appointment ID
     * @param user the user who owns the appointment
     * @return true if the appointment exists for the given user
     */
    boolean existsByAppointmentIdAndUser(String appointmentId, User user);

    /**
     * Checks if any appointment exists with the given ID.
     *
     * @deprecated Use {@link #existsByAppointmentIdAndUser(String, User)} instead
     */
    @Deprecated(forRemoval = true)
    boolean existsByAppointmentId(String appointmentId);

    /**
     * Deletes an appointment by ID and user.
     *
     * @param appointmentId the appointment ID
     * @param user the user who owns the appointment
     * @return number of deleted records (0 or 1)
     */
    @Modifying
    @Transactional
    int deleteByAppointmentIdAndUser(String appointmentId, User user);

    /**
     * Finds all appointments associated with a specific project for a user.
     *
     * <p>This query method avoids in-memory filtering by pushing the filter
     * down to the database level where indexes can be utilized.
     *
     * @param projectId the project ID to filter by
     * @param user the user who owns the appointments
     * @return list of appointments for the specified project
     */
    List<AppointmentEntity> findByProjectIdAndUser(String projectId, User user);

    /**
     * Finds all appointments associated with a specific task for a user.
     *
     * <p>This query method avoids in-memory filtering by pushing the filter
     * down to the database level where indexes can be utilized.
     *
     * @param taskId the task ID to filter by
     * @param user the user who owns the appointments
     * @return list of appointments for the specified task
     */
    List<AppointmentEntity> findByTaskIdAndUser(String taskId, User user);
}
