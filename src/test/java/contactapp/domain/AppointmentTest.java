package contactapp.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Appointment}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Trimming on construction and defensive copies of dates</li>
 *   <li>Successful updates (date/description) and atomic rejection on invalid inputs</li>
 *   <li>Setter happy path plus validation of description/date in constructor, setters, and update(...)</li>
 * </ul>
 *
 * <p>Tests are in the same package as Appointment to access package-private methods.
 */
public class AppointmentTest {

    /**
     * Ensures construction trims strings and defensively copies the date.
     */
    @Test
    void testSuccessfulCreationTrimsAndCopiesDate() {
        Date futureDate = futureDate(30);
        Appointment appointment = new Appointment(" 200 ", futureDate, " Document Date ");

        assertThat(appointment.getAppointmentId()).isEqualTo("200");
        assertThat(appointment.getDescription()).isEqualTo("Document Date");
        assertThat(appointment.getAppointmentDate()).isEqualTo(futureDate);
        assertThat(appointment.getAppointmentDate()).isNotSameAs(futureDate); // defensive copy
    }

    /**
     * Verifies {@link Appointment#update(Date, String)} replaces both fields atomically.
     */
    @Test
    void testUpdateReplacesValuesAtomically() {
        Appointment appointment = new Appointment("400", futureDate(30), "First Appointment");
        Date newDate = futureDate(60);

        appointment.update(newDate, "Updated Appointment");

        assertThat(appointment.getAppointmentDate()).isEqualTo(newDate);
        assertThat(appointment.getAppointmentDate()).isNotSameAs(newDate); // defensive copy on update
        assertThat(appointment.getDescription()).isEqualTo("Updated Appointment");
    }

    /**
     * Confirms {@link Appointment#setDescription(String)} accepts valid data.
     */
    @Test
    void testSetDescriptionAcceptsValidValue() {
        Appointment appointment = new Appointment("500", futureDate(30), "Initial");

        appointment.setDescription("Updated Description");

        assertThat(appointment.getDescription()).isEqualTo("Updated Description");
    }

    /**
     * Ensures {@link Appointment#getAppointmentDate()} returns a defensive copy.
     */
    @Test
    void testGetAppointmentDateReturnsDefensiveCopy() {
        Date future = futureDate(30);
        Appointment appointment = new Appointment("600", future, "Sample");

        Date returned = appointment.getAppointmentDate();
        returned.setTime(pastDate().getTime());

        assertThat(appointment.getAppointmentDate()).isEqualTo(future);
    }

