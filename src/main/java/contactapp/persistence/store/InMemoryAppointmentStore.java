package contactapp.persistence.store;

import contactapp.domain.Appointment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Fallback appointment store used prior to Spring context initialization.
 */
public class InMemoryAppointmentStore implements AppointmentStore {

    private final Map<String, Appointment> database = new ConcurrentHashMap<>();

    @Override
    public boolean existsById(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("appointmentId must not be null");
        }
        return database.containsKey(id);
    }

    @Override
    public void save(final Appointment aggregate) {
        if (aggregate == null) {
            throw new IllegalArgumentException("appointment must not be null");
        }
        final String appointmentId = aggregate.getAppointmentId();
        if (appointmentId == null) {
            throw new IllegalArgumentException("appointmentId must not be null");
        }
        final Appointment copy = Optional.ofNullable(aggregate.copy())
                .orElseThrow(() -> new IllegalStateException("appointment copy must not be null"));
        final Appointment existing = database.putIfAbsent(appointmentId, copy);
        if (existing != null) {
            throw new DataIntegrityViolationException(
                    "Appointment with id '" + appointmentId + "' already exists");
        }
    }

    @Override
    public Optional<Appointment> findById(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("appointmentId must not be null");
        }
        final Appointment appointment = database.get(id);
        return appointment == null ? Optional.empty() : Optional.of(appointment.copy());
    }

    @Override
    @Deprecated(since = "1.0", forRemoval = true)
    public List<Appointment> findAll() {
        final List<Appointment> appointments = new ArrayList<>();
        database.values().forEach(appointment -> appointments.add(appointment.copy()));
        return appointments;
    }

    @Override
    public boolean deleteById(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("appointmentId must not be null");
        }
        return database.remove(id) != null;
    }

    @Override
    public void deleteAll() {
        database.clear();
    }
}
