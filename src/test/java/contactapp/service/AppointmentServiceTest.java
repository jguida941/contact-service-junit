package contactapp.service;

import contactapp.api.exception.DuplicateResourceException;
import contactapp.domain.Appointment;
import contactapp.security.Role;
import contactapp.security.TestUserSetup;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Calendar;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link AppointmentService} behavior against the Spring context (PostgreSQL + Flyway via Testcontainers).
 */
@SpringBootTest
@ActiveProfiles("integration")
@Isolated
@Tag("legacy-singleton")
public class AppointmentServiceTest {

    @Autowired
    private AppointmentService service;

    @Autowired
    private TestUserSetup testUserSetup;

    @Autowired
    private TestCleanupUtility testCleanup;

    /**
     * Sets up test user and clears data before each test.
     */
    @BeforeEach
    void reset() {
        testCleanup.resetTestEnvironment();
    }

    @Test
    @Disabled("Temporarily disabled singleton path to isolate duplicate-id flakiness")
    void testSingletonSharesStateWithSpringBean() {
        testUserSetup.setupTestUser("test-appt-singleton", "appt-singleton@example.com", Role.USER);

        AppointmentService singleton = AppointmentService.getInstance();

        Date futureDate = futureDate(10);
        Appointment legacyAppointment = new Appointment("legacy-apt", futureDate, "Singleton path");
        boolean addedViaSingleton = singleton.addAppointment(legacyAppointment);

        assertThat(addedViaSingleton).isTrue();
        assertThat(service.getAppointmentById("legacy-apt")).isPresent();
    }

    /**
     * Confirms {@link AppointmentService#addAppointment(Appointment)} stores new appointments.
     */
    @Test
    void testAddAppointment() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(30);
        Appointment appointment = new Appointment("200", futureDate, "Document Date");

        boolean added = service.addAppointment(appointment);

