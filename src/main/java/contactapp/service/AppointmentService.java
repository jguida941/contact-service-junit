package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.config.ApplicationContextProvider;
import contactapp.domain.Appointment;
import contactapp.domain.Validation;
import contactapp.persistence.store.AppointmentStore;
import contactapp.persistence.store.InMemoryAppointmentStore;
import contactapp.persistence.store.JpaAppointmentStore;
import contactapp.security.Role;
import contactapp.security.User;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for managing {@link Appointment} instances.
 *
 * <p>Delegates persistence to {@link AppointmentStore} so Spring-managed JPA repositories
 * and legacy {@link #getInstance()} callers see the same behavior while
 * {@link #clearAllAppointments()} remains available for package-private test helpers.
 *
 * <h2>Why Not Final?</h2>
 * <p>This class was previously {@code final}. The modifier was removed because
 * Spring's {@code @Transactional} annotation uses CGLIB proxy subclassing,
 * which requires non-final classes for method interception.
 */
@Service
@Transactional
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Constructor calls registerInstance() for singleton pattern compatibility; "
                + "this is intentional for backward compatibility with legacy non-Spring callers")
public class AppointmentService {

    private static AppointmentService instance;

    private final AppointmentStore store;
    private final boolean legacyStore;

    @org.springframework.beans.factory.annotation.Autowired
    public AppointmentService(final AppointmentStore store) {
        this(store, false);
    }

    private AppointmentService(final AppointmentStore store, final boolean legacyStore) {
        this.store = store;
        this.legacyStore = legacyStore;
        registerInstance(this);
    }

    private static synchronized void registerInstance(final AppointmentService candidate) {
        if (instance != null && instance.legacyStore && !candidate.legacyStore) {
            instance.getAllAppointments().forEach(candidate::addAppointment);
        }
        instance = candidate;
    }

    /**
     * Returns the shared AppointmentService singleton instance.
     *
     * <p>The method is synchronized so that concurrent callers cannot create
     * multiple singleton instances.
     *
     * <p>When running inside Spring, prefer constructor injection. This method
     * remains for backward compatibility with code that still calls it directly.
     *
     * <p>If called before Spring context initializes, lazily creates a service
     * backed by {@link InMemoryAppointmentStore}. This preserves backward
     * compatibility for legacy non-Spring callers.
     *
     * @return the singleton {@code AppointmentService} instance
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton intentionally exposes shared instance for backward compatibility")
    public static synchronized AppointmentService getInstance() {
        if (instance != null) {
            return instance;
        }
        final ApplicationContext context = ApplicationContextProvider.getContext();
        if (context != null) {
            return context.getBean(AppointmentService.class);
        }
        return new AppointmentService(new InMemoryAppointmentStore(), true);
    }

    /**
     * Gets the currently authenticated user from the Spring Security context.
     *
     * @return the authenticated user
     * @throws IllegalStateException if no user is authenticated
     */
    private User getCurrentUser() {
        final var context = SecurityContextHolder.getContext();
        final Authentication authentication = context != null ? context.getAuthentication() : null;
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        final Object principal = authentication.getPrincipal();
        if (!(principal instanceof User)) {
            throw new IllegalStateException("Authenticated principal is not a User");
        }
        return (User) principal;
    }

    /**
     * Checks if the current user has ADMIN role.
     *
     * @param user the user to check
     * @return true if the user is an ADMIN
     */
    private boolean isAdmin(final User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    /**
     * Adds an appointment for the authenticated user if its id is not already present.
     *
     * <p>Database uniqueness constraints signal duplicates; the service surfaces them as
     * {@link DuplicateResourceException} so HTTP 409 responses stay consistent without
     * re-implementing {@code existsById} checks in Java.
     *
     * @param appointment the appointment to add; must not be null
     * @return true if the appointment was added
     * @throws IllegalArgumentException if appointment is null or has blank ID
     * @throws DuplicateResourceException if an appointment with the same ID exists
     */
    public boolean addAppointment(final Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("appointment must not be null");
        }
        final String normalizedId = normalizeAndValidateId(appointment.getAppointmentId());

        // Use user-aware store methods if available
        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();
            try {
                jpaStore.insert(appointment, currentUser);
                return true;
            } catch (DataIntegrityViolationException e) {
                throw new DuplicateResourceException(
                        "Appointment with id '" + normalizedId + "' already exists", e);
            }
        }

        // Fallback for legacy in-memory store
        if (store.existsById(normalizedId)) {
            throw new DuplicateResourceException(
                    "Appointment with id '" + normalizedId + "' already exists");
        }
        store.save(appointment);
        return true;
    }

    /**
     * Deletes an appointment by id for the authenticated user.
     *
     * @param appointmentId id to remove; must not be blank
     * @return true if removed, false otherwise
     * @throws IllegalArgumentException if appointmentId is null or blank
     */
    public boolean deleteAppointment(final String appointmentId) {
        final String normalizedId = normalizeAndValidateId(appointmentId);

        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.deleteById(normalizedId, currentUser);
        }

        return store.deleteById(normalizedId);
    }

    /**
     * Updates an existing appointment's mutable fields for the authenticated user.
     *
     * @param appointmentId the id of the appointment to update
     * @param appointmentDate new appointment date
     * @param description new description
     * @return true if the appointment exists and was updated, false if not found
     * @throws IllegalArgumentException if any new field value is invalid
     */
    public boolean updateAppointment(
            final String appointmentId,
            final Date appointmentDate,
            final String description) {
        return updateAppointment(appointmentId, appointmentDate, description, null, null);
    }

    /**
     * Updates an existing appointment's mutable fields for the authenticated user.
     *
     * @param appointmentId the id of the appointment to update
     * @param appointmentDate new appointment date
     * @param description new description
     * @param projectId new project ID (nullable, can unlink from project)
     * @param taskId new task ID (nullable, can unlink from task)
     * @return true if the appointment exists and was updated, false if not found
     * @throws IllegalArgumentException if any new field value is invalid
     */
    public boolean updateAppointment(
            final String appointmentId,
            final Date appointmentDate,
            final String description,
            final String projectId,
            final String taskId) {
        final String normalizedId = normalizeAndValidateId(appointmentId);

        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();

            final Optional<Appointment> appointment = jpaStore.findById(normalizedId, currentUser);
            if (appointment.isEmpty()) {
                return false;
            }
            final Appointment existing = appointment.get();
            existing.update(appointmentDate, description, projectId, taskId);
            jpaStore.save(existing, currentUser);
            return true;
        }

        // Fallback for legacy in-memory store
        final Optional<Appointment> appointment = store.findById(normalizedId);
        if (appointment.isEmpty()) {
            return false;
        }
        final Appointment existing = appointment.get();
        existing.update(appointmentDate, description, projectId, taskId);
        store.save(existing);
        return true;
    }

    /**
     * Returns an unmodifiable snapshot of the current store.
     */
    @Transactional(readOnly = true)
    public Map<String, Appointment> getDatabase() {
        return store.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Appointment::getAppointmentId,
                        Appointment::copy));
    }

    /**
     * Returns all appointments as a list of defensive copies for the authenticated user.
     *
     * <p>This method always scopes results to the user tied to the active
     * {@link org.springframework.security.core.Authentication}. Administrators who need a global view
     * should call {@link #getAllAppointmentsAllUsers()} instead.
     *
     * @return list of appointment defensive copies for the current user
     */
    @Transactional(readOnly = true)
    public List<Appointment> getAllAppointments() {
        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findAll(currentUser).stream()
                    .map(Appointment::copy)
                    .toList();
        }

        return store.findAll().stream()
                .map(Appointment::copy)
                .toList();
    }

    /**
     * Returns all appointments across all users (ADMIN only).
     *
     * <p>This method should only be called by controllers when ?all=true
     * is specified and the authenticated user is an ADMIN. While the service
     * is operating in its legacy in-memory mode (before Spring initializes),
     * it preserves the historical behavior of returning all appointments
     * without requiring an authenticated principal.
     *
     * @return list of all appointment defensive copies
     * @throws AccessDeniedException if current user is not an ADMIN
     */
    @Transactional(readOnly = true)
    public List<Appointment> getAllAppointmentsAllUsers() {
        if (legacyStore) {
            return store.findAll().stream()
                    .map(Appointment::copy)
                    .toList();
        }

        final User currentUser = getCurrentUser();
        if (!isAdmin(currentUser)) {
            throw new AccessDeniedException("Only ADMIN users can access all appointments");
        }

        return store.findAll().stream()
                .map(Appointment::copy)
                .toList();
    }

    /**
     * Finds an appointment by ID for the authenticated user.
     *
     * <p>The ID is validated and trimmed before lookup so callers can pass
     * values like " 123 " and still find the appointment stored as "123".
     *
     * @param appointmentId the appointment ID to search for
     * @return Optional containing a defensive copy of the appointment, or empty if not found
     * @throws IllegalArgumentException if appointmentId is null or blank
     */
    @Transactional(readOnly = true)
    public Optional<Appointment> getAppointmentById(final String appointmentId) {
        final String normalizedId = normalizeAndValidateId(appointmentId);

        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findById(normalizedId, currentUser).map(Appointment::copy);
        }

        return store.findById(normalizedId).map(Appointment::copy);
    }

    /**
     * Returns all appointments associated with the specified project for the authenticated user.
     *
     * <p>Uses database-level filtering via {@code findByProjectId(projectId, user)} query for optimal
     * performance instead of in-memory filtering.
     *
     * @param projectId the project ID to filter by
     * @return list of appointments for the specified project
     * @throws IllegalArgumentException if projectId is null or blank
     */
    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsByProjectId(final String projectId) {
        Validation.validateNotBlank(projectId, "projectId");
        final String trimmedProjectId = projectId.trim();

        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findByProjectId(trimmedProjectId, currentUser).stream()
                    .map(Appointment::copy)
                    .toList();
        }

        // Fallback for legacy in-memory store (uses in-memory filtering)
        return store.findAll().stream()
                .filter(appointment -> trimmedProjectId.equals(appointment.getProjectId()))
                .map(Appointment::copy)
                .toList();
    }

    /**
     * Returns all appointments associated with the specified task for the authenticated user.
     *
     * <p>Uses database-level filtering via {@code findByTaskId(taskId, user)} query for optimal
     * performance instead of in-memory filtering.
     *
     * @param taskId the task ID to filter by
     * @return list of appointments for the specified task
     * @throws IllegalArgumentException if taskId is null or blank
     */
    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsByTaskId(final String taskId) {
        Validation.validateNotBlank(taskId, "taskId");
        final String trimmedTaskId = taskId.trim();

        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();
            return jpaStore.findByTaskId(trimmedTaskId, currentUser).stream()
                    .map(Appointment::copy)
                    .toList();
        }

        // Fallback for legacy in-memory store (uses in-memory filtering)
        return store.findAll().stream()
                .filter(appointment -> trimmedTaskId.equals(appointment.getTaskId()))
                .map(Appointment::copy)
                .toList();
    }

    /**
     * Archives an appointment by setting its archived flag to true.
     *
     * @param appointmentId the id of the appointment to archive
     * @return true if the appointment exists and was archived, false if not found
     * @throws IllegalArgumentException if appointmentId is null or blank
     */
    public boolean archiveAppointment(final String appointmentId) {
        final String normalizedId = normalizeAndValidateId(appointmentId);

        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();

            final Optional<Appointment> appointment = jpaStore.findById(normalizedId, currentUser);
            if (appointment.isEmpty()) {
                return false;
            }
            final Appointment existing = appointment.get();
            existing.setArchived(true);
            jpaStore.save(existing, currentUser);
            return true;
        }

        // Fallback for legacy in-memory store
        final Optional<Appointment> appointment = store.findById(normalizedId);
        if (appointment.isEmpty()) {
            return false;
        }
        final Appointment existing = appointment.get();
        existing.setArchived(true);
        store.save(existing);
        return true;
    }

    /**
     * Unarchives an appointment by setting its archived flag to false.
     *
     * @param appointmentId the id of the appointment to unarchive
     * @return true if the appointment exists and was unarchived, false if not found
     * @throws IllegalArgumentException if appointmentId is null or blank
     */
    public boolean unarchiveAppointment(final String appointmentId) {
        final String normalizedId = normalizeAndValidateId(appointmentId);

        if (store instanceof JpaAppointmentStore) {
            final JpaAppointmentStore jpaStore = (JpaAppointmentStore) store;
            final User currentUser = getCurrentUser();

            final Optional<Appointment> appointment = jpaStore.findById(normalizedId, currentUser);
            if (appointment.isEmpty()) {
                return false;
            }
            final Appointment existing = appointment.get();
            existing.setArchived(false);
            jpaStore.save(existing, currentUser);
            return true;
        }

        // Fallback for legacy in-memory store
        final Optional<Appointment> appointment = store.findById(normalizedId);
        if (appointment.isEmpty()) {
            return false;
        }
        final Appointment existing = appointment.get();
        existing.setArchived(false);
        store.save(existing);
        return true;
    }

    void clearAllAppointments() {
        store.deleteAll();
    }

    private String normalizeAndValidateId(final String appointmentId) {
        Validation.validateNotBlank(appointmentId, "appointmentId");
        return appointmentId.trim();
    }
}
