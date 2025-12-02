package contactapp.persistence.entity;

import java.io.Serializable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the composite key implements the JPA requirements (Serializable, equals/hashCode)
 * and uses both components so ProjectContact links stay addressable in repositories.
 */
class ProjectContactIdTest {

    @Test
    void defaultsToNullAndSettersPopulateFields() {
        final ProjectContactId id = new ProjectContactId();
        id.setProjectId(1L);
        id.setContactId(2L);

        assertThat(id.getProjectId()).isEqualTo(1L);
        assertThat(id.getContactId()).isEqualTo(2L);
        assertThat(id).isInstanceOf(Serializable.class);
    }

    @Test
    void equalsAndHashCodeUseBothIds() {
        final ProjectContactId first = new ProjectContactId(10L, 20L);
        final ProjectContactId same = new ProjectContactId(10L, 20L);
        final ProjectContactId differentProject = new ProjectContactId(11L, 20L);
        final ProjectContactId differentContact = new ProjectContactId(10L, 21L);

        assertThat(first).isEqualTo(same);
        assertThat(first).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(differentProject);
        assertThat(first).isNotEqualTo(differentContact);
        assertThat(first).isNotEqualTo(null);
        assertThat(first).isNotEqualTo("not-an-id");
    }
}
