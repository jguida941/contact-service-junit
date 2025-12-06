package contactapp.persistence.store;

import contactapp.domain.Project;
import contactapp.domain.ProjectStatus;
import contactapp.persistence.entity.ProjectEntity;
import contactapp.persistence.mapper.ProjectMapper;
import contactapp.persistence.repository.ProjectRepository;
import contactapp.security.User;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Primary {@link ProjectStore} implementation backed by Spring Data JPA.
 *
 * <p>Supports per-user data isolation by filtering projects based on the authenticated user.
 */
@Component
public class JpaProjectStore implements ProjectStore {

    private final ProjectRepository repository;
    private final ProjectMapper mapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring-managed singleton stores repository and mapper references; "
                    + "these are framework-managed beans with controlled lifecycle")
    public JpaProjectStore(final ProjectRepository repository, final ProjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Checks if a project exists for a specific user.
     *
     * @param id the project ID
     * @param user the user who owns the project
     * @return true if the project exists for the given user
     */
    public boolean existsById(final String id, final User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return repository.existsByProjectIdAndUser(id, user);
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean existsById(final String id) {
        throw new UnsupportedOperationException(
                "Use existsById(id, User) to enforce per-user authorization");
    }

    /**
     * Saves a project for a specific user.
     *
     * @param aggregate the project to save
     * @param user the user who owns the project
     */
    @Transactional
    @Override
    public void save(final Project aggregate, final User user) {
        if (aggregate == null) {
            throw new IllegalArgumentException("project aggregate must not be null");
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        // Check if entity already exists to preserve user association on updates
        final Optional<ProjectEntity> existing = repository.findByProjectIdAndUser(
                aggregate.getProjectId(), user);
        existing.ifPresentOrElse(
                entity -> {
                    mapper.updateEntity(entity, aggregate);
                    repository.save(entity);
                },
                () -> repository.save(mapper.toEntity(aggregate, user)));
    }

    /**
     * Inserts a brand-new project and relies on database constraints to detect duplicates.
     *
     * @param aggregate the project to create
     * @param user the owner
     */
    @Transactional
    public void insert(final Project aggregate, final User user) {
        if (aggregate == null) {
            throw new IllegalArgumentException("project aggregate must not be null");
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        repository.save(mapper.toEntity(aggregate, user));
    }

    @Override
    @Deprecated(forRemoval = true)
    public void save(final Project aggregate) {
        throw new UnsupportedOperationException(
                "Use save(Project, User) instead. This method requires a User parameter.");
    }

    /**
     * Finds a project by ID for a specific user.
     *
     * @param id the project ID
     * @param user the user who owns the project
     * @return optional containing the project if found
     */
    public Optional<Project> findById(final String id, final User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return repository.findByProjectIdAndUser(id, user).map(mapper::toDomain);
    }

    @Override
    @Deprecated(forRemoval = true)
    public Optional<Project> findById(final String id) {
        throw new UnsupportedOperationException(
                "Use findById(String, User) to enforce per-user authorization");
    }

    /**
     * Finds all projects for a specific user.
     *
     * @param user the user who owns the projects
     * @return list of projects for the given user
     */
    public List<Project> findAll(final User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return repository.findByUser(user).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Deprecated(since = "1.0", forRemoval = true)
    public List<Project> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * Finds all projects for a user with a specific status.
     *
     * @param user the user who owns the projects
     * @param status the status to filter by
     * @return list of projects matching the status
     */
    @Override
    public List<Project> findByStatus(final User user, final ProjectStatus status) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return repository.findByUserAndStatus(user, status).stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * Deletes a project by ID for a specific user.
     *
     * @param id the project ID
     * @param user the user who owns the project
     * @return true if the project was deleted, false if not found
     */
    public boolean deleteById(final String id, final User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return repository.deleteByProjectIdAndUser(id, user) > 0;
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean deleteById(final String id) {
        throw new UnsupportedOperationException(
                "Use deleteById(String, User) to enforce per-user authorization");
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }
}