    /**
     * Validates constructor inputs against the requirements using the argument matrix below.
     */
    @ParameterizedTest
    @MethodSource("invalidCreationInputs")
    void testConstructorValidation(String appointmentId, Date date, String description, String expectedMessage) {
        assertThatThrownBy(() -> new Appointment(appointmentId, date, description))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Ensures {@link Appointment#setDescription(String)} enforces length/not-blank rules.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "' ', 'description must not be null or blank'",
            "'', 'description must not be null or blank'",
            "null, 'description must not be null or blank'",
            "'This description is intentionally made way too long to exceed the fifty character limit set', 'description length must be between 1 and 50'"
    }, nullValues = "null")
    void testSetDescriptionValidation(String invalidDescription, String expectedMessage) {
        Appointment appointment = new Appointment("700", futureDate(30), "Valid");

        assertThatThrownBy(() -> appointment.setDescription(invalidDescription))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Verifies {@link Appointment#update(Date, String)} rejects invalid inputs and preserves state.
     */
    @ParameterizedTest
    @MethodSource("invalidUpdateInputs")
    void testUpdateRejectsInvalidValuesAtomically(Date newDate, String newDescription, String expectedMessage) {
        Date originalDate = futureDate(30);
        Appointment appointment = new Appointment("800", originalDate, "Original");

        assertThatThrownBy(() -> appointment.update(newDate, newDescription))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

        assertThat(appointment.getAppointmentDate()).isEqualTo(originalDate);
        assertThat(appointment.getDescription()).isEqualTo("Original");
    }

    /**
     * Verifies copy() produces an independent instance with identical values.
     */
    @Test
    void testCopyProducesIndependentInstance() {
        Date originalDate = futureDate(30);
        Appointment appointment = new Appointment("900", originalDate, "Copy source");

        Appointment copy = appointment.copy();

        assertThat(copy).isNotSameAs(appointment);
        assertThat(copy.getAppointmentId()).isEqualTo("900");
        assertThat(copy.getDescription()).isEqualTo("Copy source");
        assertThat(copy.getAppointmentDate()).isEqualTo(originalDate);
        assertThat(copy.getAppointmentDate()).isNotSameAs(originalDate);
    }

    /**
     * Ensures the copy guard rejects corrupted state (null internal fields).
     *
     * <p>Added to kill PITest mutant: "removed call to validateCopySource" in Appointment.copy().
     * This test uses reflection to corrupt each internal field and verify copy() throws.
     * Parameterized to achieve full branch coverage of the validateCopySource null checks.
     */
    @ParameterizedTest(name = "copy rejects null {0}")
    @MethodSource("nullFieldProvider")
    void testCopyRejectsNullInternalState(String fieldName) throws Exception {
        Date originalDate = futureDate(30);
        Appointment appointment = new Appointment("901", originalDate, "Copy source");

        Field field = Appointment.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(appointment, null);

        assertThatThrownBy(appointment::copy)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("appointment copy source must not be null");
    }

    /**
     * Provides field names for the null internal state test.
     */
    static Stream<String> nullFieldProvider() {
        return Stream.of("appointmentId", "appointmentDate", "description");
    }

    /**
     * Supplies invalid constructor inputs for {@link #testConstructorValidation}.
     */
    private static Stream<Arguments> invalidCreationInputs() {
        Date future = futureDate(30);
        Date past = pastDate();
        String longDesc = "This description is intentionally made way too long to exceed the fifty character limit set";
        return Stream.of(
                Arguments.of(" ", future, "Desc", "appointmentId must not be null or blank"),
                Arguments.of("", future, "Desc", "appointmentId must not be null or blank"),
                Arguments.of(null, future, "Desc", "appointmentId must not be null or blank"),
                Arguments.of("12345678901", future, "Desc", "appointmentId length must be between 1 and 10"),
                Arguments.of("1", future, " ", "description must not be null or blank"),
                Arguments.of("1", future, "", "description must not be null or blank"),
                Arguments.of("1", future, null, "description must not be null or blank"),
                Arguments.of("1", future, longDesc, "description length must be between 1 and 50"),
                Arguments.of("1", null, "Desc", "appointmentDate must not be null"),
                Arguments.of("1", past, "Desc", "appointmentDate must not be in the past")
        );
    }

    /**
     * Supplies invalid update inputs for {@link #testUpdateRejectsInvalidValuesAtomically}.
     */
    private static Stream<Arguments> invalidUpdateInputs() {
        Date past = pastDate();
        Date future = futureDate(30);
        String longDesc = "This description is intentionally made way too long to exceed the fifty character limit set";
        return Stream.of(
                Arguments.of(null, "Valid", "appointmentDate must not be null"),
                Arguments.of(past, "Valid", "appointmentDate must not be in the past"),
                Arguments.of(future, " ", "description must not be null or blank"),
                Arguments.of(future, "", "description must not be null or blank"),
                Arguments.of(future, null, "description must not be null or blank"),
                Arguments.of(future, longDesc, "description length must be between 1 and 50")
        );
    }

    /**
     * Helper to build a zeroed {@link Date} {@code daysInFuture} days from now.
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

    /**
     * Helper to build a zeroed {@link Date} one day in the past.
     */
    private static Date pastDate() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
