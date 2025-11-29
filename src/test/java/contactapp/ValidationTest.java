package contactapp;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused tests for {@link Validation} helpers to ensure PIT sees
 * their behavior when blank checks or boundary logic are mutated.
 */
class ValidationTest {

    private static final int MIN_LENGTH = 1;
    private static final int NAME_MAX = 10;
    private static final int ADDRESS_MAX = 30;
    private static final int PHONE_LENGTH = 10;

    /**
     * Ensures the helper allows inputs exactly at the configured min/max boundaries.
     */
    @Test
    void validateLengthAcceptsBoundaryValues() {
        assertThatNoException().isThrownBy(() ->
                Validation.validateLength("A", "firstName", MIN_LENGTH, NAME_MAX));

        assertThatNoException().isThrownBy(() ->
                Validation.validateLength("ABCDEFGHIJ", "firstName", MIN_LENGTH, NAME_MAX));

        assertThatNoException().isThrownBy(() ->
                Validation.validateLength("123456789012345678901234567890", "address", MIN_LENGTH, ADDRESS_MAX));
    }

    /**
     * Blank inputs must fail length validation for any label.
     */
    @Test
    void validateLengthRejectsBlankStrings() {
        assertThatThrownBy(() ->
                Validation.validateLength("   ", "firstName", MIN_LENGTH, NAME_MAX))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("firstName must not be null or blank");
    }

    /**
     * Null inputs should fail immediately before trim/length checks.
     */
    @Test
    void validateLengthRejectsNull() {
        assertThatThrownBy(() ->
                Validation.validateLength(null, "firstName", MIN_LENGTH, NAME_MAX))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("firstName must not be null or blank");
    }

    /**
     * Covers the "too long" branch of {@link Validation#validateLength(String, String, int, int)}.
     */
    @Test
    void validateLengthRejectsTooLong() {
        assertThatThrownBy(() ->
                Validation.validateLength("ABCDEFGHIJK", "firstName", MIN_LENGTH, NAME_MAX))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("firstName length must be between 1 and 10");
    }

    /**
     * Covers the "too short" branch of {@link Validation#validateLength(String, String, int, int)}.
     */
    @Test
    void validateLengthRejectsTooShort() {
        assertThatThrownBy(() ->
                Validation.validateLength("A", "middleName", /*minLength*/ 2, /*maxLength*/ 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("middleName length must be between 2 and 5");
    }

    /**
     * Phone numbers must fail fast when blank.
     */
    @Test
    void validateDigitsRejectsBlankStrings() {
        assertThatThrownBy(() ->
                Validation.validateDigits("          ", "phone", PHONE_LENGTH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("phone must not be null or blank");
    }

    /**
     * Null phones must trigger the same blank check as blanks.
     */
    @Test
    void validateDigitsRejectsNull() {
        assertThatThrownBy(() ->
                Validation.validateDigits(null, "phone", PHONE_LENGTH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("phone must not be null or blank");
    }

    /**
     * Confirms future dates pass {@link Validation#validateDateNotPast(Date, String)}.
     */
    @Test
    void validateDateNotPastAcceptsFutureDate() {
        final Date future = Date.from(Instant.now().plus(Duration.ofHours(1)));
        assertThatNoException().isThrownBy(() ->
                Validation.validateDateNotPast(future, "appointmentDate"));
    }

    /**
     * Null dates must throw an explicit error.
     */
    @Test
    void validateDateNotPastRejectsNull() {
        assertThatThrownBy(() ->
                Validation.validateDateNotPast(null, "appointmentDate"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("appointmentDate must not be null");
    }

    /**
     * Past dates must be rejected so appointments cannot be scheduled retroactively.
     */
    @Test
    void validateDateNotPastRejectsPastDate() {
        final Date past = Date.from(Instant.now().minus(Duration.ofHours(1)));
        assertThatThrownBy(() ->
                Validation.validateDateNotPast(past, "appointmentDate"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("appointmentDate must not be in the past");
    }

    /**
     * A date exactly equal to "now" must be accepted (not rejected as "in the past").
     *
     * Added to kill PITest mutant: boundary condition < vs <= in validateDateNotPast().
     * Uses Clock.fixed() to deterministically test the exact boundary where
     * date.getTime() == clock.millis(). With < operator this passes; with <= it would fail.
     *
     * See: https://stackoverflow.com/questions/2001671/override-java-system-currenttimemillis-for-testing-time-sensitive-code
     */
    @Test
    void validateDateNotPastAcceptsDateExactlyEqualToNow() {
        // Fix the clock at a specific instant
        final Instant fixedInstant = Instant.parse("2025-01-15T10:00:00Z");
        final Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        // Create a date with the exact same timestamp as the fixed clock
        final Date exactlyNow = new Date(fixedInstant.toEpochMilli());

        // With < operator: exactlyNow < fixedClock.millis() is false, so no exception
        // With <= operator (mutant): exactlyNow <= fixedClock.millis() is true, so exception
        assertThatNoException().isThrownBy(() ->
                Validation.validateDateNotPast(exactlyNow, "appointmentDate", fixedClock));
    }

    /**
     * Verifies the private constructor exists and is not accessible.
     *
     * Added for line coverage: private constructor in utility class Validation.
     * While not functionally necessary, this test ensures PITest doesn't flag
     * the private constructor as uncovered and documents that the class is
     * intentionally designed as a utility class (non-instantiable).
     */
    @Test
    void privateConstructorIsNotAccessible() throws Exception {
        Constructor<Validation> constructor = Validation.class.getDeclaredConstructor();
        assertThat(constructor.canAccess(null)).isFalse();

        // Verify the constructor can be invoked via reflection (for coverage)
        // but produces a useless instance (utility class pattern)
        constructor.setAccessible(true);
        Validation instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
