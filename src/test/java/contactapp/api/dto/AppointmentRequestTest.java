package contactapp.api.dto;

import java.util.Date;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AppointmentRequest} defensively copies mutable {@link Date} values
 * (line 83) so callers cannot mutate internal state after construction.
 */
class AppointmentRequestTest {

    @Test
    void appointmentDate_isDefensivelyCopied() {
        final Date original = new Date();
        final AppointmentRequest request = new AppointmentRequest(
                "apt-1",
                original,
                "Desc",
                null,
                null);

        final Date stored = request.appointmentDate();
        assertThat(stored).isNotSameAs(original);
        assertThat(stored).isEqualTo(original);

        // Mutating the original should not affect the stored copy
        original.setTime(original.getTime() + 10_000);
        assertThat(request.appointmentDate()).isNotEqualTo(original);
        assertThat(request.appointmentDate()).isNotSameAs(stored);
    }

    @Test
    void appointmentDate_allowsNull() {
        final AppointmentRequest request = new AppointmentRequest(
                "apt-2",
                null,
                "Desc",
                null,
                null);

        // Null date remains null when accessed
        assertThat(request.appointmentDate()).isNull();
    }
}
