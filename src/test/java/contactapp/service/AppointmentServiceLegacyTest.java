package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.domain.Appointment;
import contactapp.persistence.store.AppointmentStore;
import contactapp.persistence.store.InMemoryAppointmentStore;
import contactapp.security.TestUserSetup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import contactapp.support.PostgresContainerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ensures {@link AppointmentService#getInstance()} works outside Spring.
 *
 * <p>Note: These tests run with Spring context to get the TestUserSetup bean.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Isolated
class AppointmentServiceLegacyTest extends PostgresContainerSupport {

    @Autowired
    private TestUserSetup testUserSetup;

    @Autowired
    private AppointmentService springService;

    @BeforeEach
    void resetSingleton() throws Exception {
        testUserSetup.setupTestUser();
        springService.clearAllAppointments();
        setInstance(null);
    }

    @AfterEach
    void cleanSingleton() throws Exception {
        setInstance(null);
    }

    @Test
    void coldStartReturnsInMemoryStore() {
        AppointmentService legacy = AppointmentService.getInstance();
        assertThat(legacy).isNotNull();

        Appointment appointment = new Appointment("777", futureDate(5), "Legacy Appointment");
        assertThat(legacy.addAppointment(appointment)).isTrue();
        assertThat(legacy.getAppointmentById("777")).isPresent();
    }

    @Test
    void repeatedCallsReturnSameLegacyInstance() {
        AppointmentService first = AppointmentService.getInstance();
        AppointmentService second = AppointmentService.getInstance();
        assertThat(first).isSameAs(second);
    }

    /**
     * Validates that {@link AppointmentService#registerInstance(AppointmentService)}
     * copies pending appointments from the in-memory fallback into the injected store.
     */
    @Test
    void springBeanRegistrationMigratesLegacyAppointments() throws Exception {
        AppointmentService legacy = createLegacyService();
        legacy.addAppointment(new Appointment("A-55", futureDate(2), "Cutover appointment"));

        CapturingAppointmentStore store = new CapturingAppointmentStore();
        AppointmentService springBean = new AppointmentService(store);

        assertThat(store.findById("A-55")).isPresent();
        assertThat(AppointmentService.getInstance()).isSameAs(springBean);
    }

    /**
     * Validates the legacy fallback keeps the same duplicate-detection semantics as the JPA-backed path so PIT
     * can't mutate the boolean return or omit the DuplicateResourceException.
     */
    @Test
    void legacyInsertReturnsTrueAndDuplicateThrows() throws Exception {
        AppointmentService legacy = createLegacyService();
        Appointment appointment = new Appointment("LEG-A", futureDate(3), "Legacy branch");

        assertThat(legacy.addAppointment(appointment)).isTrue();
        assertThat(legacy.getAppointmentById("LEG-A")).isPresent();
        assertThatThrownBy(() -> legacy.addAppointment(appointment))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("LEG-A");
    }

    @Test
    void legacyAllUsersListingRemainsAccessibleWithoutSecurity() throws Exception {
        AppointmentService legacy = createLegacyService();
        legacy.addAppointment(new Appointment("LEG-A1", futureDate(1), "Legacy first"));
        legacy.addAppointment(new Appointment("LEG-A2", futureDate(2), "Legacy second"));

        assertThat(legacy.getAllAppointmentsAllUsers())
                .extracting(Appointment::getAppointmentId)
                .containsExactlyInAnyOrder("LEG-A1", "LEG-A2");
    }

    private static void setInstance(final AppointmentService newInstance) throws Exception {
        Field instanceField = AppointmentService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, newInstance);
    }

    private static Date futureDate(final int daysInFuture) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysInFuture);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    /**
     * Builds a synthetic legacy instance so the migration path can be tested
     * without mutating the Spring-managed singleton.
     */
    private static AppointmentService createLegacyService() throws Exception {
        Constructor<AppointmentService> constructor = AppointmentService.class
                .getDeclaredConstructor(AppointmentStore.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new InMemoryAppointmentStore(), true);
    }

    private static final class CapturingAppointmentStore implements AppointmentStore {
        private final Map<String, Appointment> database = new LinkedHashMap<>();

        @Override
        public boolean existsById(final String id) {
            return database.containsKey(id);
        }

        @Override
        public void save(final Appointment aggregate) {
            database.put(aggregate.getAppointmentId(), aggregate.copy());
        }

        @Override
        public Optional<Appointment> findById(final String id) {
            return Optional.ofNullable(database.get(id)).map(Appointment::copy);
        }

        @Override
        public List<Appointment> findAll() {
            final List<Appointment> appointments = new ArrayList<>();
            database.values().forEach(appointment -> appointments.add(appointment.copy()));
            return appointments;
        }

        @Override
        public boolean deleteById(final String id) {
            return database.remove(id) != null;
        }

        @Override
        public void deleteAll() {
            database.clear();
        }
    }
}
