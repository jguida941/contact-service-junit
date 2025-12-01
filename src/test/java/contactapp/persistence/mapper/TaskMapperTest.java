package contactapp.persistence.mapper;

import contactapp.domain.Task;
import contactapp.persistence.entity.TaskEntity;
import contactapp.support.TestUserFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ensures {@link TaskMapper} copies fields between domain and entity objects.
 */
class TaskMapperTest {

    private final TaskMapper mapper = new TaskMapper();

    @Test
    void toEntityCopiesAllFields() {
        Task task = new Task("task-1", "Write docs", "Document persistence layer");

        TaskEntity entity = mapper.toEntity(task, TestUserFactory.createUser("task-mapper"));

        assertThat(entity.getTaskId()).isEqualTo("task-1");
        assertThat(entity.getName()).isEqualTo("Write docs");
        assertThat(entity.getDescription()).isEqualTo("Document persistence layer");
        assertThat(entity.getUser()).isNotNull();
    }

    /**
     * Null inputs should short-circuit to null so callers only get defensive copies
     * when a real domain object exists.
     */
    @Test
    void toEntityReturnsNullWhenDomainIsNull() {
        assertThat(mapper.toEntity(null, TestUserFactory.createUser())).isNull();
    }

    @Test
    void toDomainRoundTripsEntity() {
        TaskEntity entity = new TaskEntity(
                "task-1",
                "Write docs",
                "Document persistence layer",
                TestUserFactory.createUser("task-mapper-domain"));

        Task task = mapper.toDomain(entity);

        assertThat(task.getTaskId()).isEqualTo("task-1");
        assertThat(task.getName()).isEqualTo("Write docs");
        assertThat(task.getDescription()).isEqualTo("Document persistence layer");
    }

    /**
     * JPA repositories can surface null rows during delete-or-insert flows, so make sure
     * the mapper handles those gracefully instead of throwing.
     */
    @Test
    void toDomainReturnsNullWhenEntityIsNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedToEntityReturnsNullWhenDomainNull() {
        // codeql[java/avoid-deprecated-apis] Legacy mapper overload retained solely for backward compatibility tests.
        assertThat(mapper.toEntity((Task) null)).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedToEntityThrowsWhenDomainProvided() {
        Task task = new Task("legacy-1", "Name", "Desc");
        // codeql[java/avoid-deprecated-apis] Ensures the deprecated overload still throws until we delete it.
        assertThatThrownBy(() -> mapper.toEntity(task))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updateEntityCopiesMutableFields() {
        TaskEntity entity = new TaskEntity(
                "task-123",
                "Old",
                "Old desc",
                TestUserFactory.createUser("task-mapper-update"));
        Task updated = new Task("task-123", "New Name", "New Desc");

        mapper.updateEntity(entity, updated);

        assertThat(entity.getName()).isEqualTo("New Name");
        assertThat(entity.getDescription()).isEqualTo("New Desc");
    }

    @Test
    void updateEntityThrowsWhenTargetIsNull() {
        Task source = new Task("task-1", "Name", "Desc");
        assertThatThrownBy(() -> mapper.updateEntity(null, source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target entity must not be null");
    }

    @Test
    void updateEntityThrowsWhenSourceIsNull() {
        TaskEntity entity = new TaskEntity(
                "task-1",
                "Old",
                "Old desc",
                TestUserFactory.createUser("task-mapper-null-source"));
        assertThatThrownBy(() -> mapper.updateEntity(entity, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source task must not be null");
    }
}
