package contactapp.persistence.entity;

import contactapp.domain.TaskStatus;
import contactapp.domain.Validation;
import contactapp.security.User;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hibernate entity for {@link contactapp.domain.Task} persistence.
 */
@Entity
@Table(
        name = "tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tasks_task_id_user_id",
                columnNames = {"task_id", "user_id"}))
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP",
        justification = "JPA entity returns User reference for ORM relationship; "
                + "entity lifecycle is managed by Hibernate persistence context")
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "task_id", length = Validation.MAX_ID_LENGTH, nullable = false)
    private String taskId;

    @Column(name = "name", length = Validation.MAX_TASK_NAME_LENGTH, nullable = false)
    private String name;

    @Column(name = "description", length = Validation.MAX_DESCRIPTION_LENGTH, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = Validation.MAX_STATUS_LENGTH, nullable = false)
    private TaskStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "project_id", length = Validation.MAX_ID_LENGTH)
    private String projectId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    protected TaskEntity() {
        // JPA only
    }

    public TaskEntity(
            final String taskId,
            final String name,
            final String description,
            final TaskStatus status,
            final LocalDate dueDate,
            final User user) {
        this.taskId = taskId;
        this.name = name;
        this.description = description;
        this.status = status != null ? status : TaskStatus.TODO;
        this.dueDate = dueDate;
        this.user = user;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(final String taskId) {
        this.taskId = taskId;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(final TaskStatus status) {
        this.status = status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(final LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(final String projectId) {
        this.projectId = projectId;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(final UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(final boolean archived) {
        this.archived = archived;
    }

    @PrePersist
    protected void onCreate() {
        final Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = TaskStatus.TODO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
