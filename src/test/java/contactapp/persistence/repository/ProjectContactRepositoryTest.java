package contactapp.persistence.repository;

import contactapp.domain.ProjectStatus;
import contactapp.persistence.entity.ContactEntity;
import contactapp.persistence.entity.ProjectContactEntity;
import contactapp.persistence.entity.ProjectContactId;
import contactapp.persistence.entity.ProjectEntity;
import contactapp.security.User;
import contactapp.security.UserRepository;
import contactapp.support.TestUserFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository slice tests for {@link ProjectContactRepository} (H2 + Flyway schema).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectContactRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectContactRepository projectContactRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectRepository projectRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ContactRepository contactRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @Test
    void savePersistsProjectContact() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-user"));
        ProjectEntity project = createAndSaveProject("P-1", owner);
        ContactEntity contact = createAndSaveContact("C-1", owner);

        ProjectContactEntity link = new ProjectContactEntity(project, contact, "CLIENT");
        projectContactRepository.saveAndFlush(link);

        assertThat(projectContactRepository.existsByProjectIdAndContactId(
                project.getId(), contact.getId())).isTrue();
    }

    @Test
    void findByProjectIdReturnsAllContacts() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-find-project"));
        ProjectEntity project = createAndSaveProject("P-2", owner);
        ContactEntity contact1 = createAndSaveContact("C-2", owner);
        ContactEntity contact2 = createAndSaveContact("C-3", owner);

        projectContactRepository.save(new ProjectContactEntity(project, contact1, "CLIENT"));
        projectContactRepository.save(new ProjectContactEntity(project, contact2, "STAKEHOLDER"));
        projectContactRepository.flush();

        List<ProjectContactEntity> links = projectContactRepository.findByProjectId(project.getId());

        assertThat(links).hasSize(2);
        assertThat(links).extracting(pc -> pc.getContact().getContactId())
                .containsExactlyInAnyOrder("C-2", "C-3");
    }

    @Test
    void findByContactIdReturnsAllProjects() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-find-contact"));
        ProjectEntity project1 = createAndSaveProject("P-3", owner);
        ProjectEntity project2 = createAndSaveProject("P-4", owner);
        ContactEntity contact = createAndSaveContact("C-4", owner);

        projectContactRepository.save(new ProjectContactEntity(project1, contact, "CLIENT"));
        projectContactRepository.save(new ProjectContactEntity(project2, contact, "VENDOR"));
        projectContactRepository.flush();

        List<ProjectContactEntity> links = projectContactRepository.findByContactId(contact.getId());

        assertThat(links).hasSize(2);
        assertThat(links).extracting(pc -> pc.getProject().getProjectId())
                .containsExactlyInAnyOrder("P-3", "P-4");
    }

    @Test
    void existsByProjectIdAndContactIdReturnsTrueWhenExists() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-exists"));
        ProjectEntity project = createAndSaveProject("P-5", owner);
        ContactEntity contact = createAndSaveContact("C-5", owner);

        projectContactRepository.save(new ProjectContactEntity(project, contact, "CLIENT"));
        projectContactRepository.flush();

        boolean exists = projectContactRepository.existsByProjectIdAndContactId(
                project.getId(), contact.getId());

        assertThat(exists).isTrue();
    }

    @Test
    void existsByProjectIdAndContactIdReturnsFalseWhenNotExists() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-not-exists"));
        ProjectEntity project = createAndSaveProject("P-6", owner);
        ContactEntity contact = createAndSaveContact("C-6", owner);

        boolean exists = projectContactRepository.existsByProjectIdAndContactId(
                project.getId(), contact.getId());

        assertThat(exists).isFalse();
    }

    @Test
    void duplicateProjectContactFails() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-duplicate"));
        ProjectEntity project = createAndSaveProject("P-7", owner);
        ContactEntity contact = createAndSaveContact("C-7", owner);

        ProjectContactEntity first = new ProjectContactEntity(project, contact, "CLIENT");
        ProjectContactEntity second = new ProjectContactEntity(project, contact, "STAKEHOLDER");

        projectContactRepository.saveAndFlush(first);

        assertThatThrownBy(() -> projectContactRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteByIdRemovesProjectContact() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-delete"));
        ProjectEntity project = createAndSaveProject("P-8", owner);
        ContactEntity contact = createAndSaveContact("C-8", owner);

        ProjectContactEntity link = new ProjectContactEntity(project, contact, "CLIENT");
        projectContactRepository.saveAndFlush(link);

        ProjectContactId id = new ProjectContactId(project.getId(), contact.getId());
        projectContactRepository.deleteById(id);
        projectContactRepository.flush();

        assertThat(projectContactRepository.existsByProjectIdAndContactId(
                project.getId(), contact.getId())).isFalse();
    }

    @Test
    void cascadeDeleteRemovesLinksWhenProjectDeleted() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-cascade-project"));
        ProjectEntity project = createAndSaveProject("P-9", owner);
        ContactEntity contact = createAndSaveContact("C-9", owner);

        projectContactRepository.save(new ProjectContactEntity(project, contact, "CLIENT"));
        projectContactRepository.flush();

        projectRepository.delete(project);
        projectRepository.flush();

        assertThat(projectContactRepository.existsByProjectIdAndContactId(
                project.getId(), contact.getId())).isFalse();
    }

    @Test
    void cascadeDeleteRemovesLinksWhenContactDeleted() {
        User owner = userRepository.save(TestUserFactory.createUser("pc-repo-cascade-contact"));
        ProjectEntity project = createAndSaveProject("P-10", owner);
        ContactEntity contact = createAndSaveContact("C-10", owner);

        projectContactRepository.save(new ProjectContactEntity(project, contact, "CLIENT"));
        projectContactRepository.flush();

        contactRepository.delete(contact);
        contactRepository.flush();

        assertThat(projectContactRepository.existsByProjectIdAndContactId(
                project.getId(), contact.getId())).isFalse();
    }

    // Helper methods

    private ProjectEntity createAndSaveProject(String projectId, User owner) {
        ProjectEntity project = new ProjectEntity(
                projectId,
                "Test Project",
                "Description",
                ProjectStatus.ACTIVE,
                owner);
        return projectRepository.save(project);
    }

    private ContactEntity createAndSaveContact(String contactId, User owner) {
        ContactEntity contact = new ContactEntity(
                contactId,
                "John",
                "Doe",
                "1234567890",
                "123 Main St",
                owner);
        return contactRepository.save(contact);
    }
}
