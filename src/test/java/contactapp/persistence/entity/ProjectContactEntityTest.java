package contactapp.persistence.entity;

import contactapp.domain.ProjectStatus;
import contactapp.support.TestUserFactory;
import contactapp.security.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the mutable {@link ProjectContactEntity} accessor surface so JaCoCo counts the JPA layer.
 */
class ProjectContactEntityTest {

    @Test
    void constructorAndGettersExposeFields() {
        User owner = TestUserFactory.createUser("project-contact-user");
        ProjectEntity project = createProjectEntity("P-1", owner);
        ContactEntity contact = createContactEntity("C-1", owner);

        ProjectContactEntity entity = new ProjectContactEntity(project, contact, "CLIENT");

        assertThat(entity.getProjectId()).isEqualTo(project.getId());
        assertThat(entity.getContactId()).isEqualTo(contact.getId());
        assertThat(entity.getProject()).isEqualTo(project);
        assertThat(entity.getContact()).isEqualTo(contact);
        assertThat(entity.getRole()).isEqualTo("CLIENT");
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void constructorAllowsNullRole() {
        User owner = TestUserFactory.createUser("project-contact-null-role");
        ProjectEntity project = createProjectEntity("P-2", owner);
        ContactEntity contact = createContactEntity("C-2", owner);

        ProjectContactEntity entity = new ProjectContactEntity(project, contact, null);

        assertThat(entity.getRole()).isNull();
    }

    @Test
    void constructorTrimsAndValidatesRole() {
        User owner = TestUserFactory.createUser("project-contact-trim");
        ProjectEntity project = createProjectEntity("P-3", owner);
        ContactEntity contact = createContactEntity("C-3", owner);

        ProjectContactEntity entity = new ProjectContactEntity(project, contact, "  STAKEHOLDER  ");

        assertThat(entity.getRole()).isEqualTo("STAKEHOLDER");
    }

    @Test
    void constructorRejectsRoleTooLong() {
        User owner = TestUserFactory.createUser("project-contact-long-role");
        ProjectEntity project = createProjectEntity("P-4", owner);
        ContactEntity contact = createContactEntity("C-4", owner);

        String longRole = "A".repeat(51);

        assertThatThrownBy(() -> new ProjectContactEntity(project, contact, longRole))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role length must be between");
    }

    @Test
    void constructorRejectsNullProject() {
        User owner = TestUserFactory.createUser("project-contact-null-project");
        ContactEntity contact = createContactEntity("C-5", owner);

        assertThatThrownBy(() -> new ProjectContactEntity(null, contact, "CLIENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project must not be null");
    }

    @Test
    void constructorRejectsNullContact() {
        User owner = TestUserFactory.createUser("project-contact-null-contact");
        ProjectEntity project = createProjectEntity("P-6", owner);

        assertThatThrownBy(() -> new ProjectContactEntity(project, null, "CLIENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contact must not be null");
    }

    @Test
    void constructorRejectsProjectWithoutId() {
        User owner = TestUserFactory.createUser("project-contact-no-id");
        ProjectEntity project = new ProjectEntity();
        ContactEntity contact = createContactEntity("C-7", owner);

        assertThatThrownBy(() -> new ProjectContactEntity(project, contact, "CLIENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project must not be null and must have an ID");
    }

    @Test
    void setRoleUpdatesValue() {
        User owner = TestUserFactory.createUser("project-contact-set-role");
        ProjectEntity project = createProjectEntity("P-8", owner);
        ContactEntity contact = createContactEntity("C-8", owner);

        ProjectContactEntity entity = new ProjectContactEntity(project, contact, "CLIENT");
        entity.setRole("VENDOR");

        assertThat(entity.getRole()).isEqualTo("VENDOR");
    }

    @Test
    void setRoleTrimsValue() {
        User owner = TestUserFactory.createUser("project-contact-set-role-trim");
        ProjectEntity project = createProjectEntity("P-9", owner);
        ContactEntity contact = createContactEntity("C-9", owner);

        ProjectContactEntity entity = new ProjectContactEntity(project, contact, "CLIENT");
        entity.setRole("  PARTNER  ");

        assertThat(entity.getRole()).isEqualTo("PARTNER");
    }

    @Test
    void setRoleAcceptsNull() {
        User owner = TestUserFactory.createUser("project-contact-set-role-null");
        ProjectEntity project = createProjectEntity("P-10", owner);
        ContactEntity contact = createContactEntity("C-10", owner);

        ProjectContactEntity entity = new ProjectContactEntity(project, contact, "CLIENT");
        entity.setRole(null);

        assertThat(entity.getRole()).isNull();
    }

    // Helper methods to create entities with IDs set via reflection

    private ProjectEntity createProjectEntity(String projectId, User owner) {
        ProjectEntity entity = new ProjectEntity(
                projectId,
                "Test Project",
                "Description",
                ProjectStatus.ACTIVE,
                owner);
        setId(entity, nextId());
        return entity;
    }

    private ContactEntity createContactEntity(String contactId, User owner) {
        ContactEntity entity = new ContactEntity(
                contactId,
                "John",
                "Doe",
                "1234567890",
                "123 Main St",
                owner);
        setId(entity, nextId());
        return entity;
    }

    private void setId(Object entity, Long id) {
        try {
            java.lang.reflect.Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long idCounter = 1L;
    private Long nextId() {
        return idCounter++;
    }
}
