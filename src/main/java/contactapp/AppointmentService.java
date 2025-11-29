package contactapp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for managing {@link Appointment} instances.
 *
 * Mirrors the singleton + in-memory store pattern used by other services:
 * add/delete/update appointments with trimmed IDs, reuse Appointment validation,
 * and expose a read-only snapshot for tests.
 */
public final class AppointmentService {
    /**
     * Singleton instance, created lazily on first access.
     * Not volatile because getInstance() is synchronized.
     */
    private static AppointmentService instance;

    /**
     * In-memory store keyed by appointmentId.
     */
    private final Map<String, Appointment> database = new ConcurrentHashMap<>();

    /**
     * Private constructor to enforce singleton usage.
     */
    private AppointmentService() {
        // no-op constructor
    }

    /**
     * Returns the shared AppointmentService instance.
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton intentionally exposes the shared instance")
    public static synchronized AppointmentService getInstance() {
        if (instance == null) {
            instance = new AppointmentService();
        }
        return instance;
    }

    /**
     * Adds an appointment if its id is not already present.
     *
     * @param appointment appointment to store (must not be null); id is trimmed before validation and storage
     * @return true if inserted; false if an appointment with the same id exists
     */
    public boolean addAppointment(final Appointment appointment) {
        if (appointment == null) {
            throw new IllegalArgumentException("appointment must not be null");
        }
        // Appointment constructor already trims/validates the id; this guard
        // protects against subclasses returning a blank id.
        Validation.validateNotBlank(appointment.getAppointmentId(), "appointmentId");
        return database.putIfAbsent(appointment.getAppointmentId(), appointment) == null;
    }

    /**
     * Deletes an appointment by id.
     *
     * @param appointmentId id to remove; must not be blank
     * @return true if removed; false if no matching id existed
     */
    public boolean deleteAppointment(final String appointmentId) {
        final String normalizedId = normalizeAndValidateId(appointmentId);
        return database.remove(normalizedId) != null;
    }

    /**
     * Updates an existing appointment's mutable fields.
     *
     * @param appointmentId   id of the appointment to update
     * @param appointmentDate new date (not null, not in the past)
     * @param description     new description (length 1-50)
     * @return true if the appointment existed and was updated; false otherwise
     */
    public boolean updateAppointment(
            final String appointmentId,
            final Date appointmentDate,
            final String description) {
        final String normalizedId = normalizeAndValidateId(appointmentId);
        return database.computeIfPresent(normalizedId, (key, appointment) -> {
            appointment.update(appointmentDate, description);
            return appointment;
        }) != null;
    }

    /**
     * Returns an unmodifiable copy of the current store.
     *
     * Returns defensive copies of each Appointment to prevent external mutation.
     */
    public Map<String, Appointment> getDatabase() {
        return database.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().copy()));
    }

    /**
     * Clears all stored appointments.
     *
     * <p>Package-private to limit usage to test code within the same package.
     * This prevents accidental calls from production code outside the package.
     */
    void clearAllAppointments() {
        database.clear();
    }

    private String normalizeAndValidateId(final String appointmentId) {
        Validation.validateNotBlank(appointmentId, "appointmentId");
        return appointmentId.trim();
    }

}