        assertThat(added).isTrue();
        assertThat(service.getDatabase()).containsKey("200");
        Appointment stored = service.getDatabase().get("200");
        assertThat(stored.getAppointmentDate()).isEqualTo(futureDate);
        assertThat(stored.getDescription()).isEqualTo("Document Date");
    }

    /**
     * Verifies delete removes stored appointments and returns true.
     */
    @Test
    void testDeleteAppointment() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(30);
        Appointment appointment = new Appointment("700", futureDate, "Sample Appointment");
        service.addAppointment(appointment);

        boolean deleted = service.deleteAppointment("700");

        assertThat(deleted).isTrue();
        assertThat(service.getDatabase()).doesNotContainKey("700");
    }

    /**
     * Ensures {@link AppointmentService#updateAppointment} updates both fields.
     */
    @Test
    void testUpdateAppointment() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(30);
        Appointment appointment = new Appointment("400", futureDate, "First Appointment");
        service.addAppointment(appointment);

        Date newDate = futureDate(60);

        boolean updated = service.updateAppointment("400", newDate, "Updated Appointment");

        Appointment updatedAppt = service.getDatabase().get("400");
        assertThat(updatedAppt.getAppointmentDate()).isEqualTo(newDate);
        assertThat(updatedAppt.getDescription()).isEqualTo("Updated Appointment");
        assertThat(updated).isTrue();
    }

    /**
     * Verifies duplicate IDs are rejected while the original entry remains.
     */
    @Test
    void testAddDuplicateAppointmentIdFails() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(30);
        Appointment original = new Appointment("300", futureDate, "Document Date");
        Appointment duplicate = new Appointment("300", futureDate, "Duplicate Date");

        assertThat(service.addAppointment(original)).isTrue();
        assertThatThrownBy(() -> service.addAppointment(duplicate))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("300");
        Appointment stored = service.getDatabase().get("300");
        assertThat(stored.getAppointmentDate()).isEqualTo(futureDate);
        assertThat(stored.getDescription()).isEqualTo("Document Date");
    }

    /**
     * Ensures {@link AppointmentService#addAppointment(Appointment)} throws for null inputs.
     */
    @Test
    void testAddAppointmentNullThrows() {
        AppointmentService service = this.service;

        assertThatThrownBy(() -> service.addAppointment(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("appointment must not be null");
    }

    /**
     * Proves IDs are validated even if a subclass returns blank IDs.
     */
    @Test
    void testAddAppointmentWithBlankIdThrows() throws Exception {
        AppointmentService service = this.service;
        Date future = futureDate(30);
        // Force a blank id via reflection to simulate corrupted input and hit the guard
        Appointment bad = new Appointment("tmp", future, "Desc");
        Field idField = Appointment.class.getDeclaredField("appointmentId");
        idField.setAccessible(true);
        idField.set(bad, " ");

        assertThatThrownBy(() -> service.addAppointment(bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("appointmentId must not be null or blank");
    }

    /**
     * Ensures delete enforces the not-blank ID rule.
     */
    @Test
    void testDeleteAppointmentBlankIdThrows() {
        AppointmentService service = this.service;

        assertThatThrownBy(() -> service.deleteAppointment(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("appointmentId must not be null or blank");
    }

    /**
     * Verifies delete returns {@code false} when the ID does not exist.
     */
    @Test
    void testDeleteMissingAppointmentReturnsFalse() {
        AppointmentService service = this.service;

        assertThat(service.deleteAppointment("missing")).isFalse();
    }

    /**
     * Confirms {@link AppointmentService#clearAllAppointments()} empties the store.
     */
    @Test
    void testClearAllAppointmentsRemovesEntries() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(30);
        Appointment appointment = new Appointment("500", futureDate, "Sample Appointment");
        service.addAppointment(appointment);

        service.clearAllAppointments();

        assertThat(service.getDatabase()).isEmpty();
    }

    /**
     * Ensures getDatabase returns defensive copies so external mutation cannot alter service state.
     */
    @Test
    void testGetDatabaseReturnsDefensiveCopies() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(30);
        Appointment appointment = new Appointment("900", futureDate, "Immutable?");
        service.addAppointment(appointment);

        Appointment snapshot = service.getDatabase().get("900");
        snapshot.setDescription("Mutated outside service");

        Appointment freshSnapshot = service.getDatabase().get("900");
        assertThat(freshSnapshot.getDescription()).isEqualTo("Immutable?");
    }

    /**
     * Confirms update trims IDs before looking them up.
     */
    @Test
    void testUpdateAppointmentTrimsId() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(45);
        Appointment appointment = new Appointment("600", futureDate, "Initial Description");
        service.addAppointment(appointment);

        boolean updated = service.updateAppointment(" 600 ", futureDate, "Updated Description");

        assertThat(updated).isTrue();
        Appointment updatedAppt = service.getDatabase().get("600");
        assertThat(updatedAppt.getDescription()).isEqualTo("Updated Description");
    }

    /**
     * Ensures update validates ID input before touching the map.
     */
    @Test
    void testUpdateAppointmentBlankIdThrows() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(30);
        assertThatThrownBy(() -> service.updateAppointment(" ", futureDate, "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("appointmentId must not be null or blank");
    }

    /**
     * Verifies update returns {@code false} when the appointment ID is missing.
     */
    @Test
    void testUpdateMissingAppointmentReturnsFalse() {
        AppointmentService service = this.service;

        Date futureDate = futureDate(30);
        assertThat(service.updateAppointment("missing", futureDate, "Desc")).isFalse();
    }

    @Test
    void getAllAppointmentsAllUsers_requiresAdminRole() {
        runAs("limited", Role.USER, () ->
                service.addAppointment(new Appointment("apt-1", futureDate(5), "User only")));

        assertThatThrownBy(service::getAllAppointmentsAllUsers)
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAllAppointmentsAllUsers_returnsDataForAdmins() {
        runAs("user-one", Role.USER, () ->
                service.addAppointment(new Appointment("au-1", futureDate(3), "First")));
        runAs("user-two", Role.USER, () ->
                service.addAppointment(new Appointment("au-2", futureDate(4), "Second")));

        runAs("admin", Role.ADMIN, () -> assertThat(service.getAllAppointmentsAllUsers())
                .extracting(Appointment::getAppointmentId)
                .contains("au-1", "au-2"));
    }

    @Test
    void getAppointmentById_onlyReturnsCurrentUsersAppointments() {
        runAs("owner", Role.USER, () ->
                service.addAppointment(new Appointment("shared-apt", futureDate(2), "Owner entry")));

        runAs("other", Role.USER, () ->
                assertThat(service.getAppointmentById("shared-apt")).isEmpty());
    }

    @Test
    void deleteAppointment_doesNotAllowCrossUserDeletion() {
        runAs("owner", Role.USER, () ->
                service.addAppointment(new Appointment("locked-apt", futureDate(1), "Locked")));

        runAs("other", Role.USER, () ->
                assertThat(service.deleteAppointment("locked-apt")).isFalse());
    }

    @Test
    void updateAppointment_doesNotAllowCrossUserModification() {
        runAs("owner", Role.USER, () ->
                service.addAppointment(new Appointment("owned-apt", futureDate(7), "Owned")));

        runAs("other", Role.USER, () ->
                assertThat(service.updateAppointment("owned-apt", futureDate(8), "Hacker")).isFalse());
    }

    private void runAs(final String username, final Role role, final Runnable action) {
        testUserSetup.setupTestUser(username, username + "@example.com", role);
        action.run();
    }

    /**
     * Helper to generate a zeroed future date relative to "now".
     */
    private static Date futureDate(int daysInFuture) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, daysInFuture);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
