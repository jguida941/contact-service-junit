package contactapp.persistence.entity;

import contactapp.domain.Validation;
import contactapp.security.User;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Persistence representation for {@link contactapp.domain.Appointment}.
 *
 * <p>Stores timestamps as {@link Instant} so they map cleanly to
 * {@code TIMESTAMP WITH TIME ZONE}.
 */
@Entity
@Table(
        name = "appointments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_appointments_appointment_id_user_id",
                columnNames = {"appointment_id", "user_id"}))
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JPA entity returns User reference for ORM relationship; "
                + "entity lifecycle is managed by Hibernate persistence context")
public class AppointmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "appointment_id", length = Validation.MAX_ID_LENGTH, nullable = false)
    private String appointmentId;

    @Column(name = "appointment_date", nullable = false)
    private Instant appointmentDate;

    @Column(name = "description", length = Validation.MAX_DESCRIPTION_LENGTH, nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "project_id", length = Validation.MAX_ID_LENGTH)
    private String projectId;

    @Column(name = "task_id", length = Validation.MAX_ID_LENGTH)
    private String taskId;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    protected AppointmentEntity() {
        // JPA only
    }

    public AppointmentEntity(
            final String appointmentId,
            final Instant appointmentDate,
            final String description,
            final User user) {
        this.appointmentId = appointmentId;
        this.appointmentDate = appointmentDate;
        this.description = description;
        this.user = user;
    }

    public String getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(final String appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Instant getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(final Instant appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public User getUser() {
        return user;
    }

    public void setUser(final User user) {
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Setter exposed for testing so versioned entities can be reconstituted deterministically.
     */
    public void setVersion(final Long version) {
        this.version = version;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(final String projectId) {
        this.projectId = projectId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(final String taskId) {
        this.taskId = taskId;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(final boolean archived) {
        this.archived = archived;
    }
}
