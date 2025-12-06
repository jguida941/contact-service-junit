package contactapp.persistence.mapper;

import contactapp.domain.Appointment;
import contactapp.persistence.entity.AppointmentEntity;
import contactapp.security.User;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * Mapper bridging {@link Appointment} and {@link AppointmentEntity}.
 */
@Component
public class AppointmentMapper {

    /**
     * Converts a domain Appointment to an entity, associating it with the given user.
     *
     * @param domain the domain appointment
     * @param user the user who owns this appointment
     * @return the entity representation
     */
    public AppointmentEntity toEntity(final Appointment domain, final User user) {
        if (domain == null) {
            return null;
        }
        final Date appointmentDate = domain.getAppointmentDate();
        if (appointmentDate == null) {
            throw new IllegalArgumentException("appointmentDate must not be null");
        }
        final Instant instant = appointmentDate.toInstant();
        final AppointmentEntity entity = new AppointmentEntity(
                domain.getAppointmentId(),
                instant,
                domain.getDescription(),
                user);
        entity.setProjectId(domain.getProjectId());
        entity.setTaskId(domain.getTaskId());
        entity.setArchived(domain.isArchived());
        return entity;
    }

    /**
     * Converts a domain Appointment to an entity.
     *
     * @param domain the domain appointment
     * @return the entity representation
     * @deprecated Use {@link #toEntity(Appointment, User)} instead
     */
    @Deprecated
    public AppointmentEntity toEntity(final Appointment domain) {
        if (domain == null) {
            return null;
        }
        throw new UnsupportedOperationException(
                "Use toEntity(Appointment, User) instead. This method requires a User parameter.");
    }

    /**
     * Converts an entity to a domain Appointment using reconstitution.
     *
     * <p>Uses {@link Appointment#reconstitute} to bypass the "not in past" validation,
     * allowing existing appointments with past dates to be loaded successfully.
     *
     * @param entity the entity to convert
     * @return the domain appointment, or null if entity is null
     */
    public Appointment toDomain(final AppointmentEntity entity) {
        if (entity == null) {
            return null;
        }
        final Instant persistedInstant = entity.getAppointmentDate();
        if (persistedInstant == null) {
            throw new IllegalStateException("appointmentDate column must not be null");
        }
        final Date date = Date.from(persistedInstant);
        return Appointment.reconstitute(
                entity.getAppointmentId(),
                date,
                entity.getDescription(),
                entity.getProjectId(),
                entity.getTaskId(),
                entity.isArchived());
    }

    /**
     * Updates an existing entity with the latest appointment metadata.
     *
     * @param target entity to mutate
     * @param source domain aggregate
     */
    public void updateEntity(final AppointmentEntity target, final Appointment source) {
        if (target == null) {
            throw new IllegalArgumentException("target entity must not be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("source appointment must not be null");
        }
        final Date appointmentDate = source.getAppointmentDate();
        if (appointmentDate == null) {
            throw new IllegalArgumentException("appointmentDate must not be null");
        }
        target.setAppointmentDate(appointmentDate.toInstant());
        target.setDescription(source.getDescription());
        target.setProjectId(source.getProjectId());
        target.setTaskId(source.getTaskId());
        target.setArchived(source.isArchived());
    }
}
