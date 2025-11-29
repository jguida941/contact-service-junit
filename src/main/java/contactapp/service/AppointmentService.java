package contactapp.service;

import contactapp.domain.Appointment;
import contactapp.domain.Validation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing {@link Appointment} instances.
 *
 * <h2>Spring Integration</h2>
 * <p>Annotated with {@link Service} so Spring can manage the bean lifecycle.
 * The singleton pattern via {@link #getInstance()} is retained for backward
 * compatibility with existing tests and non-Spring callers.
 *
 * <h2>Thread Safety</h2>
 * <p>Uses {@link ConcurrentHashMap} for O(1) thread-safe operations.
 * Updates use {@code computeIfPresent} for atomic lookup + update.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Singleton pattern retained for backward compatibility</li>
 *   <li>{@code @Service} added for Spring DI in controllers (Phase 2)</li>
 *   <li>Defensive copies returned from {@link #getDatabase()}</li>
 *   <li>{@link #clearAllAppointments()} is package-private for test isolation</li>
 * </ul>
 *
 * @see Appointment
 * @see Validation
 */
@Service
public final class AppointmentService {

    /**
     * Singleton instance, created lazily on first access.
     * Not volatile because getInstance() is synchronized.
     * Retained for backward compatibility with existing tests.
     */
    private static AppointmentService instance;

    /**
     * In-memory store keyed by appointmentId.
     *
     * <p>Static so all access paths (Spring DI and {@link #getInstance()}) share
     * the same backing store regardless of how many instances are created.
     *
     * <p>ConcurrentHashMap provides O(1) average time for CRUD operations
     * and is safe for concurrent access without external locking.
     */
    private static final Map<String, Appointment> DATABASE = new ConcurrentHashMap<>();

    /**
     * Default constructor for Spring bean creation.
     *
     * <p>Public to allow Spring to instantiate the service via reflection.
     * For non-Spring usage, prefer {@link #getInstance()}.
     *
     * <p>Thread-safe: Registers this instance as the static singleton only if none
     * exists, preserving any data already added through {@link #getInstance()}.
     * This ensures Spring-created beans and legacy callers share the same backing store
     * regardless of initialization order.
     */
    public AppointmentService() {
        synchronized (AppointmentService.class) {
            if (instance == null) {
                instance = this;
            }
        }
    }

    /**
     * Returns the shared AppointmentService singleton instance.
     *
     * <p>Thread safety: The method is synchronized so that, if multiple threads
     * call it at the same time, only one will create the instance.
     *
     * <p>Note: When using Spring DI (e.g., in controllers), prefer constructor
     * injection over this method. This exists for backward compatibility.
     * Both access patterns share the same instance and backing store.
     *
     * @return the singleton AppointmentService instance
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton intentionally exposes shared instance for backward compatibility")
    public static synchronized AppointmentService getInstance() {
        if (instance == null) {
            new AppointmentService();
        }
        return instance;
    }

    /**
     * Adds an appointment if its id is not already present.
     *
     * <p>Uses {@link ConcurrentHashMap#putIfAbsent} for atomic uniqueness check.
     *
     * @param appointment appointment to store (must not be null)
     * @return true if inserted; false if an appointment with the same id exists
     * @throws IllegalArgumentException if appointment is null or has blank id
     */
    public boolean addAppointment(final Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("appointment must not be null");
        }
        // Appointment constructor already trims/validates the id; this guard
        // protects against subclasses returning a blank id.
        Validation.validateNotBlank(appointment.getAppointmentId(), "appointmentId");
        return DATABASE.putIfAbsent(appointment.getAppointmentId(), appointment) == null;
    }

    /**
     * Deletes an appointment by id.
     *
     * <p>The id is validated and trimmed before removal.
     *
     * @param appointmentId id to remove; must not be blank
     * @return true if removed; false if no matching id existed
     * @throws IllegalArgumentException if appointmentId is null or blank
     */
    public boolean deleteAppointment(final String appointmentId) {
        final String normalizedId = normalizeAndValidateId(appointmentId);
        return DATABASE.remove(normalizedId) != null;
    }

    /**
     * Updates an existing appointment's mutable fields.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Validates and trims the appointmentId before lookup</li>
     *   <li>Uses {@link ConcurrentHashMap#computeIfPresent} for thread-safe atomic update</li>
     *   <li>Delegates validation to {@link Appointment#update(Date, String)}</li>
     * </ul>
     *
     * @param appointmentId   id of the appointment to update
     * @param appointmentDate new date (not null, not in the past)
     * @param description     new description (length 1-50)
     * @return true if the appointment existed and was updated; false otherwise
     * @throws IllegalArgumentException if any value is invalid
     */
    public boolean updateAppointment(
            final String appointmentId,
            final Date appointmentDate,
            final String description) {
        final String normalizedId = normalizeAndValidateId(appointmentId);
        return DATABASE.computeIfPresent(normalizedId, (key, appointment) -> {
            appointment.update(appointmentDate, description);
            return appointment;
        }) != null;
    }

    /**
     * Returns an unmodifiable snapshot of the current store.
     *
     * <p>Returns defensive copies of each Appointment to prevent external
     * mutation of internal state.
     *
     * @return unmodifiable map of appointment defensive copies
     */
    public Map<String, Appointment> getDatabase() {
        return DATABASE.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().copy()));
    }

    /**
     * Returns all appointments as a list of defensive copies.
     *
     * <p>Encapsulates the internal storage structure so controllers don't
     * need to access getDatabase() directly.
     *
     * @return list of appointment defensive copies
     */
    public List<Appointment> getAllAppointments() {
        return DATABASE.values().stream()
                .map(Appointment::copy)
                .toList();
    }

    /**
     * Finds an appointment by ID.
     *
     * <p>The ID is validated and trimmed before lookup so callers can pass
     * values like " 123 " and still find the appointment stored as "123".
     *
     * @param appointmentId the appointment ID to search for
     * @return Optional containing a defensive copy of the appointment, or empty if not found
     * @throws IllegalArgumentException if appointmentId is null or blank
     */
    public Optional<Appointment> getAppointmentById(final String appointmentId) {
        final String normalizedId = normalizeAndValidateId(appointmentId);
        final Appointment appointment = DATABASE.get(normalizedId);
        return appointment == null ? Optional.empty() : Optional.of(appointment.copy());
    }

    /**
     * Clears all stored appointments.
     *
     * <p>Package-private to limit usage to test code within the same package.
     * This prevents accidental calls from production code outside the package.
     */
    void clearAllAppointments() {
        DATABASE.clear();
    }

    /**
     * Validates and trims the appointment ID.
     *
     * @param appointmentId the id to validate
     * @return the trimmed id
     * @throws IllegalArgumentException if id is null or blank
     */
    private String normalizeAndValidateId(final String appointmentId) {
        Validation.validateNotBlank(appointmentId, "appointmentId");
        return appointmentId.trim();
    }
}
