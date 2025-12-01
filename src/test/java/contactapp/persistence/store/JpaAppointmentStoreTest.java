package contactapp.persistence.store;

import contactapp.domain.Appointment;
import contactapp.persistence.entity.AppointmentEntity;
import contactapp.persistence.mapper.AppointmentMapper;
import contactapp.persistence.repository.AppointmentRepository;
import contactapp.security.User;
import contactapp.support.TestUserFactory;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JpaAppointmentStore} delegations.
 */
@ExtendWith(MockitoExtension.class)
class JpaAppointmentStoreTest {

    @Mock
    private AppointmentRepository repository;
    @Mock
    private AppointmentMapper mapper;

    private JpaAppointmentStore store;

    @BeforeEach
    void setUp() {
        store = new JpaAppointmentStore(repository, mapper);
    }

    @Test
    void existsById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] Deprecated existsById(String) should keep throwing until legacy callers migrate.
        assertThatThrownBy(() -> store.existsById("a-1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void existsById_withUserDelegatesToRepository() {
        final User owner = TestUserFactory.createUser("appt-store");
        when(repository.existsByAppointmentIdAndUser("a-2", owner)).thenReturn(true);

        assertThat(store.existsById("a-2", owner)).isTrue();
    }

    /**
     * Covers the false path for existsById(User) to keep PIT from mutating the boolean result.
     */
    @Test
    void existsById_withUserReturnsFalseWhenRepositoryReturnsFalse() {
        final User owner = TestUserFactory.createUser("appt-store-false");
        when(repository.existsByAppointmentIdAndUser("missing", owner)).thenReturn(false);

        assertThat(store.existsById("missing", owner)).isFalse();
    }

    @Test
    void findById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] Confirms the legacy overload still throws, deterring tenant bypasses.
        assertThatThrownBy(() -> store.findById("a-3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findById_withUserUsesMapper() {
        final User owner = TestUserFactory.createUser("appt-store-find");
        final AppointmentEntity entity = mock(AppointmentEntity.class);
        final Appointment domain = new Appointment("a-4",
                new Date(System.currentTimeMillis() + 1_000),
                "Review");
        when(repository.findByAppointmentIdAndUser("a-4", owner)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(store.findById("a-4", owner)).contains(domain);
    }

    @Test
    void deleteById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] We intentionally keep this deprecated method throwing to enforce scoping.
        assertThatThrownBy(() -> store.deleteById("a-5"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deleteById_withUserUsesRepositoryReturnCode() {
        final User owner = TestUserFactory.createUser("appt-store-delete");
        when(repository.deleteByAppointmentIdAndUser("a-6", owner)).thenReturn(1);

        assertThat(store.deleteById("a-6", owner)).isTrue();
    }

    @Test
    void deleteById_withUserReturnsFalseWhenNoRowsDeleted() {
        final User owner = TestUserFactory.createUser("appt-store-delete-none");
        when(repository.deleteByAppointmentIdAndUser("nonexistent", owner)).thenReturn(0);

        assertThat(store.deleteById("nonexistent", owner)).isFalse();
    }

    // --- Null parameter validation tests ---

    @Test
    void existsById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.existsById("a-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void findById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.findById("a-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void findAll_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.findAll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void deleteById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.deleteById("a-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void save_withNullAggregateThrowsIllegalArgument() {
        final User owner = TestUserFactory.createUser("appt-store-null-agg");
        assertThatThrownBy(() -> store.save(null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appointment aggregate must not be null");
    }

    @Test
    void save_withNullUserThrowsIllegalArgument() {
        final Appointment appt = new Appointment("a-1",
                new Date(System.currentTimeMillis() + 1_000), "Desc");
        assertThatThrownBy(() -> store.save(appt, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    @SuppressWarnings("deprecation")
    void save_withoutUserThrowsUnsupportedOperation() {
        final Appointment appt = new Appointment("a-1",
                new Date(System.currentTimeMillis() + 1_000), "Desc");
        // codeql[java/avoid-deprecated-apis] Validate the deprecated no-user method still throws.
        assertThatThrownBy(() -> store.save(appt))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void insert_withNullAggregateThrowsIllegalArgument() {
        final User owner = TestUserFactory.createUser("appt-store-insert-null");
        assertThatThrownBy(() -> store.insert(null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appointment aggregate must not be null");
    }

    @Test
    void insert_withNullUserThrowsIllegalArgument() {
        final Appointment appt = new Appointment("a-1",
                new Date(System.currentTimeMillis() + 1_000), "Desc");
        assertThatThrownBy(() -> store.insert(appt, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    // --- Save and insert behavior tests ---

    @Test
    void save_updatesExistingAppointment() {
        final User owner = TestUserFactory.createUser("appt-store-update");
        final Appointment appt = new Appointment("a-upd",
                new Date(System.currentTimeMillis() + 1_000), "Updated");
        final AppointmentEntity existingEntity = mock(AppointmentEntity.class);
        when(repository.findByAppointmentIdAndUser("a-upd", owner))
                .thenReturn(Optional.of(existingEntity));

        store.save(appt, owner);

        verify(mapper).updateEntity(existingEntity, appt);
        verify(repository).save(existingEntity);
    }

    @Test
    void save_insertsNewAppointment() {
        final User owner = TestUserFactory.createUser("appt-store-insert-new");
        final Appointment appt = new Appointment("a-new",
                new Date(System.currentTimeMillis() + 1_000), "New");
        final AppointmentEntity newEntity = mock(AppointmentEntity.class);
        when(repository.findByAppointmentIdAndUser("a-new", owner)).thenReturn(Optional.empty());
        when(mapper.toEntity(appt, owner)).thenReturn(newEntity);

        store.save(appt, owner);

        verify(repository).save(newEntity);
    }

    @Test
    void insert_savesNewEntity() {
        final User owner = TestUserFactory.createUser("appt-store-insert");
        final Appointment appt = new Appointment("a-ins",
                new Date(System.currentTimeMillis() + 1_000), "Insert");
        final AppointmentEntity entity = mock(AppointmentEntity.class);
        when(mapper.toEntity(appt, owner)).thenReturn(entity);

        store.insert(appt, owner);

        verify(repository).save(entity);
    }

    // --- findAll tests ---

    @Test
    void findAll_withUserReturnsMappedAppointments() {
        final User owner = TestUserFactory.createUser("appt-store-findall");
        final AppointmentEntity entity1 = mock(AppointmentEntity.class);
        final AppointmentEntity entity2 = mock(AppointmentEntity.class);
        final Appointment appt1 = new Appointment("a-1",
                new Date(System.currentTimeMillis() + 1_000), "Appt1");
        final Appointment appt2 = new Appointment("a-2",
                new Date(System.currentTimeMillis() + 2_000), "Appt2");
        when(repository.findByUser(owner)).thenReturn(List.of(entity1, entity2));
        when(mapper.toDomain(entity1)).thenReturn(appt1);
        when(mapper.toDomain(entity2)).thenReturn(appt2);

        final List<Appointment> result = store.findAll(owner);

        assertThat(result).containsExactly(appt1, appt2);
    }

    @Test
    void findAll_adminReturnsMappedAppointments() {
        final AppointmentEntity entity = mock(AppointmentEntity.class);
        final Appointment appt = new Appointment("a-admin",
                new Date(System.currentTimeMillis() + 1_000), "Admin");
        when(repository.findAll()).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(appt);

        final List<Appointment> result = store.findAll();

        assertThat(result).containsExactly(appt);
    }

    @Test
    void findById_withUserReturnsEmptyWhenNotFound() {
        final User owner = TestUserFactory.createUser("appt-store-find-empty");
        when(repository.findByAppointmentIdAndUser("missing", owner)).thenReturn(Optional.empty());

        assertThat(store.findById("missing", owner)).isEmpty();
    }

    @Test
    void deleteAll_delegatesToRepository() {
        store.deleteAll();

        verify(repository).deleteAll();
    }
}
